package com.hbbxl.user.controller;

import com.hbbxl.common.core.auth.RequireRole;
import com.hbbxl.common.core.constant.CommonConstants;
import com.hbbxl.common.core.result.R;
import com.hbbxl.user.service.AdminLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "管理端-操作日志")
@RestController
@RequestMapping("/user/admin/logs")
@RequiredArgsConstructor
public class AdminLogController {

    private final AdminLogService adminLogService;

    @Operation(summary = "操作日志分页")
    @GetMapping
    @RequireRole(CommonConstants.ROLE_ADMIN)
    public R<Map<String, Object>> page(@RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "20") int size,
                                       @RequestParam(required = false) String keyword,
                                       @RequestParam(required = false) Integer status,
                                       @RequestParam(required = false) String module) {
        return R.ok(adminLogService.page(page, size, keyword, status, module));
    }
}
