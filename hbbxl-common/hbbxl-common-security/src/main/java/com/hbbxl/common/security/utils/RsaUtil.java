package com.hbbxl.common.security.utils;

import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * RSA 加密工具类（用于前端密码加密传输）
 * <p>
 * 前端使用公钥加密密码，后端使用私钥解密，避免密码在 HTTP 传输中明文暴露。
 * 本地开发使用随机生成的密钥对；上线时建议从配置中心读取固定密钥对。
 */
public class RsaUtil {

    /** RSA 密钥长度 */
    private static final int KEY_SIZE = 2048;

    /** 本地随机生成的密钥对缓存 */
    private static volatile KeyPair cachedKeyPair;

    private RsaUtil() {
    }

    /**
     * 获取当前密钥对（首次调用时生成，之后复用）
     */
    public static KeyPair currentKeyPair() {
        if (cachedKeyPair == null) {
            synchronized (RsaUtil.class) {
                if (cachedKeyPair == null) {
                    try {
                        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
                        generator.initialize(KEY_SIZE);
                        cachedKeyPair = generator.generateKeyPair();
                    } catch (NoSuchAlgorithmException e) {
                        throw new IllegalStateException("RSA 算法不可用", e);
                    }
                }
            }
        }
        return cachedKeyPair;
    }

    /**
     * 重置密钥对（测试用）
     */
    public static synchronized void reset() {
        cachedKeyPair = null;
    }

    /**
     * 获取公钥的 Base64 字符串（返回给前端用于加密）
     */
    public static String getPublicKeyBase64() {
        return Base64.getEncoder().encodeToString(currentKeyPair().getPublic().getEncoded());
    }

    /**
     * 用公钥 Base64 加密：base64(公钥加密后的密文)
     */
    public static String encryptByPublicKey(String plainText) {
        try {
            PublicKey publicKey = currentKeyPair().getPublic();
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new IllegalStateException("RSA 加密失败", e);
        }
    }

    /**
     * 用私钥解密：入参为 base64 密文
     */
    public static String decryptByPrivateKey(String cipherText) {
        try {
            PrivateKey privateKey = currentKeyPair().getPrivate();
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(cipherText));
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("RSA 解密失败", e);
        }
    }

    /**
     * 标准 JWT 风格密钥对生成：返回 {publicKey, privateKey} Base64 字符串（PEM body）
     */
    public static Map<String, String> generateKeyPairBase64() throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(KEY_SIZE);
        KeyPair pair = generator.generateKeyPair();
        Map<String, String> result = new HashMap<>();
        result.put("publicKey", Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()));
        result.put("privateKey", Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()));
        return result;
    }

    /** 从 Base64 字符串恢复公钥 */
    public static PublicKey fromPublicKeyBase64(String base64) throws Exception {
        KeyFactory factory = KeyFactory.getInstance("RSA");
        return factory.generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(base64)));
    }

    /** 从 Base64 字符串恢复私钥 */
    public static PrivateKey fromPrivateKeyBase64(String base64) throws Exception {
        KeyFactory factory = KeyFactory.getInstance("RSA");
        return factory.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
    }
}