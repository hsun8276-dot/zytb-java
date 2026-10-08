package com.hbbxl.aspiration.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hbbxl.aspiration.service.AspirationService;
import com.hbbxl.common.core.result.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 志愿方案(学生管理): 业务逻辑全部在 AspirationServiceImpl */
@Tag(name = "志愿方案(学生管理)")
@RestController
@RequestMapping("/aspiration")
@RequiredArgsConstructor
public class AspirationController {

    private final AspirationService aspirationService;

    @Operation(summary = "方案分页列表(所有管理员开通的填报师账号共享)")
    @GetMapping("/list")
    public R<IPage<Map<String, Object>>> list(@RequestParam(defaultValue = "1") long page,
                                              @RequestParam(defaultValue = "10") long size,
                                              @RequestParam(required = false) String keyword,
                                              @RequestParam(required = false) Integer submitStatus,
                                              @RequestParam(required = false) String province,
                                              @RequestParam(required = false) Integer year,
                                              @RequestParam(required = false) String batch,
                                              @RequestParam(required = false) String groupName,
                                              @RequestParam(required = false) Long fillerUserId,
                                              @RequestHeader("X-User-Id") Long currentUserId,
                                              @RequestHeader(value = "X-User-Role", required = false) String role) {
        return R.ok(aspirationService.pageList(page, size, keyword, submitStatus, province, year, batch,
                groupName, fillerUserId, currentUserId, role));
    }

    @Operation(summary = "填报师下拉(创建过方案的账号)")
    @GetMapping("/fillers")
    public R<List<Map<String, Object>>> fillers() {
        return R.ok(aspirationService.fillers());
    }

    @Operation(summary = "创建方案(添加学生): 学生核心信息创建后不可改")
    @PostMapping("/create")
    public R<Map<String, Object>> create(@RequestBody Map<String, Object> body,
                                         @RequestHeader("X-User-Id") Long currentUserId) {
        String code = aspirationService.create(body, currentUserId);
        return R.ok(Map.of("aspirationCode", code));
    }

    // aspirationCode 限定 UUID, 避免 /fillers /list 等静态路径被当成方案码
    private static final String CODE = "/{aspirationCode:[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}}";

    @Operation(summary = "方案详情(学生详细信息)")
    @GetMapping(CODE)
    public R<Map<String, Object>> detail(@PathVariable String aspirationCode,
                                         @RequestHeader("X-User-Id") Long currentUserId,
                                         @RequestHeader(value = "X-User-Role", required = false) String role) {
        return R.ok(aspirationService.detail(aspirationCode, currentUserId, role));
    }

    @Operation(summary = "志愿录取情况报告(已选志愿提档线对比)")
    @GetMapping(CODE + "/admission-report")
    public R<Map<String, Object>> admissionReport(@PathVariable String aspirationCode,
                                                  @RequestHeader("X-User-Id") Long currentUserId,
                                                  @RequestHeader(value = "X-User-Role", required = false) String role) {
        return R.ok(aspirationService.admissionReport(aspirationCode, currentUserId, role));
    }

    @Operation(summary = "编辑志愿信息段(志愿名称/就读高中/联系电话), 已交单锁定")
    @PutMapping(CODE)
    public R<Void> updateInfo(@PathVariable String aspirationCode,
                              @RequestBody Map<String, Object> body,
                              @RequestHeader("X-User-Id") Long currentUserId,
                              @RequestHeader(value = "X-User-Role", required = false) String role) {
        aspirationService.updateInfo(aspirationCode, body, currentUserId, role);
        return R.ok();
    }

    @Operation(summary = "修改推荐滑杆参数(向上/向下分数), 已交单锁定")
    @PutMapping(CODE + "/slider")
    public R<Void> updateSlider(@PathVariable String aspirationCode,
                                @RequestParam Integer upperParam,
                                @RequestParam Integer lowerParam,
                                @RequestHeader("X-User-Id") Long currentUserId,
                                @RequestHeader(value = "X-User-Role", required = false) String role) {
        aspirationService.updateSlider(aspirationCode, upperParam, lowerParam, currentUserId, role);
        return R.ok();
    }

    @Operation(summary = "交单(1)/撤销交单(0)")
    @PutMapping(CODE + "/submit")
    public R<Void> submit(@PathVariable String aspirationCode,
                          @RequestParam Integer status,
                          @RequestHeader("X-User-Id") Long currentUserId,
                          @RequestHeader(value = "X-User-Role", required = false) String role) {
        aspirationService.submit(aspirationCode, status, currentUserId, role);
        return R.ok();
    }

    @Operation(summary = "删除方案(逻辑删)")
    @DeleteMapping(CODE)
    public R<Void> delete(@PathVariable String aspirationCode,
                          @RequestHeader("X-User-Id") Long currentUserId,
                          @RequestHeader(value = "X-User-Role", required = false) String role) {
        aspirationService.delete(aspirationCode, currentUserId, role);
        return R.ok();
    }
}
