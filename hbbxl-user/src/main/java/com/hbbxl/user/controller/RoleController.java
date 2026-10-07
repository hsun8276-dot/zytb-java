package com.hbbxl.user.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hbbxl.common.core.auth.RequireRole;
import com.hbbxl.common.core.constant.CommonConstants;
import com.hbbxl.common.core.result.R;
import com.hbbxl.user.dao.SysRoleMapper;
import com.hbbxl.user.entity.SysRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 角色权限控制器（管理端）
 */
@Tag(name = "角色权限")
@RestController
@RequestMapping("/role")
@RequireRole(CommonConstants.ROLE_ADMIN)
public class RoleController {

    @Autowired
    private SysRoleMapper roleMapper;

    @Operation(summary = "角色列表")
    @GetMapping("/list")
    public R<IPage<SysRole>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return R.ok(roleMapper.selectPage(new Page<>(page, size), null));
    }

    @Operation(summary = "新增角色")
    @PostMapping
    public R<Void> create(@RequestBody SysRole role) {
        if (role.getCode() == null || role.getName() == null) {
            return R.fail("角色编码和名称不能为空");
        }
        roleMapper.insert(role);
        return R.ok();
    }

    @Operation(summary = "更新角色")
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody SysRole role) {
        SysRole existing = roleMapper.selectById(id);
        if (existing == null) {
            return R.fail("角色不存在");
        }
        if (role.getName() != null) existing.setName(role.getName());
        if (role.getDescription() != null) existing.setDescription(role.getDescription());
        roleMapper.updateById(existing);
        return R.ok();
    }

    @Operation(summary = "删除角色")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        // 检查是否有用户关联此角色
        Integer count = roleMapper.countUserRoleByRoleId(id);
        if (count != null && count > 0) {
            return R.fail("该角色下还有用户，无法删除");
        }
        roleMapper.deleteById(id);
        return R.ok();
    }

    @Operation(summary = "获取用户角色列表")
    @GetMapping("/user/list")
    public R<IPage<Map<String, Object>>> userRoleList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        // 先按用户分页，再补当前页用户的角色，多角色用户不会占用多行
        IPage<Map<String, Object>> users = roleMapper.selectUserRoleList(new Page<>(page, size));
        Map<Long, List<Map<String, Object>>> rolesByUser = new HashMap<>();
        if (!users.getRecords().isEmpty()) {
            List<Long> userIds = users.getRecords().stream()
                    .map(u -> ((Number) u.get("id")).longValue())
                    .toList();
            for (Map<String, Object> row : roleMapper.selectRolesByUserIds(userIds)) {
                Map<String, Object> role = new LinkedHashMap<>();
                role.put("id", row.get("roleId"));
                role.put("name", row.get("roleName"));
                role.put("code", row.get("roleCode"));
                rolesByUser.computeIfAbsent(((Number) row.get("userId")).longValue(), k -> new ArrayList<>()).add(role);
            }
        }
        for (Map<String, Object> user : users.getRecords()) {
            user.put("roles", rolesByUser.getOrDefault(((Number) user.get("id")).longValue(), new ArrayList<>()));
        }
        return R.ok(users);
    }

    @Operation(summary = "分配用户角色")
    @PostMapping("/user/assign")
    public R<Void> assignRole(@RequestParam Long userId, @RequestParam Long roleId) {
        // 检查是否已存在
        Integer count = roleMapper.countUserRole(userId, roleId);
        if (count != null && count > 0) {
            return R.fail("该用户已有此角色");
        }
        roleMapper.insertUserRoleMapping(userId, roleId);
        return R.ok();
    }

    @Operation(summary = "移除用户角色")
    @DeleteMapping("/user/remove")
    public R<Void> removeRole(@RequestParam Long userId, @RequestParam Long roleId) {
        roleMapper.deleteUserRole(userId, roleId);
        return R.ok();
    }
}