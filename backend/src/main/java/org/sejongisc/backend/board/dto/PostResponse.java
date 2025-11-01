package org.sejongisc.backend.board.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.sejongisc.backend.board.domain.PostType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class PostResponse {
    private UUID id;
    private UUID boardId;
    private String title;
    private String content;
    private PostType postType;
    private int likeCount;
    private int commentCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<PostAttachmentDto> attachments;
}
