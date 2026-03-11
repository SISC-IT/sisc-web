package org.sejongisc.backend.attendance.dto.sessionUser;

import java.util.UUID;

public record RoundHeaderResponse(
    UUID roundId,
    int roundNumber // 1, 2, 3...
) {}