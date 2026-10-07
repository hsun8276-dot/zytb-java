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

    /** 重新开通/续期(管理员): 未到期则顺延一年, 已过期从当前时间起算; 同时恢复启用 */
    void renew(Long id, int years);

    /** 到期自动停用(定时任务): expire_time已过且仍启用的账号置为停用 */
    int autoDisableExpired();

    /** 用户详情(本人或管理员可查) */
    Map<String, Object> detail(Long userId, Long currentUserId, String role);
}
