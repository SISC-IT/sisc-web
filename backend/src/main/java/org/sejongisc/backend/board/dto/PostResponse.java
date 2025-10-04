package org.sejongisc.backend.board.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class PostResponse {
    private UUID id;
    private String title;
    private String content;
    private String authorName;
    private List<PostAttachmentDto> attachments;
    private LocalDateTime createdAt;
}
