package org.sejongisc.backend.stock.repository;

import java.util.List;
import org.sejongisc.backend.stock.dto.HoldingDto;
import org.sejongisc.backend.stock.dto.TradeLogDto;
import org.sejongisc.backend.stock.entity.Execution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ExecutionRepository extends JpaRepository<Execution, Long> {
  // 전체 매매로그 (최신 체결 순)
  List<TradeLogDto> findAllByOrderByFillDateDesc();

  // 전체 종목별 보유 주식
  @Query("""
        SELECT new org.sejongisc.backend.stock.dto.HoldingDto(
            e.ticker, 
            e.positionQty, 
            e.pnlUnrealized, 
            e.cashAfter
        )
        FROM Execution e
        WHERE e.id IN (
            SELECT MAX(e2.id) 
            FROM Execution e2 
            GROUP BY e2.ticker
        )
        AND e.positionQty > 0
        ORDER BY e.fillDate DESC
    """)
  List<HoldingDto> findCurrentHoldings();


}
