package com.hbbxl.auth.controller;

import com.hbbxl.auth.service.AuthService;
import com.hbbxl.common.core.result.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/** 认证控制器(账号由管理员开通, 无自助注册); 业务逻辑在 AuthServiceImpl */
@Tag(name = "认证管理")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "获取图形校验码")
    @GetMapping("/captcha")
    public R<Map<String, String>> captcha() {
        String[] c = authService.generateCaptcha();
        Map<String, String> data = new HashMap<>();
        data.put("captchaId", c[0]);
        data.put("image", c[1]);
        return R.ok(data);
    }

    @Operation(summary = "登录(支持RSA加密密码传输, 需图形校验码)")
    @PostMapping("/login")
    public R<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthService.LoginResult result = authService.login(request.getUsername(), request.getPassword(),
                request.getEncryptedPassword(), request.getCaptchaId(), request.getCaptchaCode());
        LoginResponse resp = new LoginResponse();
        resp.setToken(result.token());
        resp.setRole(result.role());
        resp.setUsername(result.username());
        resp.setRealName(result.realName());
        resp.setUserId(result.userId());
        return R.ok(resp);
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

        /** 明文密码(encryptedPassword为空时必填) */
        private String password;

        /** RSA加密后的密码(优先于password字段) */
        private String encryptedPassword;

        /** 图形校验码ID */
        private String captchaId;

        /** 用户输入的校验码 */
        private String captchaCode;
    }

    @Data
    public static class LoginResponse {
        private String token;
        private String role;
        private String username;
        private String realName;
        private Long userId;
    }
}
