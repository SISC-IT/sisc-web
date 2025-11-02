package org.sejongisc.backend.common.auth.jwt;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

public class TokenEncryptor {
    private static final String SECRET_KEY = "1234567890123456"; // 환경변수로 관리 권장
    private static final String ALGORITHM = "AES";

    public static String encrypt(String token) {
        try {
            SecretKeySpec key = new SecretKeySpec(SECRET_KEY.getBytes(), ALGORITHM);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key);
            return Base64.getEncoder().encodeToString(cipher.doFinal(token.getBytes()));
        } catch (Exception e) {
            throw new RuntimeException("Token encryption failed", e);
        }
    }

    public static String decrypt(String encryptedToken) {
        try {
            SecretKeySpec key = new SecretKeySpec(SECRET_KEY.getBytes(), ALGORITHM);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key);
            return new String(cipher.doFinal(Base64.getDecoder().decode(encryptedToken)));
        } catch (Exception e) {
            throw new RuntimeException("Token decryption failed", e);
        }
    }
}
