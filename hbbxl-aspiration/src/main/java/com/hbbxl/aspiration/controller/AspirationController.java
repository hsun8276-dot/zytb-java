package com.hbbxl.aspiration.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hbbxl.aspiration.service.AspirationService;
import com.hbbxl.common.core.result.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 志愿方案(学生管理): 业务逻辑全部在 AspirationServiceImpl */
@Tag(name = "志愿方案(学生管理)")
@RestController
@RequestMapping("/aspiration")
@RequiredArgsConstructor
public class AspirationController {

    private final AspirationService aspirationService;

    @Operation(summary = "方案分页列表(填报师看自己创建的, 管理员看全部)")
    @GetMapping("/list")
    public R<IPage<Map<String, Object>>> list(@RequestParam(defaultValue = "1") long page,
                                              @RequestParam(defaultValue = "10") long size,
                                              @RequestParam(required = false) String keyword,
                                              @RequestParam(required = false) Integer submitStatus,
                                              @RequestHeader("X-User-Id") Long currentUserId,
                                              @RequestHeader(value = "X-User-Role", required = false) String role) {
        return R.ok(aspirationService.pageList(page, size, keyword, submitStatus, currentUserId, role));
    }

    @Operation(summary = "创建方案(添加学生): 学生核心信息创建后不可改")
    @PostMapping("/create")
    public R<Map<String, Object>> create(@RequestBody Map<String, Object> body,
                                         @RequestHeader("X-User-Id") Long currentUserId) {
        String code = aspirationService.create(body, currentUserId);
        return R.ok(Map.of("aspirationCode", code));
    }

    @Operation(summary = "方案详情(学生详细信息)")
    @GetMapping("/{aspirationCode}")
    public R<Map<String, Object>> detail(@PathVariable String aspirationCode,
                                         @RequestHeader("X-User-Id") Long currentUserId,
                                         @RequestHeader(value = "X-User-Role", required = false) String role) {
        return R.ok(aspirationService.detail(aspirationCode, currentUserId, role));
    }

    @Operation(summary = "编辑志愿信息段(志愿名称/就读高中/联系电话), 已交单锁定")
    @PutMapping("/{aspirationCode}")
    public R<Void> updateInfo(@PathVariable String aspirationCode,
                              @RequestBody Map<String, Object> body,
                              @RequestHeader("X-User-Id") Long currentUserId,
                              @RequestHeader(value = "X-User-Role", required = false) String role) {
        aspirationService.updateInfo(aspirationCode, body, currentUserId, role);
        return R.ok();
    }

    @Operation(summary = "修改推荐滑杆参数(向上/向下分数), 已交单锁定")
    @PutMapping("/{aspirationCode}/slider")
    public R<Void> updateSlider(@PathVariable String aspirationCode,
                                @RequestParam Integer upperParam,
                                @RequestParam Integer lowerParam,
                                @RequestHeader("X-User-Id") Long currentUserId,
                                @RequestHeader(value = "X-User-Role", required = false) String role) {
        aspirationService.updateSlider(aspirationCode, upperParam, lowerParam, currentUserId, role);
        return R.ok();
    }

    @Operation(summary = "交单(1)/撤销交单(0)")
    @PutMapping("/{aspirationCode}/submit")
    public R<Void> submit(@PathVariable String aspirationCode,
                          @RequestParam Integer status,
                          @RequestHeader("X-User-Id") Long currentUserId,
                          @RequestHeader(value = "X-User-Role", required = false) String role) {
        aspirationService.submit(aspirationCode, status, currentUserId, role);
        return R.ok();
    }

    @Operation(summary = "删除方案(逻辑删)")
    @DeleteMapping("/{aspirationCode}")
    public R<Void> delete(@PathVariable String aspirationCode,
                          @RequestHeader("X-User-Id") Long currentUserId,
                          @RequestHeader(value = "X-User-Role", required = false) String role) {
        aspirationService.delete(aspirationCode, currentUserId, role);
        return R.ok();
    }
}
