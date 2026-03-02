package org.sejongisc.backend.admin.dto.dashboard;

public record VisitorTrendResponse(
            String date, // "YYYY-MM-DD"
            long visitorCount
) {}