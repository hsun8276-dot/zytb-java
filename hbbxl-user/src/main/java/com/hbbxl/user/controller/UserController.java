package com.hbbxl.user.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hbbxl.common.core.auth.RequireRole;
import com.hbbxl.common.core.auth.RoleUtil;
import com.hbbxl.common.core.constant.CommonConstants;
import com.hbbxl.common.core.exception.BizException;
import com.hbbxl.common.core.result.R;
import com.hbbxl.common.security.utils.RsaUtil;
import com.hbbxl.user.dao.StudentMapper;
import com.hbbxl.user.dao.SysRoleMapper;
import com.hbbxl.user.dao.SysUserMapper;
import com.hbbxl.user.dao.SysUserRoleMapper;
import com.hbbxl.user.entity.SysRole;
import com.hbbxl.user.entity.SysUser;
import com.hbbxl.user.util.MinioUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户控制器
 */
@Tag(name = "用户管理")
@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private SysRoleMapper roleMapper;

    @Autowired
    private SysUserRoleMapper userRoleMapper;

    @Autowired
    private MinioUtil minioUtil;

    @Autowired
    private StudentMapper studentMapper;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /** 开通账号时允许的目标角色（按操作用户角色限制） */
    private static final Map<String, List<String>> ALLOWED_ROLES = new HashMap<>();

    static {
        // 老师只能给学生开通账号
        ALLOWED_ROLES.put("teacher", List.of("student"));
        // 管理员（校长）可以给学生、老师开通账号
        ALLOWED_ROLES.put("admin", List.of("student", "teacher", "admin"));
    }

    @Operation(summary = "获取当前用户信息")
    @GetMapping("/info")
    public R<Map<String, Object>> info(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Username", required = false) String username,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        Map<String, Object> data = new HashMap<>();
        data.put("userId", userId);
        data.put("username", username);
        data.put("role", role);
        return R.ok(data);
    }

    @Operation(summary = "获取用户详细信息")
    @GetMapping("/detail")
    public R<Map<String, Object>> detail(@RequestParam Long userId,
                                         @RequestHeader("X-User-Id") Long currentUserId,
                                         @RequestHeader(value = "X-User-Role", required = false) String role) {
        if (!userId.equals(currentUserId) && !RoleUtil.isTeacherOrAdmin(role)) {
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
        data.put("gender", user.getGender());
        data.put("signature", user.getSignature());
        data.put("status", user.getStatus());
        return R.ok(data);
    }

    @Operation(summary = "更新个人信息")
    @PutMapping("/profile")
    public R<Void> updateProfile(@RequestHeader("X-User-Id") Long userId,
                                 @RequestBody SysUser update) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            return R.fail("用户不存在");
        }
        if (update.getRealName() != null) user.setRealName(update.getRealName());
        if (update.getNickname() != null) user.setNickname(update.getNickname());
        // 手机号即登录用户名，不允许用户自行修改（防止绕过前端直接调接口）
        if (update.getEmail() != null) user.setEmail(update.getEmail());
        if (update.getAvatar() != null) user.setAvatar(update.getAvatar());
        if (update.getGender() != null) user.setGender(update.getGender());
        if (update.getSignature() != null) user.setSignature(update.getSignature());
        userMapper.updateById(user);
        return R.ok();
    }

    @Operation(summary = "修改密码")
    @PutMapping("/password")
    public R<Void> updatePassword(@RequestHeader("X-User-Id") Long userId,
                                  @RequestBody Map<String, String> body) {
        String oldPassword = body.get("oldPassword");
        String newPassword = body.get("newPassword");
        if (oldPassword == null || newPassword == null) {
            return R.fail("参数不完整");
        }
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            return R.fail("用户不存在");
        }
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            return R.fail("原密码错误");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);
        return R.ok();
    }

    @Operation(summary = "上传头像")
    @PostMapping("/avatar")
    public R<String> uploadAvatar(@RequestHeader("X-User-Id") Long userId,
                                  @RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return R.fail("请选择文件");
        }
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            return R.fail("用户不存在");
        }
        // 上传到 MinIO avatars 目录
        String url = minioUtil.upload("avatars", file);
        // 更新用户头像
        user.setAvatar(url);
        userMapper.updateById(user);
        return R.ok(url);
    }

    @Operation(summary = "学生列表（仅学生角色，教师端用）")
    @GetMapping("/list/students")
    @RequireRole({CommonConstants.ROLE_TEACHER, CommonConstants.ROLE_ADMIN})
    public R<IPage<Map<String, Object>>> listStudents(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        IPage<Map<String, Object>> rows = userMapper.listStudents(new Page<>(page, size), keyword);
        return R.ok(rows);
    }

    @Operation(summary = "用户列表（管理端）")
    @GetMapping("/list")
    @RequireRole(CommonConstants.ROLE_ADMIN)
    public R<IPage<Map<String, Object>>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String roleCode) {
        // 先按用户分页，再补当前页用户的角色，多角色用户不会占用多行
        IPage<Map<String, Object>> users = userMapper.listAllUsers(new Page<>(page, size), roleCode);
        Map<Long, List<Map<String, Object>>> rolesByUser = rolesByUserId(users.getRecords());
        for (Map<String, Object> user : users.getRecords()) {
            Long uid = ((Number) user.get("id")).longValue();
            user.put("roles", rolesByUser.getOrDefault(uid, new ArrayList<>()));
        }
        return R.ok(users);
    }

    /** 查询一页用户的角色，按用户 id 分组，元素为 {id, code, name} */
    private Map<Long, List<Map<String, Object>>> rolesByUserId(List<Map<String, Object>> users) {
        Map<Long, List<Map<String, Object>>> result = new HashMap<>();
        if (users.isEmpty()) {
            return result;
        }
        List<Long> userIds = users.stream().map(u -> ((Number) u.get("id")).longValue()).toList();
        for (Map<String, Object> row : roleMapper.selectRolesByUserIds(userIds)) {
            Map<String, Object> role = new LinkedHashMap<>();
            role.put("id", row.get("roleId"));
            role.put("code", row.get("roleCode"));
            role.put("name", row.get("roleName"));
            result.computeIfAbsent(((Number) row.get("userId")).longValue(), k -> new ArrayList<>()).add(role);
        }
        return result;
    }

    @Operation(summary = "开通账号（老师开通学生 / 管理员开通任意角色）")
    @PostMapping("/create")
    @RequireRole({CommonConstants.ROLE_TEACHER, CommonConstants.ROLE_ADMIN})
    @Transactional(rollbackFor = Exception.class)
    public R<Void> create(@RequestHeader("X-User-Role") String operatorRole,
                          @RequestBody CreateUserRequest request) {
        if (request.getPhone() == null || request.getPhone().isBlank()) {
            return R.fail("手机号不能为空");
        }
        boolean isStudent = CommonConstants.ROLE_STUDENT.equals(request.getRole());
        if (isStudent && request.getCategoryId() == null) {
            return R.fail("学生账号必须选择考试大类");
        }

        // 支持 RSA 加密密码传输
        String password = request.getPassword();
        if (request.getEncryptedPassword() != null && !request.getEncryptedPassword().isBlank()) {
            try {
                password = RsaUtil.decryptByPrivateKey(request.getEncryptedPassword());
            } catch (Exception e) {
                return R.fail("密码解密失败，请刷新页面重试");
            }
        }
        if (password == null || password.isBlank()) {
            return R.fail("密码不能为空");
        }
        if (password.length() < 6) {
            return R.fail("密码至少6位");
        }

        // 角色权限校验：老师只能开通学生
        List<String> allowed = ALLOWED_ROLES.get(operatorRole);
        if (allowed == null || !allowed.contains(request.getRole())) {
            return R.fail("无权开通该角色的账号");
        }

        // 手机号作为登录用户名，需唯一
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, request.getPhone()));
        if (count > 0) {
            return R.fail("该手机号已存在");
        }

        // 校验角色编码
        SysRole role = roleMapper.selectOne(
                new LambdaQueryWrapper<SysRole>().eq(SysRole::getCode, request.getRole()));
        if (role == null) {
            return R.fail("无效的角色类型");
        }

        // 创建用户(默认二次元头像)
        SysUser user = new SysUser();
        user.setUsername(request.getPhone());
        user.setPassword(passwordEncoder.encode(password));
        user.setPhone(request.getPhone());
        user.setRealName(request.getRealName());
        user.setAvatar(CommonConstants.DEFAULT_AVATAR);
        user.setStatus(1);
        userMapper.insert(user);

        // 关联角色
        userRoleMapper.insertUserRole(user.getId(), role.getId());

        // 学生角色：自动写入 student_info（考试大类）；写不进去时抛异常回滚前面的用户和角色
        if (isStudent && studentMapper.insertStudentInfo(user.getId(), request.getCategoryId()) == 0) {
            throw new BizException(400, "所选考试大类不存在或已停用");
        }

        return R.ok();
    }

    @Operation(summary = "更新用户状态（管理端）")
    @PutMapping("/{id}/status")
    @RequireRole(CommonConstants.ROLE_ADMIN)
    public R<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            return R.fail("用户不存在");
        }
        user.setStatus(status);
        userMapper.updateById(user);
        return R.ok();
    }

    @Operation(summary = "重置用户密码（管理员可重置任意账号，老师只能重置学生）")
    @PutMapping("/{id}/password/reset")
    @RequireRole({CommonConstants.ROLE_TEACHER, CommonConstants.ROLE_ADMIN})
    public R<Void> resetPassword(@PathVariable Long id,
                                 @RequestHeader("X-User-Role") String operatorRole,
                                 @RequestBody Map<String, String> body) {
        String password = body.get("password");
        if (password == null || password.length() < 6) {
            return R.fail("新密码至少6位");
        }
        SysUser user = userMapper.selectById(id);
        if (user == null || Integer.valueOf(1).equals(user.getDeleted())) {
            return R.fail("用户不存在");
        }
        if (!RoleUtil.isAdmin(operatorRole)) {
            boolean onlyStudent = roleMapper.selectRolesByUserIds(List.of(id)).stream()
                    .allMatch(r -> CommonConstants.ROLE_STUDENT.equals(r.get("roleCode")));
            if (!onlyStudent) {
                return R.fail(CommonConstants.FORBIDDEN, "老师只能重置学生账号的密码");
            }
        }
        user.setPassword(passwordEncoder.encode(password));
        userMapper.updateById(user);
        return R.ok();
    }

    /** 开通账号请求体 */
    public static class CreateUserRequest {
        private String phone;
        private String password;
        private String realName;
        private String role;
        /** RSA 加密后的密码（优先于 password 字段） */
        private String encryptedPassword;
        /** 考试大类ID（学生必填） */
        private Long categoryId;

        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getRealName() { return realName; }
        public void setRealName(String realName) { this.realName = realName; }
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public String getEncryptedPassword() { return encryptedPassword; }
        public void setEncryptedPassword(String encryptedPassword) { this.encryptedPassword = encryptedPassword; }
        public Long getCategoryId() { return categoryId; }
        public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    }
}