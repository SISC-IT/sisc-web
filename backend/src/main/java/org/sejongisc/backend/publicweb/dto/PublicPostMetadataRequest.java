package org.sejongisc.backend.publicweb.dto;

import java.time.LocalDateTime;

public record PublicPostMetadataRequest(
    Boolean publicVisible,
    LocalDateTime publicPublishedAt
) {
}
