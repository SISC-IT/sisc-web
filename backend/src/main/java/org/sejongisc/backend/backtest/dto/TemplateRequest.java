package org.sejongisc.backend.backtest.dto;


import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record TemplateRequest(
        @Schema(hidden = true, description = "템플릿 ID")
        UUID templateId,

        @Schema(description = "템플릿 제목", defaultValue = "기술주 템플릿")
        String title,

        @Schema(description = "템플릿 설명", defaultValue = "기술주 템플릿입니다.")
        String description,

        @Schema(description = "템플릿 공개 여부", defaultValue = "false")
        Boolean isPublic
) {}
