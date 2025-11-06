package org.sejongisc.backend.board.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
public class CommentCreateRequest {

    // ⚠️ postId 제거 (URL 의 path 값이 우선)
    private String content;
    private UUID parentId; // 대댓글이면 존재, 아니면 null
}
