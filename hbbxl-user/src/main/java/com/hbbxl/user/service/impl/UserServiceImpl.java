package com.hbbxl.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hbbxl.common.core.constant.CommonConstants;
import com.hbbxl.common.core.exception.BizException;
import com.hbbxl.user.dao.SysUserMapper;
import com.hbbxl.user.dao.SysUserRoleMapper;
import com.hbbxl.user.entity.SysUser;
import com.hbbxl.user.entity.SysRole;
import com.hbbxl.user.dao.SysRoleMapper;
import com.hbbxl.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

/** 用户服务: 账号只由管理员开通, 角色固定为user */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final String ROLE_USER = "user";
    private static final String DEFAULT_PASSWORD = "123456";

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public IPage<Map<String, Object>> pageList(int page, int size, String keyword) {
        return userMapper.pageList(new Page<>(page, size), (keyword == null || keyword.isBlank()) ? null : keyword.trim());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(String username, String realName, String password, Long currentUserId) {
        if (username == null || username.isBlank()) {
            throw new BizException("账号(手机号)不能为空");
        }
        if (realName == null || realName.isBlank()) {
            throw new BizException("姓名不能为空");
        }
        Long count = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username.trim())
                .eq(SysUser::getDeleted, 0));
        if (count > 0) {
            throw new BizException("该账号已存在");
        }
        SysRole role = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>().eq(SysRole::getCode, ROLE_USER));
        if (role == null) {
            throw new BizException("系统缺少 user 角色, 请先初始化角色数据");
        }
        SysUser user = new SysUser();
        user.setUsername(username.trim());
        user.setRealName(realName.trim());
        user.setPhone(username.trim());
        user.setPassword(passwordEncoder.encode(password == null || password.isBlank() ? DEFAULT_PASSWORD : password));
        user.setStatus(1);
        user.setExpireTime(LocalDateTime.now().plusYears(1)); // 开通有效期1年
        user.setAvatar(CommonConstants.DEFAULT_AVATAR);
        userMapper.insert(user);
        userRoleMapper.insertUserRole(user.getId(), role.getId());
        return user.getId();
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BizException("状态不合法");
        }
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        if (CommonConstants.ROLE_ADMIN.equals(userRoleMapper.selectRoleCodeByUserId(id))) {
            throw new BizException("不能停用管理员账号");
        }
        user.setStatus(status);
        userMapper.updateById(user);
    }

    @Override
    public void renew(Long id, int years) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        // 未到期顺延, 已过期从现在起算
        LocalDateTime base = user.getExpireTime() != null && user.getExpireTime().isAfter(LocalDateTime.now())
                ? user.getExpireTime() : LocalDateTime.now();
        user.setExpireTime(base.plusYears(years));
        // 到期自动停用过的账号, 重新开通时恢复启用
        user.setStatus(1);
        userMapper.updateById(user);
    }

    @Override
    public int autoDisableExpired() {
        return userMapper.disableExpired();
    }

    @Override
    public Map<String, Object> detail(Long userId, Long currentUserId, String role) {
        if (!userId.equals(currentUserId) && !CommonConstants.ROLE_ADMIN.equals(role)) {
            throw new BizException(403, "无权查看该用户信息");
        }
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        Map<String, Object> data = new java.util.HashMap<>();
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
        return data;
    }

    @Override
    public void resetPassword(Long id, String password) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        user.setPassword(passwordEncoder.encode(password == null || password.isBlank() ? DEFAULT_PASSWORD : password));
        userMapper.updateById(user);
    }

    @Override
    public void updateProfile(Long currentUserId, Map<String, Object> body) {
        if (currentUserId == null) {
            throw new BizException("未登录");
        }
        SysUser user = userMapper.selectById(currentUserId);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        if (body == null) {
            throw new BizException("参数不能为空");
        }
        if (body.containsKey("realName")) {
            String realName = str(body.get("realName"));
            if (realName == null || realName.isBlank()) {
                throw new BizException("姓名不能为空");
            }
            if (realName.length() > 50) {
                throw new BizException("姓名过长");
            }
            user.setRealName(realName.trim());
        }
        if (body.containsKey("nickname")) {
            String nickname = str(body.get("nickname"));
            if (nickname != null && nickname.length() > 50) {
                throw new BizException("昵称过长");
            }
            user.setNickname(nickname == null ? "" : nickname.trim());
        }
        if (body.containsKey("phone")) {
            String phone = str(body.get("phone"));
            if (phone != null && !phone.isBlank() && !phone.matches("^1\\d{10}$")) {
                throw new BizException("手机号格式不正确");
            }
            user.setPhone(phone == null ? "" : phone.trim());
        }
        if (body.containsKey("email")) {
            String email = str(body.get("email"));
            if (email != null && !email.isBlank() && !email.matches("^[\\w.+-]+@[\\w.-]+\\.[A-Za-z]{2,}$")) {
                throw new BizException("邮箱格式不正确");
            }
            if (email != null && email.length() > 100) {
                throw new BizException("邮箱过长");
            }
            user.setEmail(email == null ? "" : email.trim());
        }
        if (body.containsKey("gender")) {
            Integer gender = toInt(body.get("gender"));
            if (gender == null || (gender != 0 && gender != 1 && gender != 2)) {
                throw new BizException("性别不合法");
            }
            user.setGender(gender);
        }
        if (body.containsKey("signature")) {
            String signature = str(body.get("signature"));
            if (signature != null && signature.length() > 255) {
                throw new BizException("签名过长");
            }
            user.setSignature(signature == null ? "" : signature.trim());
        }
        if (body.containsKey("avatar")) {
            String avatar = str(body.get("avatar"));
            if (avatar != null && avatar.length() > 255) {
                throw new BizException("头像地址过长");
            }
            user.setAvatar(avatar == null || avatar.isBlank() ? CommonConstants.DEFAULT_AVATAR : avatar.trim());
        }
        userMapper.updateById(user);
    }

    @Override
    public void changePassword(Long currentUserId, String oldPassword, String newPassword) {
        if (currentUserId == null) {
            throw new BizException("未登录");
        }
        if (oldPassword == null || oldPassword.isBlank()) {
            throw new BizException("请输入原密码");
        }
        if (newPassword == null || newPassword.isBlank()) {
            throw new BizException("请输入新密码");
        }
        if (newPassword.length() < 6 || newPassword.length() > 32) {
            throw new BizException("新密码长度需为6-32位");
        }
        if (oldPassword.equals(newPassword)) {
            throw new BizException("新密码不能与原密码相同");
        }
        SysUser user = userMapper.selectById(currentUserId);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BizException("原密码不正确");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static Integer toInt(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(v).trim());
        } catch (Exception e) {
            return null;
        }
    }
}
