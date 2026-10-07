package com.hbbxl.common.core.log;

import com.hbbxl.common.core.constant.CommonConstants;
import com.hbbxl.common.core.result.R;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 记下失败响应的提示信息，供操作日志判断成败
 * <p>
 * 多数接口用 {@code return R.fail(...)} 表示失败，HTTP 状态仍是 200，只能从响应体里的 code 判断。
 */
@RestControllerAdvice
public class ResultCodeRecorder implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (body instanceof R<?> result && result.getCode() != CommonConstants.SUCCESS
                && request instanceof ServletServerHttpRequest servletRequest) {
            servletRequest.getServletRequest().setAttribute(OperationLogInterceptor.FAIL_MESSAGE_ATTR,
                    result.getMessage() != null ? result.getMessage() : "code " + result.getCode());
        }
        return body;
    }
}
