package com.hbbxl.auth.controller;

import com.hbbxl.auth.service.AuthService;
import com.hbbxl.common.core.result.R;
import com.hbbxl.common.security.utils.RsaUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器
 */
@Tag(name = "认证管理")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "登录（支持 RSA 加密密码传输）")
    @PostMapping("/login")
    public R<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        // 如果前端传了加密后的密码，先解密
        String password = request.getPassword();
        if (request.getEncryptedPassword() != null && !request.getEncryptedPassword().isBlank()) {
            try {
                password = RsaUtil.decryptByPrivateKey(request.getEncryptedPassword());
            } catch (Exception e) {
                return R.fail("密码解密失败，请刷新页面重试");
            }
        }
        if (password == null || password.isBlank()) {
            return R.fail("密码不能为空");
        }
        AuthService.LoginResult result = authService.login(request.getUsername(), password);
        LoginResponse resp = new LoginResponse();
        resp.setToken(result.token());
        resp.setRole(result.role());
        resp.setUsername(result.username());
        resp.setRealName(result.realName());
        resp.setUserId(result.userId());
        return R.ok(resp);
    }

    @Operation(summary = "注册（已废弃，仅保留兼容）")
    @PostMapping("/register")
    public R<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request.getPhone(), request.getPassword(), request.getRole());
        return R.ok();
    }

    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    public R<Void> logout(@RequestHeader("Authorization") String authorization) {
        String token = authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring(7)
                : authorization;
        authService.logout(token);
        return R.ok();
    }

    @Data
    public static class LoginRequest {
        @NotBlank(message = "用户名不能为空")
        private String username;

        /** 明文密码（encryptedPassword 为空时必填） */
        private String password;

        /** RSA 加密后的密码（优先于 password 字段） */
        private String encryptedPassword;
    }

    @Data
    public static class LoginResponse {
        private String token;
        private String role;
        private String username;
        private String realName;
        private Long userId;
    }

    @Data
    public static class RegisterRequest {
        @NotBlank(message = "手机号不能为空")
        private String phone;

        @NotBlank(message = "密码不能为空")
        private String password;

        @NotBlank(message = "身份不能为空")
        private String role;
    }
}
