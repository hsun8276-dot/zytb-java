package com.hbbxl.auth.controller;

import com.hbbxl.common.core.result.R;
import com.hbbxl.common.security.utils.RsaUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/** 加密配置: 下发RSA公钥给前端加密登录密码 */
@Tag(name = "加密配置")
@RestController
@RequestMapping("/crypto")
@RequiredArgsConstructor
public class CryptoController {

    @Operation(summary = "获取RSA公钥")
    @GetMapping("/rsa/public-key")
    public R<Map<String, Object>> rsaPublicKey() {
        Map<String, Object> data = new HashMap<>();
        data.put("publicKey", RsaUtil.getPublicKeyBase64());
        data.put("algorithm", "RSA");
        return R.ok(data);
    }
}
