package org.sejongisc.backend.activity.event;

import org.sejongisc.backend.activity.entity.ActivityType;

import java.util.UUID;

public record ActivityEvent(
    UUID userId,
    String username,
    ActivityType activityType,
    String message,
    UUID targetId,
    String boardName
) {}