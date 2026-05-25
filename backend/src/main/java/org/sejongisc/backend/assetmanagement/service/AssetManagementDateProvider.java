package org.sejongisc.backend.assetmanagement.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AssetManagementDateProvider {
  private static final DateTimeFormatter QUERY_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

  private final ZoneId zoneId;

  public AssetManagementDateProvider(@Value("${asset-management.time-zone:Asia/Seoul}") String zoneId) {
    this.zoneId = ZoneId.of(zoneId);
  }

  public String currentQueryDate() {
    return LocalDate.now(zoneId).format(QUERY_DATE_FORMATTER);
  }
}
