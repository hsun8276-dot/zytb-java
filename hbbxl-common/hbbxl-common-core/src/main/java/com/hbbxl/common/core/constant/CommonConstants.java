package com.hbbxl.common.core.constant;

/**
 * 通用常量
 */
public class CommonConstants {

    /** 成功状态码 */
    public static final int SUCCESS = 0;
    /** 失败状态码 */
    public static final int FAIL = 500;
    /** 未授权 */
    public static final int UNAUTHORIZED = 401;
    /** 禁止访问 */
    public static final int FORBIDDEN = 403;
    /** 未找到 */
    public static final int NOT_FOUND = 404;

    /** 用户token Header名称 */
    public static final String HEADER_AUTHORIZATION = "Authorization";
    public static final String HEADER_TOKEN_PREFIX = "Bearer ";

    /** 用户信息请求头（网关传递） */
    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USERNAME = "X-Username";
    public static final String HEADER_USER_ROLE = "X-User-Role";

    /** 角色常量 */
    public static final String ROLE_ADMIN = "admin";
    public static final String ROLE_TEACHER = "teacher";
    public static final String ROLE_STUDENT = "student";

    /** 缓存Key前缀 */
    public static final String REDIS_TOKEN_PREFIX = "hbbxl:token:";
    public static final String REDIS_USER_PREFIX = "hbbxl:user:";
    public static final String REDIS_CATEGORY_PREFIX = "hbbxl:category:";

    /** 默认密码 */
    public static final String DEFAULT_PASSWORD = "123456";

    /** 默认头像(二次元风格, 存于MinIO, 经/video/media代理展示) */
    public static final String DEFAULT_AVATAR = "http://81.70.105.12:9010/hbbxl/avatars/default-avatar.svg";

    /** 删除标记 */
    public static final int DEL_FLAG_NORMAL = 0;
    public static final int DEL_FLAG_DELETED = 1;
}
