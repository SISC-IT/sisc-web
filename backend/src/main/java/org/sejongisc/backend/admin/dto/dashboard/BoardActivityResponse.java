package org.sejongisc.backend.admin.dto.dashboard;

public record BoardActivityResponse(
            String boardName,
            long activityCount
) {}