package com.hbbxl.auth.service;

import com.hbbxl.auth.entity.SysUser;
import com.hbbxl.auth.entity.SysRole;

/**
 * 认证服务接口
 */
public interface AuthService {

    /**
     * 登录
     *
     * @param username 用户名
     * @param password 密码
     * @return 包含 token 和角色信息的 Map
     */
    LoginResult login(String username, String password);

    /**
     * 注册
     *
     * @param phone    手机号
     * @param password 密码
     * @param role     角色编码
     */
    void register(String phone, String password, String role);

    /**
     * 退出登录
     *
     * @param token 令牌
     */
    void logout(String token);

    /**
     * 登录结果
     */
    record LoginResult(String token, String role, String username, String realName, Long userId) {
    }
}