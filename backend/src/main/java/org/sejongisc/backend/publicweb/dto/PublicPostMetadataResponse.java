package org.sejongisc.backend.publicweb.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record PublicPostMetadataResponse(
    UUID id,
    boolean publicVisible,
    LocalDateTime publicPublishedAt,
    String thumbnailUrl
) {
}
