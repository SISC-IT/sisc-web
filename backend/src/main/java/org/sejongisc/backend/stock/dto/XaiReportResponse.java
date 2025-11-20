package org.sejongisc.backend.stock.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record XaiReportResponse(
    String ticker,
    String signal,
    BigDecimal price,
    LocalDate date,
    String report
) {}

