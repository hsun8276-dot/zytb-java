package com.hbbxl.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hbbxl.auth.entity.SysConfig;
import com.hbbxl.auth.entity.SysRole;
import com.hbbxl.auth.entity.SysUser;
import com.hbbxl.auth.mapper.AuthRoleMapper;
import com.hbbxl.auth.mapper.AuthUserMapper;
import com.hbbxl.auth.mapper.AuthUserRoleMapper;
import com.hbbxl.auth.mapper.SysConfigMapper;
import com.hbbxl.auth.service.AuthService;
import com.hbbxl.common.core.constant.CommonConstants;
import com.hbbxl.common.core.exception.BizException;
import com.hbbxl.common.security.utils.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

/**
 * 认证服务实现
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthUserMapper userMapper;
    private final AuthRoleMapper roleMapper;
    private final AuthUserRoleMapper userRoleMapper;
    private final SysConfigMapper sysConfigMapper;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;

    @Override
    public LoginResult login(String username, String password) {
        // 查询用户
        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, username)
                        .eq(SysUser::getDeleted, 0)
        );
        if (user == null) {
            throw new BizException(400, "用户名或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BizException(400, "账号已被禁用");
        }

        // 校验密码
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BizException(400, "用户名或密码错误");
        }

        // 查询角色
        String roleCode = userRoleMapper.selectRoleCodeByUserId(user.getId());
        if (roleCode == null) {
            roleCode = CommonConstants.ROLE_STUDENT;
        }

        // 生成 Token
        String token = JwtUtil.generateToken(user.getId(), user.getUsername(), roleCode);

        // 存 Redis（key: token, value: userId, 过期时间 24 小时）
        redisTemplate.opsForValue().set(
                CommonConstants.REDIS_TOKEN_PREFIX + token,
                String.valueOf(user.getId()),
                24,
                TimeUnit.HOURS
        );

        return new LoginResult(token, roleCode, user.getUsername(), user.getRealName(), user.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(String phone, String password, String role) {
        // 检查是否开放注册：没有这条配置时按未开放处理
        SysConfig openRegister = sysConfigMapper.selectById("open_register");
        if (openRegister == null || !"true".equalsIgnoreCase(openRegister.getConfigValue())) {
            throw new BizException(400, "系统暂未开放注册，请联系管理员开通账号");
        }
        // 自助注册只能注册学生，老师和管理员账号由管理员开通
        if (!CommonConstants.ROLE_STUDENT.equals(role)) {
            throw new BizException(400, "只能自助注册学生账号");
        }

        // 检查手机号是否已注册
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, phone)
        );
        if (count > 0) {
            throw new BizException(400, "该手机号已注册");
        }

        // 查询角色
        SysRole roleEntity = roleMapper.selectOne(
                new LambdaQueryWrapper<SysRole>().eq(SysRole::getCode, role)
        );
        if (roleEntity == null) {
            throw new BizException(400, "无效的角色类型");
        }

        // 创建用户(默认二次元头像)
        SysUser user = new SysUser();
        user.setUsername(phone);
        user.setPassword(passwordEncoder.encode(password));
        user.setPhone(phone);
        user.setAvatar(CommonConstants.DEFAULT_AVATAR);
        userMapper.insert(user);

        // 关联角色
        userRoleMapper.insertUserRole(user.getId(), roleEntity.getId());
    }

    @Override
    public void logout(String token) {
        if (token != null) {
            redisTemplate.delete(CommonConstants.REDIS_TOKEN_PREFIX + token);
        }
    }
}