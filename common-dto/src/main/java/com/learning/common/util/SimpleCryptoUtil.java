package com.learning.common.util;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

public final class SimpleCryptoUtil {

    private static final String ALGO = "AES/GCM/NoPadding";
    private static final int KEY_SIZE = 128;
    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_SIZE = 12;

    // DO NOT keep this here in real systems — use AWS KMS / Vault / Secrets Manager.
    private static final String BASE64_KEY = System.getenv().getOrDefault(
            "MASTER_SECRET_KEY",
            "kPn9Kp7FvBV6dPxZcug3FA=="
    );

    private static SecretKey secretKey() {
        byte[] decoded = Base64.getDecoder().decode(BASE64_KEY);
        return new SecretKeySpec(decoded, "AES");
    }

    public static String encrypt(String plainText) {
        try {
            byte[] iv = new byte[IV_SIZE];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey(), new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] result = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(encrypted, 0, result, iv.length, encrypted.length);

            return Base64.getEncoder().encodeToString(result);
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    public static String decrypt(String cipherText) {
        try {
            byte[] decoded = Base64.getDecoder().decode(cipherText);

            byte[] iv = new byte[IV_SIZE];
            byte[] cipherBytes = new byte[decoded.length - IV_SIZE];

            System.arraycopy(decoded, 0, iv, 0, IV_SIZE);
            System.arraycopy(decoded, IV_SIZE, cipherBytes, 0, cipherBytes.length);

            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] decrypted = cipher.doFinal(cipherBytes);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }

    // Generate a secure key once (for ops team, CI/CD bootstrap)
    public static String generateKey() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(KEY_SIZE);
            return Base64.getEncoder().encodeToString(keyGen.generateKey().getEncoded());
        } catch (Exception e) {
            throw new RuntimeException("Key generation failed", e);
        }
    }
}
