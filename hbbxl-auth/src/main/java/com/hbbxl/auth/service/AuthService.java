package com.hbbxl.auth.service;

public interface AuthService {

    /**
     * 登录: 校验码→密码解密→账号/状态/有效期/密码校验, 签发token并存Redis(24h)
     *
     * @param username          登录名
     * @param password          明文密码(encryptedPassword为空时必填)
     * @param encryptedPassword RSA加密密码(优先)
     * @param captchaId         图形校验码ID
     * @param captchaCode       用户输入的校验码
     */
    LoginResult login(String username, String password, String encryptedPassword,
                      String captchaId, String captchaCode);

    /** 登出: 删除Redis中的token */
    void logout(String token);

    /** 生成图形校验码: [captchaId, base64图片] */
    String[] generateCaptcha();

    record LoginResult(String token, String role, String username, String realName, Long userId) {
    }
}
