package org.sejongisc.backend.board.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Board {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "board_id", columnDefinition = "BINARY(16)")
    private UUID id;

    private UUID teamId;

    private UUID createdBy;

    private String name;

    private boolean isPrivate;

    private LocalDateTime createdAt;
}
