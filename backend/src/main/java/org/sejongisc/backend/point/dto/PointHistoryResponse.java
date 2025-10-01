package org.sejongisc.backend.point.dto;

import lombok.Builder;
import lombok.Getter;
import org.sejongisc.backend.point.entity.PointHistory;
import org.sejongisc.backend.user.entity.User;
import org.springframework.data.domain.Page;

import java.util.Map;

@Getter
@Builder
public class PointHistoryResponse {
  private Page<PointHistory> pointHistoryPage;
  private Map<User, Integer> leaderboard;
}
