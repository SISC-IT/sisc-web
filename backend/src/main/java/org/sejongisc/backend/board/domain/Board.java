package org.sejongisc.backend.board.domain;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Board {

    @Id
    @GeneratedValue
    private UUID id;

    private UUID teamId;

    private UUID createdBy;

    private String name;

    private boolean isPrivate;

    private LocalDateTime createdAt;
}
