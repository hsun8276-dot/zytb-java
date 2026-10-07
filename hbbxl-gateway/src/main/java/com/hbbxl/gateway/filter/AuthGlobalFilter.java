package com.hbbxl.gateway.filter;

import com.hbbxl.common.core.constant.CommonConstants;
import com.hbbxl.gateway.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;

/**
 * 全局 JWT 鉴权过滤器
 * <p>
 * 从 Authorization header 提取 JWT → 校验签名 → 校验 Redis 中 token 存在（未登出）→
 * 把 userId/username/role 注入下游请求头。
 * 下游服务直接从 header 获取用户信息，无需重复解析 token。
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private final StringRedisTemplate redisTemplate;

    /** 白名单路径前缀（不校验 JWT） */
    private static final List<String> WHITE_LIST = Arrays.asList(
            "/api/auth/login",
            "/api/auth/captcha",
            "/api/auth/register",
            "/api/auth/logout",
            // 获取 RSA 公钥（登录前需先获取）
            "/api/crypto/rsa/public-key",
            // 系统配置（登录前需要获取配置信息）
            "/api/config/all",
            // 各服务经网关的 Swagger / Knife4j 文档路径（StripPrefix 在路由阶段执行，
            // 网关过滤器看到的是原始路径，所以需配 /api/xxx/swagger-ui 和 /api/xxx/v3/api-docs）
            "/api/auth/swagger-ui",
            "/api/auth/v3/api-docs",
            "/api/user/swagger-ui",
            "/api/user/v3/api-docs",
            // 视频流播放(原生video标签无法携带JWT, 播放地址不暴露对象存储)
            "/api/video/stream/",
            // 头像/封面/校徽等媒体代理(img标签无法携带JWT)
            "/api/video/media/",
            // 院校详情页 HTML（FreeMarker 静态页面，无需登录即可访问）
            "/api/school-html/detail",
            "/api/category/swagger-ui",
            "/api/category/v3/api-docs",
            // 网关自身 swagger（不经过 /api 前缀）
            "/swagger-ui",
            "/v3/api-docs",
            "/swagger-resources",
            "/webjars"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // 白名单放行，但去掉调用方自带的身份头，免登录接口上不能伪造用户或角色
        if (isWhiteList(path)) {
            ServerWebExchange anonymousExchange = exchange.mutate()
                    .request(r -> r.headers(headers -> {
                        headers.remove("X-User-Id");
                        headers.remove("X-Username");
                        headers.remove("X-User-Role");
                    }))
                    .build();
            return chain.filter(anonymousExchange);
        }

        // 提取 Authorization header
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        String token = JwtUtil.extractToken(authHeader);

        if (token == null) {
            log.warn("请求缺少token: {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        // 校验 JWT
        Claims claims = JwtUtil.parseToken(token);
        if (claims == null) {
            log.warn("Token无效: {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        // 校验 Redis 中 token 仍存在（未登出）。auth 服务登出时删除了该 key；
        // key 存在 → token 有效；key 不存在 → token 已作废，拒绝放行。
        Boolean exists = redisTemplate.hasKey(CommonConstants.REDIS_TOKEN_PREFIX + token);
        if (Boolean.FALSE.equals(exists)) {
            log.warn("Token已登出或已失效: {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        // 提取用户信息并注入请求头
        String userId = String.valueOf(claims.get("userId"));
        String username = (String) claims.get("username");
        String role = (String) claims.get("role");

        log.debug("JWT校验通过: userId={}, username={}, role={}, path={}", userId, username, role, path);

        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(r -> r.header("X-User-Id", userId)
                        .header("X-Username", username)
                        .header("X-User-Role", role))
                .build();

        return chain.filter(mutatedExchange);
    }

    @Override
    public int getOrder() {
        return -100; // 高优先级，在其它过滤器之前执行
    }

    private boolean isWhiteList(String path) {
        // 加密信封解密后的路径没有 /api 前缀(如 /auth/login), 归一化后统一比对
        String normalized = path.startsWith("/api/") ? path.substring(4) : path;
        return WHITE_LIST.stream().anyMatch(w -> {
            String wNormalized = w.startsWith("/api/") ? w.substring(4) : w;
            return normalized.startsWith(wNormalized);
        });
    }
}