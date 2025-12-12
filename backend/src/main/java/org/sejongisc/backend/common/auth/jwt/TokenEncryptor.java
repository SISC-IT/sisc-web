package org.sejongisc.backend.common.auth.jwt;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class TokenEncryptor {
    private static final String ALGORITHM = "AES";
//    private static final String SECRET_KEY_ENV = "TOKEN_ENCRYPTION_KEY";
    private static final int GCM_IV_LENGTH = 12; // 12 bytes recommended for GCM
    private static final int GCM_TAG_LENGTH = 128; // 128 bits

    private final SecretKeySpec secretKey;

    public TokenEncryptor(@Value("${TOKEN_ENCRYPTION_KEY:mySecretKey12345}") String key) {
        if (key == null || key.length() != 16) {
            throw new IllegalStateException(
                    "유효한 16바이트 토큰 암호화 키가 설정되지 않았습니다. 환경변수 TOKEN_ENCRYPTION_KEY를 확인하세요.");
        }
        this.secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    public  String encrypt(String token) {
        if (token == null || token.isEmpty()) {
            throw new IllegalArgumentException("암호화할 토큰이 null이거나 비어있습니다.");
        }

        try {
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
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);

            byte[] ciphertext = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));

            // IV + ciphertext 합쳐서 Base64로 반환
            ByteBuffer bb = ByteBuffer.allocate(iv.length + ciphertext.length);
            bb.put(iv);
            bb.put(ciphertext);
            return Base64.getEncoder().encodeToString(bb.array());
        } catch (IllegalStateException | IllegalArgumentException e) {
            // 키 로드 실패나 입력 검증 오류
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("토큰 암호화에 실패했습니다.", e);
        }
    }

    public  String decrypt(String encryptedToken) {
        if (encryptedToken == null || encryptedToken.isEmpty()) {
            throw new IllegalArgumentException("복호화할 토큰이 null이거나 비어있습니다.");
        }

        try {

            byte[] decoded = Base64.getDecoder().decode(encryptedToken);

            if (decoded.length < GCM_IV_LENGTH) {
                throw new IllegalArgumentException("암호화된 토큰의 길이가 올바르지 않습니다.");
            }

            // IV(앞 GCM_IV_LENGTH 바이트) 분리
            ByteBuffer bb = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[GCM_IV_LENGTH];
            bb.get(iv);
            byte[] ciphertext = new byte[bb.remaining()];
            bb.get(ciphertext);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);

            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (IllegalStateException | IllegalArgumentException e) {
            // 입력 검증 또는 키 로드 실패 시 그대로 전파
            throw e;
        } catch (javax.crypto.AEADBadTagException e) {
            // GCM 인증 실패 → 변조 가능성
            throw new SecurityException("토큰 인증에 실패했습니다. 데이터가 변조되었을 수 있습니다.", e);
        }
        catch (Exception e) {
            throw new RuntimeException("토큰 복호화에 실패했습니다.", e);
        }
    }
}
