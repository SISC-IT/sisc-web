package org.sejongisc.backend.competition.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CompetitionCreateRequest(
    @NotBlank @Size(max = 100) String name,
    @NotNull LocalDate startDate,
    @NotNull LocalDate endDate
) {
}
