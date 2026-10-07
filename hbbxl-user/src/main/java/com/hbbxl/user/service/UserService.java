package com.hbbxl.user.service;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.Map;

public interface UserService {

    /** 用户分页列表(管理员) */
    IPage<Map<String, Object>> pageList(int page, int size, String keyword);

    /** 开通账号(管理员): 默认密码123456, 角色固定为user */
    Long create(String username, String realName, String password, Long currentUserId);

    /** 启用/停用账号(管理员) */
    void updateStatus(Long id, Integer status);

    /** 重置密码(管理员): 不传密码则重置为123456 */
    void resetPassword(Long id, String password);
}
