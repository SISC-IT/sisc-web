package org.sejongisc.backend.board.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class CommentResponse {
    private UUID commentId;
    private String content;
    private String authorName;
    private LocalDateTime createdAt;
}
