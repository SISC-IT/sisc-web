package org.sejongisc.backend.auth.dao;

import org.sejongisc.backend.auth.entity.AuthProvider;
import org.sejongisc.backend.auth.entity.UserOauthAccount;
import org.sejongisc.backend.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserOauthAccountRepository extends JpaRepository<UserOauthAccount, UUID> {
    Optional<UserOauthAccount> findByProviderAndProviderUid(AuthProvider provider, String providerUid);
    boolean existsByProviderAndUser(AuthProvider provider, User user);
}
