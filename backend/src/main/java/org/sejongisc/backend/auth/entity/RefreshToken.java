package org.sejongisc.backend.auth.entity;

import jakarta.persistence. *;
import lombok.*;
import java.util.UUID;

public class RefreshToken {

    @Id
    @Column(nullable = false, columnDefinition = "uuid")
    private UUID userid;

    @Column(nullable = false, length = 500)
    private String token;
}
