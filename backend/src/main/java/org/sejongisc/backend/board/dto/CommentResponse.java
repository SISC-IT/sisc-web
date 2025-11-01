package org.sejongisc.backend.board.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class CommentResponse {
    private UUID id;
    private UUID postId;
    private String content;
    private UUID parentId;
    private LocalDateTime createdAt;
}
