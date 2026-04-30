package org.sejongisc.backend.stock.repository;

import java.util.List;
import java.util.Optional;

import io.lettuce.core.dynamic.annotation.Param;
import org.sejongisc.backend.stock.dto.HoldingDto;
import org.sejongisc.backend.stock.dto.TradeLogDto;
import org.sejongisc.backend.stock.entity.Execution;
import org.sejongisc.backend.stock.repository.projection.PortfolioOverviewProjection;
import org.sejongisc.backend.stock.repository.projection.PortfolioSimpleProjection;
import org.sejongisc.backend.stock.repository.projection.PositionProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ExecutionRepository extends JpaRepository<Execution, Long> {
  // 전체 매매로그 (최신 체결 순)
  @Query("""
      SELECT new org.sejongisc.backend.stock.dto.TradeLogDto(
          e.id,
          xr.id,
          e.ticker,
          e.ticker,
          e.fillDate,
          e.fillPrice,
          e.qty,
          e.side,
          e.value,
          e.positionQty,
          e.avgPrice,
          e.pnlRealized
      )
      FROM Execution e
      LEFT JOIN e.xaiReport xr
      ORDER BY e.fillDate DESC, e.id DESC
      """)
  List<TradeLogDto> findAllByOrderByFillDateDesc();
/*
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
*/

  @Query("""
        select e
        from Execution e
        join fetch e.xaiReport xr
        where e.id = :id
    """)
  Optional<Execution> findWithXaiReportById(@Param("id") Long id);

  @Query(value = """
    SELECT 
        total_asset AS totalAsset,
        date AS date
    FROM portfolio_summary
    ORDER BY date ASC
""", nativeQuery = true)
  List<PortfolioSimpleProjection> findSimpleSummary();


  @Query(value = """
        SELECT 
            ticker AS ticker,
            position_qty AS positionQty,
            avg_price AS avgPrice,
            current_price AS currentPrice,
            market_value AS marketPrice 
        FROM portfolio_positions 
            WHERE date = (SELECT MAX(date) FROM portfolio_positions) AND position_qty > 0 
        ORDER BY ticker
    """, nativeQuery = true)
  List<PositionProjection> findAllPositions();

  @Query(value = """
        SELECT
            (SELECT date FROM portfolio_summary ORDER BY date ASC LIMIT 1)            AS startDate,
            (SELECT date FROM portfolio_summary ORDER BY date DESC LIMIT 1)           AS endDate,
            (SELECT total_asset FROM portfolio_summary ORDER BY date DESC LIMIT 1)    AS lastTotalAsset,
            (SELECT initial_capital FROM portfolio_summary ORDER BY date ASC LIMIT 1) AS initialCapital
        """,
          nativeQuery = true)
  PortfolioOverviewProjection getPortfolioOverview();
}
