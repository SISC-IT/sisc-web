package org.sejongisc.backend.competition.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Market {
    KR("KRW"),
    US("USD");

    private final String currency;
}
