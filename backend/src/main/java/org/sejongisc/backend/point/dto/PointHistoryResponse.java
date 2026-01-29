package org.sejongisc.backend.point.dto;

import org.springframework.data.domain.Page;

public record PointHistoryResponse(
  Page<PointHistoryItem> history
) {}