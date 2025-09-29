package org.sejongisc.backend.board.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class PostResponse {
    private UUID postId;
    private String title;
    private String content;
    private String postType;
    private String authorName;
    private List<AttachmentResponse> attachments;
    private LocalDateTime createdAt;

    @Getter
    @AllArgsConstructor
    public static class AttachmentResponse {
        private UUID fileId;
        private String fileName;
        private String fileUrl;
    }
}
