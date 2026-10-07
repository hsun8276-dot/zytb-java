package com.hbbxl.gateway.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 接口地址加密(信封解密): 前端把 请求方法+路径+参数+请求体 整体 AES-GCM 加密后统一 POST /api/gw,
 * 本过滤器解密还原出真实请求(方法/路径/查询串/请求体), 改写当前请求与路由后放行。
 * <p>
 * 视频流/媒体/文件上传下载等不走信封, 保持原样直接经过后续过滤器。
 * 路由匹配发生在过滤器之前, 因此 bootstrap.yml 中为 /api/gw 配了占位路由,
 * 本过滤器解密后按路径首段(服务标识)替换 GATEWAY_ROUTE_ATTR 指向真实服务。
 */
@Component
public class ApiCryptoGlobalFilter implements GlobalFilter, Ordered {

    private static final String GW_PATH = "/api/gw";
    private static final int IV_LENGTH = 12;
    private static final long MAX_CLOCK_SKEW_MS = 30 * 60_000L;

    /** 路径首段(服务标识) → 注册到Nacos的服务名, 同时充当服务白名单 */
    private static final Map<String, String> SERVICE_MAP = new HashMap<>();

    static {
        SERVICE_MAP.put("auth", "hbbxl-auth");
        SERVICE_MAP.put("crypto", "hbbxl-auth");
        SERVICE_MAP.put("config", "hbbxl-auth");
        SERVICE_MAP.put("user", "hbbxl-user");
        SERVICE_MAP.put("student", "hbbxl-user");
        SERVICE_MAP.put("role", "hbbxl-user");
        SERVICE_MAP.put("category", "hbbxl-category");
        SERVICE_MAP.put("knowledge", "hbbxl-category");
        SERVICE_MAP.put("subject", "hbbxl-category");
        SERVICE_MAP.put("category-subject", "hbbxl-category");
        SERVICE_MAP.put("outline", "hbbxl-category");
        SERVICE_MAP.put("question", "hbbxl-question");
        SERVICE_MAP.put("exam", "hbbxl-exam");
        SERVICE_MAP.put("wrong-book", "hbbxl-wrong-book");
        SERVICE_MAP.put("video", "hbbxl-video");
        SERVICE_MAP.put("video-question", "hbbxl-video");
        SERVICE_MAP.put("ai", "hbbxl-ai");
        SERVICE_MAP.put("statistics", "hbbxl-statistics");
        SERVICE_MAP.put("report", "hbbxl-statistics");
        SERVICE_MAP.put("notification", "hbbxl-notification");
        SERVICE_MAP.put("school", "hbbxl-school");
        SERVICE_MAP.put("score", "hbbxl-school");
        SERVICE_MAP.put("school-html", "hbbxl-school");
        SERVICE_MAP.put("class", "hbbxl-school");
        SERVICE_MAP.put("ocr", "hbbxl-ocr");
    }

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final byte[] keyBytes;

    public ApiCryptoGlobalFilter(@Value("${api-crypto.key}") String keyB64) {
        this.keyBytes = Base64.getDecoder().decode(keyB64);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!GW_PATH.equals(exchange.getRequest().getPath().value())) {
            return chain.filter(exchange);
        }
        return DataBufferUtils.join(exchange.getRequest().getBody())
                .defaultIfEmpty(exchange.getResponse().bufferFactory().wrap(new byte[0]))
                .flatMap(dataBuffer -> {
                    byte[] body;
                    try {
                        body = new byte[dataBuffer.readableByteCount()];
                        dataBuffer.read(body);
                    } finally {
                        DataBufferUtils.release(dataBuffer);
                    }
                    try {
                        return chain.filter(rewrite(exchange, decryptEnvelope(body)));
                    } catch (Exception e) {
                        return writeError(exchange, "请求解密失败,请刷新页面重试");
                    }
                });
    }

    /** 解密信封, 返回 还原后的请求 与 目标路由 */
    private Decrypted decryptEnvelope(byte[] body) throws Exception {
        JsonNode env = objectMapper.readTree(body);
        String d = env.path("d").asText(null);
        if (d == null || d.isEmpty()) {
            throw new IllegalArgumentException("empty envelope");
        }
        byte[] ivAndCt = Base64.getDecoder().decode(d);
        if (ivAndCt.length <= IV_LENGTH) {
            throw new IllegalArgumentException("bad envelope length");
        }
        byte[] iv = new byte[IV_LENGTH];
        System.arraycopy(ivAndCt, 0, iv, 0, IV_LENGTH);
        byte[] ct = new byte[ivAndCt.length - IV_LENGTH];
        System.arraycopy(ivAndCt, IV_LENGTH, ct, 0, ct.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyBytes, "AES"), new GCMParameterSpec(128, iv));
        JsonNode req = objectMapper.readTree(cipher.doFinal(ct));

        long ts = req.path("ts").asLong(0);
        if (Math.abs(System.currentTimeMillis() - ts) > MAX_CLOCK_SKEW_MS) {
            throw new IllegalArgumentException("envelope expired");
        }

        String method = req.path("m").asText("GET").toUpperCase();
        String p = req.path("p").asText("");
        // 去掉开头斜杠, 拒绝路径穿越与未登记的服务
        while (p.startsWith("/")) {
            p = p.substring(1);
        }
        if (p.isEmpty() || p.contains("..")) {
            throw new IllegalArgumentException("bad path");
        }
        String firstSegment = p.split("/", 2)[0];
        String serviceName = SERVICE_MAP.get(firstSegment);
        if (serviceName == null) {
            throw new IllegalArgumentException("unknown service: " + firstSegment);
        }

        // 查询串: 与 axios 默认序列化对齐 —— 跳过 null, 对象转 JSON 字符串, 数组展开成 key=v1&key=v2
        StringBuilder qs = new StringBuilder();
        JsonNode q = req.path("q");
        if (q.isObject()) {
            q.fields().forEachRemaining(e -> {
                JsonNode v = e.getValue();
                List<String> values = new ArrayList<>();
                if (v.isArray()) {
                    v.forEach(x -> {
                        if (!x.isNull()) values.add(x.asText());
                    });
                } else if (v.isNull()) {
                    // axios 跳过 null 参数, 这里同样忽略
                } else if (v.isObject()) {
                    try {
                        values.add(objectMapper.writeValueAsString(v));
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
                } else {
                    values.add(v.asText());
                }
                for (String val : values) {
                    if (qs.length() > 0) qs.append('&');
                    qs.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8))
                            .append('=')
                            .append(URLEncoder.encode(val, StandardCharsets.UTF_8));
                }
            });
        }

        HttpMethod httpMethod = HttpMethod.valueOf(method);
        byte[] newBody = new byte[0];
        JsonNode b = req.path("b");
        if (!b.isMissingNode() && !b.isNull() && httpMethod != HttpMethod.GET) {
            newBody = objectMapper.writeValueAsBytes(b);
        }
        return new Decrypted(httpMethod, p, qs.toString(), newBody, serviceName);
    }

    /** 按解密结果改写请求(方法/路径/请求体)与路由(指向真实服务) */
    private ServerWebExchange rewrite(ServerWebExchange exchange, Decrypted dec) {
        URI origin = exchange.getRequest().getURI();
        StringBuilder url = new StringBuilder()
                .append(origin.getScheme()).append("://").append(origin.getRawAuthority())
                .append('/').append(dec.path);
        if (!dec.query.isEmpty()) {
            url.append('?').append(dec.query);
        }
        ServerHttpRequest request = exchange.getRequest().mutate()
                .uri(URI.create(url.toString()))
                .method(dec.method)
                .headers(h -> {
                    h.remove("Content-Length");
                    h.setContentType(MediaType.APPLICATION_JSON);
                    h.setContentLength(dec.body.length);
                })
                .build();
        ServerHttpRequest decorated = new ServerHttpRequestDecorator(request) {
            @Override
            public Flux<DataBuffer> getBody() {
                if (dec.body.length == 0) {
                    return Flux.empty();
                }
                return Flux.defer(() -> Flux.just(exchange.getResponse().bufferFactory().wrap(dec.body)));
            }
        };
        Route route = Route.async()
                .id("api-crypto")
                .uri(URI.create("lb://" + dec.serviceName))
                .order(0)
                .predicate(s -> true)
                .build();
        exchange.getAttributes().put(org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR, route);
        return exchange.mutate().request(decorated).build();
    }

    private Mono<Void> writeError(ServerWebExchange exchange, String message) {
        ServerHttpResponse resp = exchange.getResponse();
        resp.setStatusCode(HttpStatus.BAD_REQUEST);
        resp.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] data = ("{\"code\":400,\"message\":\"" + message + "\",\"data\":null}")
                .getBytes(StandardCharsets.UTF_8);
        return resp.writeWith(Mono.just(resp.bufferFactory().wrap(data)));
    }

    @Override
    public int getOrder() {
        return -200; // 必须在 AuthGlobalFilter(-100) 之前: 鉴权与白名单要看到解密后的真实路径
    }

    private record Decrypted(HttpMethod method, String path, String query, byte[] body, String serviceName) {
    }
}
