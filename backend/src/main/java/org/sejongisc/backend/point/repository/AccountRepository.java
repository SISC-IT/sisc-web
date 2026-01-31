package org.sejongisc.backend.point.repository;

import org.sejongisc.backend.point.entity.Account;
import org.sejongisc.backend.point.entity.AccountName;
import org.sejongisc.backend.point.entity.AccountType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

  Optional<Account> findByAccountName(AccountName accountName);

  Optional<Account> findByOwnerIdAndType(UUID ownerId, AccountType accountType);

  boolean existsByAccountName(AccountName accountName);
}
