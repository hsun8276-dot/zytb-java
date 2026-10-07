package com.hbbxl.auth.service;

public interface AuthService {

    /** 登录: 校验账号密码, 签发token并存Redis(24h) */
    LoginResult login(String username, String password);

    /** 登出: 删除Redis中的token */
    void logout(String token);

    record LoginResult(String token, String role, String username, String realName, Long userId) {
    }
}
