package com.hbbxl.common.core.config;

import com.hbbxl.common.core.auth.RequireRoleInterceptor;
import com.hbbxl.common.core.log.OperationLogInterceptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 业务服务公共拦截器：操作日志、角色校验
 */
@Configuration
public class CommonWebMvcConfig implements WebMvcConfigurer {

    private final ObjectProvider<JdbcTemplate> jdbcTemplate;
    private final String applicationName;

    public CommonWebMvcConfig(ObjectProvider<JdbcTemplate> jdbcTemplate,
                              @Value("${spring.application.name:}") String applicationName) {
        this.jdbcTemplate = jdbcTemplate;
        this.applicationName = applicationName;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 日志拦截器排在前面，被角色校验拒绝的请求也会记一条失败日志
        JdbcTemplate template = jdbcTemplate.getIfAvailable();
        if (template != null) {
            registry.addInterceptor(new OperationLogInterceptor(template, applicationName));
        }
        registry.addInterceptor(new RequireRoleInterceptor());
    }
}
