package org.sejongisc.backend.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import org.sejongisc.backend.common.entity.postgres.BasePostgresEntity;
import org.sejongisc.backend.user.entity.User;

import java.util.UUID;

@Entity
@Table(name = "user_oauth_account",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"provider", "provider_uid"})
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserOauthAccount extends BasePostgresEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "user_oauth_account_id", columnDefinition = "uuid")
    private UUID userOauthAccountId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, columnDefinition = "uuid")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuthProvider provider;

    @Column(name = "provider_uid", nullable = false)
    private String providerUid;

    @Column(length = 512)
    private String accessToken;
}
