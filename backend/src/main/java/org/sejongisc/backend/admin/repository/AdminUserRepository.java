package org.sejongisc.backend.admin.repository;

import org.sejongisc.backend.admin.dto.AdminUserResponse;
import org.sejongisc.backend.point.entity.AccountType;
import org.sejongisc.backend.user.entity.Role;
import org.sejongisc.backend.user.entity.User;
import org.sejongisc.backend.user.entity.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * 관리자 화면용 User 조회 전용 레포지토리
 * - User 도메인의 기본 CRUD는 user 패키지의 UserRepository에서 담당
 * - 해당 클래스는 Admin 페이지에서만 사용되는 조회 쿼리와 Admin DTO로의 프로젝션 로직을 정의
 */
public interface AdminUserRepository extends JpaRepository<User, UUID> {

    @Query("""
        SELECT new org.sejongisc.backend.admin.dto.AdminUserResponse(
            u.userId,
            u.studentId,
            u.name,
            u.email,
            u.phoneNumber,
            COALESCE(a.balance, 0),
            u.grade,
            u.role,
            u.status,
            u.generation,
            u.college,
            u.department,
            u.teamName,
            u.positionName
        )
        FROM User u
        LEFT JOIN Account a
            ON u.userId = a.ownerId
            AND a.type = :accountType
        WHERE
            (:keyword IS NULL OR
                u.name LIKE %:keyword% OR
                u.studentId LIKE %:keyword% OR
                u.email LIKE %:keyword%)
            AND (:generation IS NULL OR u.generation = :generation)
            AND (:role IS NULL OR u.role = :role)
            AND (:status IS NULL OR u.status = :status)
        ORDER BY u.generation DESC, u.name ASC
        """)
    List<AdminUserResponse> findAllByAdminFilter(
        @Param("keyword") String keyword,
        @Param("generation") Integer generation,
        @Param("role") Role role,
        @Param("status") UserStatus status,
        @Param("accountType") AccountType accountType
    );
}