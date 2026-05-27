package org.sejongisc.backend.publicweb.dto;

import java.util.UUID;

public record PublicPostFileResponse(
    UUID id,
    String filename,
    String url,
    String contentType,
    Long fileSize
) {
}
