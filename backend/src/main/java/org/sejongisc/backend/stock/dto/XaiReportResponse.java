package org.sejongisc.backend.stock.dto;

import org.sejongisc.backend.stock.entity.XaiReport;

import java.math.BigDecimal;
import java.time.LocalDate;

public record XaiReportResponse(
    String ticker,
    String signal,
    BigDecimal price,
    LocalDate date,
    String report
) {
    public static XaiReportResponse from(XaiReport r) {
        return new XaiReportResponse(
                r.getTicker(),
                r.getSignal(),
                r.getPrice(),
                r.getDate(),
                r.getReport()
        );
    }

}

