package org.sejongisc.backend.board.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter @Setter
public class PostRequest {
    private UUID boardId;
    private String title;
    private String content;
    private List<AttachmentDto> attachments;

    @Getter @Setter
    public static class AttachmentDto {
        private String filename;
        private String url;
        private String mimeType;
    }
}
