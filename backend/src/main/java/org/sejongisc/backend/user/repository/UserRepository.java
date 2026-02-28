package org.sejongisc.backend.user.repository;


import org.sejongisc.backend.user.entity.User;
import org.sejongisc.backend.user.entity.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    boolean existsByEmail(String email);
    boolean existsByPhoneNumber(String phoneNumber);
    boolean existsByEmailOrStudentId(String email, String studentId);
    boolean existsByEmailAndStudentId(String email, String studentId);
    boolean existsByStudentId(String studentId);

    Optional<User> findUserByEmail(String email);
    Optional<User> findByEmailAndStudentId(String email, String studentId);

    @Query(
        "SELECT u FROM User u " +
        "LEFT JOIN Account a ON u.userId = a.ownerId " +
        "WHERE a.accountId IS NULL"
    )
    List<User> findAllUsersMissingAccount();

    Optional<User> findByStudentId(String studentId);

    List<User> findAllByStatus(UserStatus status);
}
