package org.sejongisc.backend.board.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class PostSummaryResponse {
    private UUID id;
    private String title;
    private int likeCount;
    private int commentCount;
    private LocalDateTime createdAt;
}
