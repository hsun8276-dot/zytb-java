package com.hbbxl.gateway.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * 网关 JWT 校验工具
 * <p>
 * 密钥与 hbbxl-common-security 的 JwtUtil 保持一致，
 * 通过共享密钥在网关侧完成无状态校验，无需调用 auth 服务。
 */
public class JwtUtil {

    /** 密钥（与 auth 服务签发保持一致） */
    private static final String SECRET = "hbbxl-jwt-secret-key-2026-hb-danzhao-duikou-education-platform";

    private static final SecretKey KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    private JwtUtil() {
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
     * 从 Header 中提取 Token（去掉 Bearer 前缀）
     */
    public static String extractToken(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        return header.startsWith("Bearer ") ? header.substring(7) : header;
    }
}