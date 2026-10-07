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
}
