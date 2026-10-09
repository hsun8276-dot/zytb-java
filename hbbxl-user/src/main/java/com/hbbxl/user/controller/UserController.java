package com.hbbxl.user.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hbbxl.common.core.auth.RequireRole;
import com.hbbxl.common.core.constant.CommonConstants;
import com.hbbxl.common.core.result.R;
import com.hbbxl.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/** 用户管理: 账号由管理员开通, 无自助注册 */
@Tag(name = "用户管理")
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "用户分页列表(管理员)")
    @GetMapping("/list")
    @RequireRole(CommonConstants.ROLE_ADMIN)
    public R<IPage<Map<String, Object>>> list(@RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "20") int size,
                                              @RequestParam(required = false) String keyword) {
        return R.ok(userService.pageList(page, size, keyword));
    }

    @Operation(summary = "开通账号(管理员): 默认密码123456, 角色固定为user")
    @PostMapping("/create")
    @RequireRole(CommonConstants.ROLE_ADMIN)
    public R<Map<String, Object>> create(@RequestBody Map<String, String> body,
                                         @RequestHeader("X-User-Id") Long currentUserId) {
        Long id = userService.create(body.get("username"), body.get("realName"),
                body.get("password"), currentUserId);
        Map<String, Object> data = new HashMap<>();
        data.put("id", id);
        return R.ok(data);
    }

    @Operation(summary = "启用/停用账号(管理员)")
    @PutMapping("/{id}/status")
    @RequireRole(CommonConstants.ROLE_ADMIN)
    public R<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        userService.updateStatus(id, status);
        return R.ok();
    }

    @Operation(summary = "重置密码(管理员): 不传则重置为123456")
    @PutMapping("/{id}/reset-password")
    @RequireRole(CommonConstants.ROLE_ADMIN)
    public R<Void> resetPassword(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        userService.resetPassword(id, body == null ? null : body.get("password"));
        return R.ok();
    }

    @Operation(summary = "重新开通/续期(管理员): years 不传则读系统配置 membership_years")
    @PutMapping("/{id}/renew")
    @RequireRole(CommonConstants.ROLE_ADMIN)
    public R<Void> renew(@PathVariable Long id,
                         @RequestParam(required = false) Integer years) {
        userService.renew(id, years);
        return R.ok();
    }

    @Operation(summary = "用户详情(本人或管理员)")
    @GetMapping("/detail")
    public R<Map<String, Object>> detail(@RequestParam Long userId,
                                         @RequestHeader("X-User-Id") Long currentUserId,
                                         @RequestHeader(value = "X-User-Role", required = false) String role) {
        return R.ok(userService.detail(userId, currentUserId, role));
    }

    @Operation(summary = "当前登录用户详情")
    @GetMapping("/me")
    public R<Map<String, Object>> me(@RequestHeader("X-User-Id") Long currentUserId,
                                     @RequestHeader(value = "X-User-Role", required = false) String role) {
        return R.ok(userService.detail(currentUserId, currentUserId, role));
    }

    @Operation(summary = "更新本人基本资料")
    @PutMapping("/profile")
    public R<Void> updateProfile(@RequestBody Map<String, Object> body,
                                 @RequestHeader("X-User-Id") Long currentUserId) {
        userService.updateProfile(currentUserId, body);
        return R.ok();
    }

    @Operation(summary = "本人修改密码")
    @PutMapping("/password")
    public R<Void> changePassword(@RequestBody Map<String, String> body,
                                  @RequestHeader("X-User-Id") Long currentUserId) {
        if (body == null) {
            return R.fail("参数不能为空");
        }
        String newPassword = body.get("newPassword");
        String confirmPassword = body.get("confirmPassword");
        if (confirmPassword != null && !confirmPassword.equals(newPassword)) {
            return R.fail("两次输入的新密码不一致");
        }
        userService.changePassword(currentUserId, body.get("oldPassword"), newPassword);
        return R.ok();
    }
}
