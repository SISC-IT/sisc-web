package org.sejongisc.backend.user.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;
import org.sejongisc.backend.common.entity.postgres.BasePostgresEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name="users") // user가 예약어라 users로 테이블명 지정
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends BasePostgresEntity{

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "user_id", columnDefinition = "uuid")
    private UUID userId;

    //OAuth 전용 계정 대비 nullable 허용 가능
    @Column(columnDefinition = "citext", unique = true)
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(nullable = false)
    private String name;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(columnDefinition = "integer default 0")
    private Integer point;

    // User : OAuthAccounts = 1 : N(여러 OAuth를 연결 가능)
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<UserOauthAccount> oauthAccounts = new ArrayList<>();
}
