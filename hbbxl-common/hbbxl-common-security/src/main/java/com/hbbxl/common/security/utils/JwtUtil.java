package com.hbbxl.common.security.utils;

import com.hbbxl.common.core.constant.CommonConstants;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT 工具类
 */
public class JwtUtil {

    /** 密钥（生产环境应从配置读取） */
    private static final String SECRET = "hbbxl-jwt-secret-key-2026-hb-danzhao-duikou-education-platform";

    /** Token 有效期：24小时 */
    private static final long EXPIRE_MILLIS = 24 * 60 * 60 * 1000L;

    private static final SecretKey KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    private JwtUtil() {
    }

    /**
     * 生成 Token
     *
     * @param userId   用户ID
     * @param username 用户名
     * @param role     角色
     */
    public static String generateToken(Long userId, String username, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);
        claims.put("role", role);
        Date now = new Date();
        return Jwts.builder()
                .claims(claims)
                .subject(username)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + EXPIRE_MILLIS))
                .signWith(KEY)
                .compact();
    }

    /**
     * 解析 Token，失败返回 null
     */
    public static Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(KEY)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 校验 Token 是否有效
     */
    public static boolean validateToken(String token) {
        return parseToken(token) != null;
    }

    /**
     * 从 Header 中提取 Token（去掉 Bearer 前缀）
     */
    public static String extractToken(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        if (header.startsWith(CommonConstants.HEADER_TOKEN_PREFIX)) {
            return header.substring(CommonConstants.HEADER_TOKEN_PREFIX.length());
        }
        return header;
    }
}