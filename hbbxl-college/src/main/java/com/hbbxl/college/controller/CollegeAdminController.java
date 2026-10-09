package com.hbbxl.college.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hbbxl.college.service.CollegeAdminService;
import com.hbbxl.common.core.auth.RequireRole;
import com.hbbxl.common.core.constant.CommonConstants;
import com.hbbxl.common.core.result.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "管理端-院校")
@RestController
@RequestMapping("/college/admin")
@RequiredArgsConstructor
public class CollegeAdminController {

    private final CollegeAdminService collegeAdminService;

    @Operation(summary = "院校分页列表")
    @GetMapping("/page")
    @RequireRole(CommonConstants.ROLE_ADMIN)
    public R<IPage<Map<String, Object>>> page(@RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "20") int size,
                                              @RequestParam(required = false) String province,
                                              @RequestParam(required = false) String city,
                                              @RequestParam(required = false) String keyword) {
        return R.ok(collegeAdminService.page(page, size, province, city, keyword));
    }
}
