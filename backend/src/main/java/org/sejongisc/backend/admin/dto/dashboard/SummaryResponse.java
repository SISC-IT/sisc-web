package org.sejongisc.backend.admin.dto.dashboard;

public record SummaryResponse(
    long count,
    double percentageComparedToLastWeek
) {}