package com.xiangqi.server.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 对局令牌：32 字节随机数，服务端只保存 SHA-256 摘要，比较时使用常量时间算法。
 */
public final class Tokens {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private Tokens() {
    }

    public static String issue() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return ENCODER.encodeToString(bytes);
    }

    public static byte[] hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(token.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 缺少 SHA-256", exception);
        }
    }

    public static boolean matches(String provided, byte[] expectedHash) {
        if (provided == null || provided.isBlank() || expectedHash == null) {
            return false;
        }
        return MessageDigest.isEqual(hash(provided), expectedHash);
    }

    /**
     * 令牌摘要转十六进制字符串，仅用于本地文件持久化，绝不返回给客户端。
     */
    public static String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(Character.forDigit((value >> 4) & 0xF, 16))
                    .append(Character.forDigit(value & 0xF, 16));
        }
        return builder.toString();
    }

    /**
     * 十六进制字符串还原为摘要；格式非法时返回 null。
     */
    public static byte[] unhex(String hex) {
        if (hex == null || hex.length() % 2 != 0) {
            return null;
        }
        byte[] bytes = new byte[hex.length() / 2];
        for (int index = 0; index < bytes.length; index++) {
            int high = Character.digit(hex.charAt(index * 2), 16);
            int low = Character.digit(hex.charAt(index * 2 + 1), 16);
            if (high < 0 || low < 0) {
                return null;
            }
            bytes[index] = (byte) ((high << 4) | low);
        }
        return bytes;
    }
}
