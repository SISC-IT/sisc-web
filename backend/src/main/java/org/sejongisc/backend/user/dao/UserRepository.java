package org.sejongisc.backend.user.dao;

import org.sejongisc.backend.user.entity.User;
import org.sejongisc.backend.user.service.projection.UserIdNameProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    boolean existsByEmail(String email);

    boolean existsByPhoneNumber(String phoneNumber);

    Optional<User> findUserByEmail(String email);

    List<User> findAllByOrderByPointDesc();

    Optional<User> findByNameAndPhoneNumber(String name, String phoneNumber);

    @Query("""
        select u.userId as userId,
               u.name as name,
               u.email as email
        from User u
        """)
    List<UserIdNameProjection> findAllUserIdAndName();
}
