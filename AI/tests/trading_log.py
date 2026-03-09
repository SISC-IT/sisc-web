#AI/tests/trading_log.py
"""
[종합 매매 로그 및 포지션 체크 스크립트]
- DB에서 전체 체결 내역(Executions)과 현재 보유 중인 포트폴리오 데이터를 조회합니다.
- 전체 매매 이력을 출력하여 AI 모델의 과거 거래 행적을 확인하고, 현재 포지션의 수익률을 점검합니다.
- 특정 종목의 주가 흐름과 매매 타점을 시각화하여 AI의 진입/청산 시점의 적절성을 평가합니다.
"""

import sys
import os
import pandas as pd
import matplotlib.pyplot as plt
from matplotlib import rc

# 프로젝트 루트 경로를 sys.path에 추가하여 libs 등 내부 모듈을 참조 가능하게 함
current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../.."))
if project_root not in sys.path:
    sys.path.append(project_root)

from AI.libs.database.connection import get_db_conn

# 한글 폰트 설정 (Windows: Malgun Gothic, Mac: AppleGothic 등 환경에 맞춰 변경 가능)
rc('font', family='Malgun Gothic')
plt.rcParams['axes.unicode_minus'] = False # 마이너스 기호 깨짐 방지

def plot_ticker_trades(ticker: str, conn):
    """
    특정 종목의 주가 차트 위에 매수/매도 타점을 시각화합니다.
    """
    try:
        # 1. 해당 종목의 최근 주가 데이터 조회 (시각화 범위: 2025-08-01 이후)
        price_query = f"""
            SELECT date, close 
            FROM public.price_data 
            WHERE ticker = '{ticker}' 
              AND date >= '2025-08-01'
            ORDER BY date ASC;
        """
        df_price = pd.read_sql(price_query, conn)
        
        # 2. 해당 종목의 모든 체결 내역 조회
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

        # 차트 생성
        plt.figure(figsize=(12, 6))
        plt.plot(df_price['date'], df_price['close'], label=f'{ticker} 종가', color='black', linewidth=1.2, alpha=0.5)

        # 매매 타점 마킹 (매수: 빨간 삼각형, 매도: 파란 역삼각형)
        if not df_trades.empty:
            df_trades['fill_date'] = pd.to_datetime(df_trades['fill_date'])
            buys = df_trades[df_trades['side'] == 'BUY']
            sells = df_trades[df_trades['side'] == 'SELL']
            
            if not buys.empty:
                plt.scatter(buys['fill_date'], buys['fill_price'], marker='^', color='red', s=100, label='매수 (BUY)', zorder=5)
            if not sells.empty:
                plt.scatter(sells['fill_date'], sells['fill_price'], marker='v', color='blue', s=100, label='매도 (SELL)', zorder=5)

        plt.title(f"AI Trading Analysis: {ticker}", fontsize=14, fontweight='bold')
        plt.xlabel("날짜")
        plt.ylabel("가격 (USD)")
        plt.legend()
        plt.grid(True, linestyle=':', alpha=0.6)
        plt.tight_layout()
        plt.show(block=False) 

    except Exception as e:
        print(f"[{ticker}] 차트 생성 중 오류 발생: {e}")

def show_full_trading_report():
    """
    전체 매매 로그와 현재 포지션을 종합하여 리포트 형태로 출력합니다.
    """
    print("\n" + "="*80)
    print("📊 [AI 퀀트 시스템 통합 매매 리포트]")
    print("="*80)
    
    conn = get_db_conn("db")
    if not conn:
        print("❌ 데이터베이스 연결 실패")
        return

    try:
        # --- [PART 1: 전체 매매 로그 조회] ---
        print("\n📜 [1. 전체 체결 내역 (최근 20건)]")
        print("-" * 80)
        execution_query = """
            SELECT fill_date, ticker, side, qty, fill_price, 
                   (qty * fill_price) as trade_amount
            FROM public.executions
            ORDER BY fill_date DESC
            LIMIT 20;
        """
        df_execs = pd.read_sql(execution_query, conn)
        
        if df_execs.empty:
            print("  데이터가 없습니다.")
        else:
            # 출력 가독성을 위해 날짜 형식 변환
            for _, row in df_execs.iterrows():
                side_str = "🔴 매수" if row['side'] == 'BUY' else "🔵 매도"
                print(f"{row['fill_date']} | {row['ticker']:<5} | {side_str} | "
                      f"수량: {int(row['qty']):>4} | 가격: ${row['fill_price']:>8.2f} | "
                      f"총액: ${row['trade_amount']:>10.2f}")

        # --- [PART 2: 현재 보유 포지션 상세] ---
        print("\n💰 [2. 현재 보유 포지션 및 수익률]")
        print("-" * 80)
        position_query = """
            SELECT ticker, position_qty, avg_price, current_price, market_value, pnl_unrealized, date
            FROM public.portfolio_positions
            WHERE date = (SELECT MAX(date) FROM public.portfolio_positions)
              AND position_qty > 0
            ORDER BY market_value DESC;
        """
        df_pos = pd.read_sql(position_query, conn)

        if df_pos.empty:
            print("  현재 보유 중인 종목이 없습니다.")
        else:
            total_mkt_val = 0.0
            total_pnl = 0.0
            tickers_to_plot = []

            for _, row in df_pos.iterrows():
                ticker = row['ticker']
                tickers_to_plot.append(ticker)
                
                mkt_val = float(row['market_value'])
                pnl = float(row['pnl_unrealized'])
                total_mkt_val += mkt_val
                total_pnl += pnl
                
                # 수익률 계산
                roi = (row['current_price'] / row['avg_price'] - 1) * 100 if row['avg_price'] > 0 else 0
                
                print(f"📌 {ticker:<5} | {int(row['position_qty']):>4}주 보유 | "
                      f"평단: ${row['avg_price']:>8.2f} | 현재: ${row['current_price']:>8.2f} | "
                      f"손익: ${pnl:>9.2f} ({roi:>+6.2f}%)")
            
            print("-" * 80)
            print(f"💵 총 평가액: ${total_mkt_val:,.2f}  |  📈 총 미실현 손익: ${total_pnl:,.2f}")

            # --- [PART 3: 시각화 분석] ---
            print(f"\n📈 [3. 보유 종목 매매 타점 분석 차트 생성 중...]")
            for ticker in tickers_to_plot:
                plot_ticker_trades(ticker, conn)
            
            print("\n💡 모든 차트가 생성되었습니다. 분석을 위해 차트 창을 확인해 주세요.")
            plt.show()

    except Exception as e:
        print(f"❌ 리포트 생성 중 오류 발생: {e}")
    finally:
        conn.close()

if __name__ == "__main__":
    show_full_trading_report()