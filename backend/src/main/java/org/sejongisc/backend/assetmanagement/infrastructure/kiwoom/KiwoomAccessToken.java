package org.sejongisc.backend.assetmanagement.infrastructure.kiwoom;

import java.time.Instant;

public record KiwoomAccessToken(String value, Instant expiresAt) {
}
