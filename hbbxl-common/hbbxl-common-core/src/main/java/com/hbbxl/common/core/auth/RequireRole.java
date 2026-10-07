package com.hbbxl.common.core.auth;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 限定可调用接口的角色，角色取自网关按 JWT 写入的 X-User-Role 请求头。
 * <p>
 * 标在类上对该控制器全部接口生效，方法上的声明优先。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireRole {

    String[] value();
}
