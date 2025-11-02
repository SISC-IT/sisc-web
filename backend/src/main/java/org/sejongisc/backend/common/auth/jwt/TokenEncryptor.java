package org.sejongisc.backend.common.auth.jwt;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

public class TokenEncryptor {
    private static final String ALGORITHM = "AES";
    private static final String SECRET_KEY_ENV = "TOKEN_ENCRYPTION_KEY";
    private static final int GCM_IV_LENGTH = 12; // 12 bytes recommended for GCM
    private static final int GCM_TAG_LENGTH = 128; // 128 bits

    private static SecretKeySpec loadKey() {
        String key = System.getenv(SECRET_KEY_ENV);
        if (key == null || key.length() != 16) {
            throw new IllegalStateException(
                    "유효한 16바이트 토큰 암호화 키가 설정되지 않았습니다. 환경변수 TOKEN_ENCRYPTION_KEY를 확인하세요.");
        }
        return new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    public static String encrypt(String token) {
        try {
            SecretKeySpec key = loadKey();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            byte[] iv = new byte[GCM_IV_LENGTH];
            SecureRandom random;
            try {
                random = SecureRandom.getInstanceStrong();
            } catch (Exception ex) {
                random = new SecureRandom();
            }
            random.nextBytes(iv);

            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, spec);

            byte[] ciphertext = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));

            // IV + ciphertext 합쳐서 Base64로 반환
            ByteBuffer bb = ByteBuffer.allocate(iv.length + ciphertext.length);
            bb.put(iv);
            bb.put(ciphertext);
            return Base64.getEncoder().encodeToString(bb.array());
        } catch (Exception e) {
            throw new RuntimeException("Token encryption failed", e);
        }
    }

    public static String decrypt(String encryptedToken) {
        try {
            SecretKeySpec key = loadKey();
            byte[] decoded = Base64.getDecoder().decode(encryptedToken);

            // IV(앞 GCM_IV_LENGTH 바이트) 분리
            ByteBuffer bb = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[GCM_IV_LENGTH];
            bb.get(iv);
            byte[] ciphertext = new byte[bb.remaining()];
            bb.get(ciphertext);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, spec);

            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Token decryption failed", e);
        }
    }
}
