package com.hbbxl.common.core.log;

import com.hbbxl.common.core.constant.CommonConstants;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.lang.reflect.AnnotatedElement;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 把写操作（POST/PUT/DELETE）记录到 sys_log
 * <p>
 * 只记录请求方法、路径和查询串，不记录请求体，避免登录、开通账号等接口的密码落库。
 */
@Slf4j
public class OperationLogInterceptor implements HandlerInterceptor {

    static final String FAIL_MESSAGE_ATTR = OperationLogInterceptor.class.getName() + ".FAIL_MESSAGE";
    private static final String START_ATTR = OperationLogInterceptor.class.getName() + ".START";

    private static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    /** 播放心跳、刷题提交、标记已读这类高频请求不记日志 */
    private static final List<String> SKIPPED_PATH_PREFIXES = List.of(
            "/video/progress", "/question/submit", "/notification/read");

    private static final String TAG_ANNOTATION = "io.swagger.v3.oas.annotations.tags.Tag";
    private static final String OPERATION_ANNOTATION = "io.swagger.v3.oas.annotations.Operation";
    private static final String INSERT_SQL = "INSERT INTO sys_log "
            + "(user_id, module, action, request_params, response_data, ip, duration, status) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

    private final JdbcTemplate jdbcTemplate;
    private final String defaultModule;
    private final ExecutorService writer = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(1000), runnable -> {
                Thread thread = new Thread(runnable, "sys-log-writer");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.DiscardPolicy());

    public OperationLogInterceptor(JdbcTemplate jdbcTemplate, String defaultModule) {
        this.jdbcTemplate = jdbcTemplate;
        this.defaultModule = defaultModule;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (handler instanceof HandlerMethod && shouldLog(request)) {
            request.setAttribute(START_ATTR, System.currentTimeMillis());
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        if (!(request.getAttribute(START_ATTR) instanceof Long start) || !(handler instanceof HandlerMethod method)) {
            return;
        }
        String failMessage = (String) request.getAttribute(FAIL_MESSAGE_ATTR);
        if (failMessage == null && ex != null) {
            failMessage = ex.getMessage();
        }
        if (failMessage == null && response.getStatus() >= 400) {
            failMessage = "HTTP " + response.getStatus();
        }
        String target = request.getMethod() + " " + request.getRequestURI();
        String query = decode(request.getQueryString());

        Object[] args = {
                parseUserId(request.getHeader(CommonConstants.HEADER_USER_ID)),
                truncate(firstNonBlank(annotationValue(method.getBeanType(), TAG_ANNOTATION, "name"), defaultModule), 64),
                truncate(firstNonBlank(annotationValue(method.getMethod(), OPERATION_ANNOTATION, "summary"), target), 128),
                truncate(query != null ? target + "?" + query : target, 2000),
                truncate(failMessage, 2000),
                truncate(clientIp(request), 64),
                (int) (System.currentTimeMillis() - start),
                failMessage == null ? 1 : 0
        };
        writer.execute(() -> {
            try {
                jdbcTemplate.update(INSERT_SQL, args);
            } catch (Exception e) {
                log.warn("写入操作日志失败: {}", e.getMessage());
            }
        });
    }

    private static boolean shouldLog(HttpServletRequest request) {
        if (READ_METHODS.contains(request.getMethod())) {
            return false;
        }
        String uri = request.getRequestURI();
        return SKIPPED_PATH_PREFIXES.stream().noneMatch(uri::startsWith);
    }

    private static String annotationValue(AnnotatedElement element, String annotationType, String attribute) {
        MergedAnnotation<?> annotation = MergedAnnotations.from(element).get(annotationType);
        if (!annotation.isPresent()) {
            return null;
        }
        String value = annotation.getString(attribute);
        return value.isBlank() ? null : value;
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static Long parseUserId(String value) {
        try {
            return value != null ? Long.valueOf(value) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String decode(String query) {
        if (query == null) {
            return null;
        }
        try {
            return URLDecoder.decode(query, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return query;
        }
    }

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
