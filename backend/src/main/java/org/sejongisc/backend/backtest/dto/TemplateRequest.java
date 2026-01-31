package org.sejongisc.backend.backtest.dto;


import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record TemplateRequest(
        /**
         * 추후 create와 update request 달라지면 분리 필요
         */

        @Schema(description = "템플릿 제목", defaultValue = "기술주 템플릿")
        String title,

        @Schema(description = "템플릿 설명", defaultValue = "기술주 템플릿입니다.")
        String description,

        @Schema(description = "템플릿 공개 여부", defaultValue = "false")
        Boolean isPublic
) {}
