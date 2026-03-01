# AI/tests/check_positions.py
"""
[포지션 체크 스크립트]
- DB에서 가장 최근 날짜의 포트폴리오 포지션 데이터를 조회하여 현재 보유 중인 종목과 수량, 평가액, 손익 등을 출력합니다.
- 주로 백필(Backfill) 작업 후에 정상적으로 포지션이 기록되고 있는지 확인하는 용도로 사용됩니다.
"""

import sys
import os
import pandas as pd
import matplotlib.pyplot as plt
from matplotlib import rc

# 프로젝트 루트 경로 추가
current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../.."))
if project_root not in sys.path:
    sys.path.append(project_root)

from AI.libs.database.connection import get_db_conn

# 한글 폰트 깨짐 방지
rc('font', family='Malgun Gothic')
plt.rcParams['axes.unicode_minus'] = False

def plot_ticker_trades(ticker: str, conn):
    """특정 종목의 주가 차트와 매매 타점을 그립니다."""
    try:
        # 주가 데이터 로드
        price_query = f"""
            SELECT date, close 
            FROM public.price_data 
            WHERE ticker = '{ticker}' 
              AND date >= '2025-08-01'
            ORDER BY date ASC;
        """
        df_price = pd.read_sql(price_query, conn)
        
        # 체결 내역 로드
        trade_query = f"""
            SELECT fill_date, fill_price, side, qty 
            FROM public.executions
            WHERE ticker = '{ticker}'
            ORDER BY fill_date ASC;
        """
        df_trades = pd.read_sql(trade_query, conn)

        if df_price.empty:
            return

        df_price['date'] = pd.to_datetime(df_price['date'])

        plt.figure(figsize=(12, 6))
        plt.plot(df_price['date'], df_price['close'], label=f'{ticker} 종가', color='black', linewidth=1.5, alpha=0.7)

        # 타점 찍기
        if not df_trades.empty:
            df_trades['fill_date'] = pd.to_datetime(df_trades['fill_date'])
            buys = df_trades[df_trades['side'] == 'BUY']
            sells = df_trades[df_trades['side'] == 'SELL']
            
            if not buys.empty:
                plt.scatter(buys['fill_date'], buys['fill_price'], marker='^', color='red', s=150, label='매수 (BUY)', zorder=5)
            if not sells.empty:
                plt.scatter(sells['fill_date'], sells['fill_price'], marker='v', color='blue', s=150, label='매도 (SELL)', zorder=5)

        plt.title(f" AI 매매 타점: {ticker}", fontsize=16, fontweight='bold')
        plt.xlabel("날짜")
        plt.ylabel("가격 (USD)")
        plt.legend()
        plt.grid(True, linestyle='--', alpha=0.5)
        plt.tight_layout()
        plt.show(block=False) # 차트를 띄워두고 다음 코드로 진행

    except Exception as e:
        print(f"[{ticker}] 차트 생성 중 오류: {e}")


def show_current_holdings_and_charts():
    print("🔍 현재 보유 중인 포트폴리오 조회 및 차트 생성을 시작합니다...\n")
    
    conn = get_db_conn("db")
    if not conn:
        print("❌ DB 연결에 실패했습니다.")
        return

    # 가장 최근 날짜의 보유 종목 조회
    query = """
    SELECT date, ticker, position_qty, avg_price, current_price, market_value, pnl_unrealized
    FROM public.portfolio_positions
    WHERE date = (SELECT MAX(date) FROM public.portfolio_positions)
      AND position_qty > 0
    ORDER BY market_value DESC;
    """

    try:
        df = pd.read_sql(query, conn)
        
        if df.empty:
            print("📭 현재 보유 중인 주식이 없습니다.")
        else:
            latest_date = df['date'].iloc[0]
            print(f"📅 [최종 업데이트 기준일: {latest_date}]")
            print("=" * 65)
            
            total_market_value = 0.0
            total_pnl = 0.0
            held_tickers = []
            
            # 1. 텍스트로 보유 현황 출력
            for index, row in df.iterrows():
                ticker = row['ticker']
                held_tickers.append(ticker)
                
                qty = int(row['position_qty'])
                avg_p = float(row['avg_price'])
                cur_p = float(row['current_price'])
                mkt_val = float(row['market_value'])
                pnl = float(row['pnl_unrealized'])
                
                total_market_value += mkt_val
                total_pnl += pnl
                return_pct = (cur_p / avg_p - 1) * 100 if avg_p > 0 else 0
                
                print(f"📌 {ticker:<5s} | 수량: {qty:,}주")
                print(f"   - 평단가: ${avg_p:,.2f}  ->  현재가: ${cur_p:,.2f}")
                print(f"   - 평가액: ${mkt_val:,.2f}  |  손익: ${pnl:,.2f} ({return_pct:+.2f}%)")
                print("-" * 65)
            
            print(f"💰 총 주식 평가액 : ${total_market_value:,.2f}")
            print(f"📈 총 미실현 손익 : ${total_pnl:,.2f}")
            print("=" * 65)
            
            # 2. 보유 중인 종목들의 타점 차트 팝업
            print("\n📊 보유 종목의 매매 타점 차트를 띄웁니다. (창을 닫으면 프로그램이 종료됩니다.)")
            for ticker in held_tickers:
                plot_ticker_trades(ticker, conn)
            
            # 차트 창이 모두 닫힐 때까지 대기
            plt.show()

    except Exception as e:
        print(f"❌ 데이터 조회 중 오류 발생: {e}")
    finally:
        conn.close()

if __name__ == "__main__":
    show_current_holdings_and_charts()