package org.sejongisc.backend.attendance.dto;

import java.util.UUID;

public record AttendanceRoundQrTokenResponse(
    UUID roundId,
    String qrToken,
    long expiresAtEpochSec
) {}
