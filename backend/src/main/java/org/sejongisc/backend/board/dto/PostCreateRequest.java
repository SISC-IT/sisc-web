package org.sejongisc.backend.board.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.sejongisc.backend.board.domain.PostType;

import java.util.List;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PostCreateRequest {
    private UUID boardId;
    private String title;
    private String content;
    private PostType postType;                // NOTICE | NORMAL
    private List<PostAttachmentDto> attachments; // 옵션: 없으면 null/빈 배열
}
