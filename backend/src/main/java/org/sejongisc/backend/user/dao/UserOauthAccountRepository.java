package org.sejongisc.backend.user.dao;

import org.sejongisc.backend.user.entity.AuthProvider;
import org.sejongisc.backend.user.entity.UserOauthAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserOauthAccountRepository extends JpaRepository<UserOauthAccount, UUID> {
    Optional<UserOauthAccount> findByProviderAndProviderUid(AuthProvider provider, String providerUid);
}
