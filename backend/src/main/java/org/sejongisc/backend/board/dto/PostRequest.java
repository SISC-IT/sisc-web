package org.sejongisc.backend.board.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter @Setter
public class PostRequest {
    private String title;
    private String content;
    private String postType; // notice / normal
    private List<AttachmentDto> attachments;

    @Getter @Setter
    public static class AttachmentDto {
        private String fileName;
        private String fileUrl;
    }
}
