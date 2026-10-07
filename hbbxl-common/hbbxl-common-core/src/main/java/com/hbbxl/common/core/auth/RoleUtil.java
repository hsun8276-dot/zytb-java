package com.hbbxl.common.core.auth;

import com.hbbxl.common.core.constant.CommonConstants;

/**
 * 角色判断工具
 */
public final class RoleUtil {

    private RoleUtil() {
    }

    public static boolean isAdmin(String role) {
        return CommonConstants.ROLE_ADMIN.equals(role);
    }

    public static boolean isTeacherOrAdmin(String role) {
        return CommonConstants.ROLE_TEACHER.equals(role) || CommonConstants.ROLE_ADMIN.equals(role);
    }
}
