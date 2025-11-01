package org.sejongisc.backend.board.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PostAttachmentDto {
    private String filename;
    private String mimeType;
    private String url;
}
