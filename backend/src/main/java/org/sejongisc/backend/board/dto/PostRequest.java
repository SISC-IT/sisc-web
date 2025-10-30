package org.sejongisc.backend.board.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class PostRequest {
    private UUID boardId;
    private String title;
    private String content;
    private String postType; // notice / normal
    private List<PostAttachmentDto> attachments;
}
