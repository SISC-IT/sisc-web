package org.sejongisc.backend.publicweb.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import org.sejongisc.backend.board.entity.PostContentFormat;

public record PublicPageRequest(
    @NotBlank(message = "제목은 필수 항목입니다.")
    String title,
    PostContentFormat contentFormat,
    String content,
    JsonNode contentJson,
    String contentHtml,
    String contentText,
    LocalDateTime publishedAt
) {
}
