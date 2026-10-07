package com.hbbxl.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hbbxl.auth.entity.SysUser;
import com.hbbxl.auth.mapper.AuthUserMapper;
import com.hbbxl.auth.mapper.AuthUserRoleMapper;
import com.hbbxl.auth.service.AuthService;
import com.hbbxl.auth.service.CaptchaService;
import com.hbbxl.common.core.constant.CommonConstants;
import com.hbbxl.common.core.exception.BizException;
import com.hbbxl.common.security.utils.JwtUtil;
import com.hbbxl.common.security.utils.RsaUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/** 认证服务: 账号只由管理员开通, 无自助注册; 登录业务全部在此实现 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthUserMapper userMapper;
    private final AuthUserRoleMapper userRoleMapper;
    private final CaptchaService captchaService;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;

    @Override
    public LoginResult login(String username, String password, String encryptedPassword,
                             String captchaId, String captchaCode) {
        // 1. 图形校验码一次性校验(忽略大小写)
        if (!captchaService.verify(captchaId, captchaCode)) {
            throw new BizException(400, "校验码错误!");
        }
        // 2. RSA解密(前端加密传输时)
        if (encryptedPassword != null && !encryptedPassword.isBlank()) {
            try {
                password = RsaUtil.decryptByPrivateKey(encryptedPassword);
            } catch (Exception e) {
                throw new BizException(400, "密码解密失败, 请刷新页面重试");
            }
        }
        if (password == null || password.isBlank()) {
            throw new BizException(400, "密码不能为空");
        }
        // 3. 账号与状态
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
        // 4. 账号有效期校验(类会员制: 过期需管理员重新开通)
        if (user.getExpireTime() != null && user.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new BizException(400, "账号已过期, 请联系管理员重新开通");
        }
        // 5. 角色
        String roleCode = userRoleMapper.selectRoleCodeByUserId(user.getId());
        if (roleCode == null) {
            throw new BizException(400, "账号未分配角色, 请联系管理员");
        }
        // 6. 签发token并存储
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

    @Override
    public String[] generateCaptcha() {
        return captchaService.generate();
    }
}
