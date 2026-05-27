package org.sejongisc.backend.publicweb.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import java.util.UUID;
import org.sejongisc.backend.board.entity.PostContentFormat;
import org.sejongisc.backend.publicweb.entity.PublicPageType;

public record PublicPageResponse(
    UUID id,
    PublicPageType pageType,
    String title,
    PostContentFormat contentFormat,
    JsonNode contentJson,
    String contentHtml,
    String contentText,
    LocalDateTime publishedAt
) {
}
