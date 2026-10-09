package com.hbbxl.user.controller;

import com.hbbxl.common.core.auth.RequireRole;
import com.hbbxl.common.core.constant.CommonConstants;
import com.hbbxl.common.core.result.R;
import com.hbbxl.user.service.AdminOverviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "管理端工作台")
@RestController
@RequestMapping("/user/admin")
@RequiredArgsConstructor
public class AdminOverviewController {

    private final AdminOverviewService adminOverviewService;

    @Operation(summary = "工作台汇总")
    @GetMapping("/overview")
    @RequireRole(CommonConstants.ROLE_ADMIN)
    public R<Map<String, Object>> overview() {
        return R.ok(adminOverviewService.overview());
    }
}
