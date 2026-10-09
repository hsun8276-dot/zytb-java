package com.hbbxl.auth.controller;

import com.hbbxl.auth.service.ConfigService;
import com.hbbxl.common.core.auth.RequireRole;
import com.hbbxl.common.core.constant.CommonConstants;
import com.hbbxl.common.core.result.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "系统配置")
@RestController
@RequestMapping("/config")
@RequiredArgsConstructor
public class ConfigController {

    private final ConfigService configService;

    @Operation(summary = "公开配置(登录页等)")
    @GetMapping("/all")
    public R<Map<String, String>> all() {
        return R.ok(configService.publicAll());
    }

    @Operation(summary = "管理端配置列表")
    @GetMapping("/admin")
    @RequireRole(CommonConstants.ROLE_ADMIN)
    public R<List<Map<String, Object>>> adminList() {
        return R.ok(configService.adminList());
    }

    @Operation(summary = "管理端保存配置")
    @PutMapping("/admin")
    @RequireRole(CommonConstants.ROLE_ADMIN)
    public R<Void> save(@RequestBody Map<String, String> body) {
        configService.save(body);
        return R.ok();
    }
}
