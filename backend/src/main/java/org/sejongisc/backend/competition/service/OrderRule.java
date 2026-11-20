package org.sejongisc.backend.competition.service;

import java.time.LocalDateTime;
import java.util.Set;
import org.sejongisc.backend.competition.dto.UnavailableReason;
import org.sejongisc.backend.competition.entity.Competition;
import org.sejongisc.backend.competition.entity.CompetitionStatus;
import org.sejongisc.backend.competition.entity.CompetitionStock;
import org.sejongisc.backend.competition.entity.DailyClosePrice;
import org.sejongisc.backend.competition.entity.Market;

// 주문 가능 여부 규칙. 주문 API와 종목 조회 API의 tradable이 같은 규칙을 쓰도록 한곳에 둔다.
final class OrderRule {

    // 미장을 열 때 US를 추가한다. 주문 금액 계산에 환율 반영도 함께 필요하다.
    private static final Set<Market> SUPPORTED_MARKETS = Set.of(Market.KR);

    private OrderRule() {
    }

    // 대회 단위 조건. 종목을 조회하기 전에 먼저 확인할 수 있게 따로 둔다.
    static UnavailableReason competitionUnavailableReason(Competition competition) {
        return competition.getStatus() == CompetitionStatus.ACTIVE ? null : UnavailableReason.COMPETITION_NOT_ACTIVE;
    }

    // 주문할 수 있으면 null을 반환한다. latest는 지금 공개된 최신 종가이고 없으면 null이다.
    static UnavailableReason unavailableReason(
            Competition competition, CompetitionStock stock, DailyClosePrice latest, LocalDateTime now) {
        UnavailableReason competitionReason = competitionUnavailableReason(competition);
        if (competitionReason != null) {
            return competitionReason;
        }
        if (!SUPPORTED_MARKETS.contains(stock.getMarket())) {
            return UnavailableReason.MARKET_NOT_SUPPORTED;
        }
        if (!stock.getMarket().isOrderOpen(now.toLocalTime())) {
            return UnavailableReason.ORDER_CLOSED;
        }
        // 종가는 거래일에만 수집되므로, 최신 종가가 오늘 날짜가 아니면 주말·공휴일이다.
        if (latest == null || !latest.getPriceDate().equals(now.toLocalDate())) {
            return UnavailableReason.PRICE_UNAVAILABLE;
        }
        return null;
    }
}
