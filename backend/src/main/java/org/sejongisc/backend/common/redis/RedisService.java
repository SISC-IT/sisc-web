package org.sejongisc.backend.common.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;


@Service
@RequiredArgsConstructor
public class RedisService {

    private final RedisTemplate<String, Object> redisTemplate;

    // 1. 값 저장 (RedisKey Enum 사용)
    public void set(RedisKey keyType, String identifier, Object value) {
        redisTemplate.opsForValue().set(keyType.getKey(identifier), value, keyType.getTtl());
    }

    // 2. 값 저장 (커스텀 TTL)
    public void set(String key, Object value, Duration ttl) {
        redisTemplate.opsForValue().set(key, value, ttl);
    }

    // 3. 값 저장 (만료시간 없음)
    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    // 4. 값 조회
    public <T> T get(RedisKey keyType, String identifier, Class<T> clazz) {
        Object value = redisTemplate.opsForValue().get(keyType.getKey(identifier));
        return clazz.cast(value);
    }

    // 5. 값 조회 (Raw Key)
    public Object get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    // 6. 키 존재 여부 확인
    public boolean hasKey(RedisKey keyType, String identifier) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(keyType.getKey(identifier)));
    }

    // 7. 삭제
    public void delete(RedisKey keyType, String identifier) {
        redisTemplate.delete(keyType.getKey(identifier));
    }

    // 8. 삭제 (Raw Key)
    public void delete(String key) {
        redisTemplate.delete(key);
    }
}