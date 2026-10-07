package com.hbbxl.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hbbxl.auth.entity.SysUser;
import com.hbbxl.auth.mapper.AuthUserMapper;
import com.hbbxl.auth.mapper.AuthUserRoleMapper;
import com.hbbxl.auth.service.AuthService;
import com.hbbxl.common.core.constant.CommonConstants;
import com.hbbxl.common.core.exception.BizException;
import com.hbbxl.common.security.utils.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/** 认证服务: 账号只由管理员开通, 无自助注册 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthUserMapper userMapper;
    private final AuthUserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;

    @Override
    public LoginResult login(String username, String password) {
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username)
                .eq(SysUser::getDeleted, 0));
        if (user == null) {
            throw new BizException(400, "用户名或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BizException(400, "账号已被停用, 请联系管理员");
        }
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BizException(400, "用户名或密码错误");
        }
        String roleCode = userRoleMapper.selectRoleCodeByUserId(user.getId());
        if (roleCode == null) {
            throw new BizException(400, "账号未分配角色, 请联系管理员");
        }
        String token = JwtUtil.generateToken(user.getId(), user.getUsername(), roleCode);
        redisTemplate.opsForValue().set(
                CommonConstants.REDIS_TOKEN_PREFIX + token,
                String.valueOf(user.getId()),
                24, TimeUnit.HOURS);
        return new LoginResult(token, roleCode, user.getUsername(), user.getRealName(), user.getId());
    }

    @Override
    public void logout(String token) {
        if (token != null) {
            redisTemplate.delete(CommonConstants.REDIS_TOKEN_PREFIX + token);
        }
    }
}
