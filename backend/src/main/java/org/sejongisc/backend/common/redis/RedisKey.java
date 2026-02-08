package org.sejongisc.backend.common.redis;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.Duration;

@Getter
@RequiredArgsConstructor
public enum RedisKey {
    EMAIL_VERIFY("EMAIL_VERIFY:", Duration.ofMinutes(3)),      // 이메일 인증 코드
    EMAIL_VERIFIED("EMAIL_VERIFIED:", Duration.ofDays(1)),     // 이메일 인증 완료 상태
    PASSWORD_RESET("PASSWORD_RESET:", Duration.ofMinutes(10)), // 비밀번호 재설정 토큰
    PASSWORD_RESET_EMAIL("PASSWORD_RESET_EMAIL:", Duration.ofMinutes(3)); // 비밀번호 재설정 인증 코드

    private final String prefix;
    private final Duration ttl;

    // 키 생성 메서드
    public String getKey(String identifier) {
        return this.prefix + identifier;
    }
}