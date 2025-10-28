package org.sejongisc.backend.auth.entity;

import jakarta.persistence. *;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "refresh_token")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RefreshToken {

    @Id
    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userid;

    @Column(nullable = false, length = 500)
    private String token;
}
