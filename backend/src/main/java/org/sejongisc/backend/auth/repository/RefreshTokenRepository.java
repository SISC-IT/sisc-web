package org.sejongisc.backend.auth.repository;

import org.sejongisc.backend.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByUserId(UUID userId);
    Optional<RefreshToken> findByToken(String token);

    void deleteByUserId(UUID userId);
}
