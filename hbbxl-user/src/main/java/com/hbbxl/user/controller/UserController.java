package com.hbbxl.user.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hbbxl.common.core.auth.RequireRole;
import com.hbbxl.common.core.constant.CommonConstants;
import com.hbbxl.common.core.result.R;
import com.hbbxl.user.entity.SysUser;
import com.hbbxl.user.dao.SysUserRoleMapper;
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
    private final SysUserRoleMapper userRoleMapper;
    private final com.hbbxl.user.dao.SysUserMapper userMapper;

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

    @Operation(summary = "重新开通/续期(管理员): 默认1年, 未到期顺延")
    @PutMapping("/{id}/renew")
    @RequireRole(CommonConstants.ROLE_ADMIN)
    public R<Void> renew(@PathVariable Long id, @RequestParam(defaultValue = "1") int years) {
        userService.renew(id, years);
        return R.ok();
    }

    @Operation(summary = "用户详情(本人或管理员)")
    @GetMapping("/detail")
    public R<Map<String, Object>> detail(@RequestParam Long userId,
                                         @RequestHeader("X-User-Id") Long currentUserId,
                                         @RequestHeader(value = "X-User-Role", required = false) String role) {
        if (!userId.equals(currentUserId) && !CommonConstants.ROLE_ADMIN.equals(role)) {
            return R.fail(CommonConstants.FORBIDDEN, "无权查看该用户信息");
        }
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            return R.fail("用户不存在");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("id", user.getId());
        data.put("username", user.getUsername());
        data.put("realName", user.getRealName());
        data.put("nickname", user.getNickname());
        data.put("phone", user.getPhone());
        data.put("email", user.getEmail());
        data.put("avatar", user.getAvatar());
        data.put("signature", user.getSignature());
        data.put("gender", user.getGender());
        data.put("status", user.getStatus());
        data.put("expireTime", user.getExpireTime());
        data.put("role", userRoleMapper.selectRoleCodeByUserId(userId));
        return R.ok(data);
    }
}
