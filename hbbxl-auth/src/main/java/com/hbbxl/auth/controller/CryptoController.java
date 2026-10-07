package com.hbbxl.auth.controller;

import com.hbbxl.common.core.result.R;
import com.hbbxl.common.security.utils.RsaUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 加密配置控制器
 * <p>
 * 提供 RSA 公钥给前端，前端用其加密敏感字段（如登录密码）后传输。
 */
@Tag(name = "加密配置")
@RestController
@RequestMapping("/crypto")
@RequiredArgsConstructor
public class CryptoController {

    @Operation(summary = "获取 RSA 公钥（用于密码加密传输）")
    @GetMapping("/rsa/public-key")
    public R<Map<String, Object>> rsaPublicKey() {
        Map<String, Object> data = new HashMap<>();
        data.put("publicKey", RsaUtil.getPublicKeyBase64());
        data.put("algorithm", "RSA");
        return R.ok(data);
    }
}