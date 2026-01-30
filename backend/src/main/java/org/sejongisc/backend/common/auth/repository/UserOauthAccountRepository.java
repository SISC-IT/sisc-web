package org.sejongisc.backend.common.auth.repository;

import org.sejongisc.backend.common.auth.entity.AuthProvider;
import org.sejongisc.backend.common.auth.entity.UserOauthAccount;
import org.sejongisc.backend.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserOauthAccountRepository extends JpaRepository<UserOauthAccount, UUID> {
    Optional<UserOauthAccount> findByProviderAndProviderUid(AuthProvider provider, String providerUid);
    boolean existsByProviderAndUser(AuthProvider provider, User user);
}
