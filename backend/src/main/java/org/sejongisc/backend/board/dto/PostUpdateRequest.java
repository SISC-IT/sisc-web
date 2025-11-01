package org.sejongisc.backend.board.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import org.sejongisc.backend.board.domain.PostType;

import java.util.List;
import java.util.UUID;

@Getter
@NoArgsConstructor
public class PostUpdateRequest {
    private String title;
    private String content;
    private PostType postType;
    private List<PostAttachmentDto> attachments;
}
