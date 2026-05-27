package org.sejongisc.backend.publicweb.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record PublicPostSummaryResponse(
    UUID id,
    String title,
    String authorName,
    LocalDateTime publicPublishedAt,
    String relativeTime,
    String thumbnailUrl,
    boolean hasPdf,
    int pdfCount
) {
}
