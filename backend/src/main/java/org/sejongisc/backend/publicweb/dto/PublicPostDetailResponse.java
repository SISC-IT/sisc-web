package org.sejongisc.backend.publicweb.dto;

import java.util.List;
import java.util.UUID;

public record PublicPostDetailResponse(
    UUID id,
    String title,
    String authorName,
    String contentHtml,
    String contentText,
    String thumbnailUrl,
    List<PublicPostFileResponse> pdfAttachments,
    List<PublicPostFileResponse> fileAttachments
) {
}
