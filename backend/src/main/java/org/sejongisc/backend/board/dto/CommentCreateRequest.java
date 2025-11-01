package org.sejongisc.backend.board.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
public class CommentCreateRequest {
    private UUID postId;
    private String content;
    private UUID parentId; // 대댓글이면 존재, 아니면 null
}
