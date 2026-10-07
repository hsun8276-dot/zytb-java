package com.hbbxl.auth.service;

import com.hbbxl.common.core.constant.CommonConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 图形校验码: 生成4位码(去易混淆字符)存Redis(5分钟, 一次性), 绘制干扰线+旋转字符的PNG
 */
@Service
@RequiredArgsConstructor
public class CaptchaService {

    private static final String CAPTCHA_PREFIX = "hbbxl:captcha:";
    private static final String CHARS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";

    private final StringRedisTemplate redisTemplate;

    /** 生成校验码: 返回 [captchaId, base64图片(data:image/png;base64,...)] */
    public String[] generate() {
        String code = randomCode();
        String captchaId = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set(CAPTCHA_PREFIX + captchaId, code.toLowerCase(), 5, TimeUnit.MINUTES);
        return new String[]{captchaId, draw(code)};
    }

    /** 校验(忽略大小写, 一次性): 正确返回true并删除 */
    public boolean verify(String captchaId, String code) {
        if (captchaId == null || code == null || code.isBlank()) return false;
        String key = CAPTCHA_PREFIX + captchaId;
        String saved = redisTemplate.opsForValue().get(key);
        redisTemplate.delete(key);
        return saved != null && saved.equalsIgnoreCase(code.trim());
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            sb.append(CHARS.charAt((int) (Math.random() * CHARS.length())));
        }
        return sb.toString();
    }

    /** 绘制校验码图片(120x44, 干扰线+随机旋转字符) */
    private String draw(String code) {
        int w = 120, h = 44;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // 背景
        g.setColor(new Color(245, 247, 250));
        g.fillRect(0, 0, w, h);
        // 干扰线
        for (int i = 0; i < 6; i++) {
            g.setColor(new Color(200 + (int) (Math.random() * 40), 205 + (int) (Math.random() * 40), 215 + (int) (Math.random() * 40)));
            g.drawLine((int) (Math.random() * w), (int) (Math.random() * h),
                    (int) (Math.random() * w), (int) (Math.random() * h));
        }
        // 逐字符绘制(随机颜色/纵向偏移/轻微旋转)
        Font base = new Font("Arial", Font.BOLD, 28);
        for (int i = 0; i < code.length(); i++) {
            Graphics2D cg = (Graphics2D) g.create();
            cg.setColor(new Color(60 + (int) (Math.random() * 90), 70 + (int) (Math.random() * 90), 90 + (int) (Math.random() * 100)));
            cg.setFont(base.deriveFont((float) (26 + Math.random() * 6)));
            double rot = (Math.random() - 0.5) * 0.6;
            int x = 12 + i * 26, y = h / 2 + 10 + (int) (Math.random() * 8 - 4);
            cg.rotate(rot, x, y);
            cg.drawString(String.valueOf(code.charAt(i)), x, y);
            cg.dispose();
        }
        // 前景噪点
        for (int i = 0; i < 30; i++) {
            g.setColor(new Color(150 + (int) (Math.random() * 100), 150 + (int) (Math.random() * 100), 160 + (int) (Math.random() * 90)));
            g.fillRect((int) (Math.random() * w), (int) (Math.random() * h), 1, 1);
        }
        g.dispose();
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", bos);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(bos.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException("校验码图片生成失败", e);
        }
    }
}
