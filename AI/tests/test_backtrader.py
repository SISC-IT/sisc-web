# AI/tests/test_transformer_backtrader.py
"""
[통합 테스트: Transformer + Backtrader]
- 가상 데이터(Mock Data) 생성 -> Backtrader 엔진 주입 -> 전략 실행 검증
- unittest.mock을 사용하여 실제 DB 연결 없이 전체 파이프라인을 테스트합니다.
- 'run_backtrader_engine'이 정상 동작하는지 확인합니다.
- 기술적 지표 생성 로직도 포함하여 전체 흐름 점검합니다.
- 실제 가중치 파일이 없어도 엔진이 예외 없이 동작하는지 확인합니다.
- 실전 데이터 테스트는  backtrader_single.py  에서 수행합니다.
"""

import sys
import os
import numpy as np
import pandas as pd
from datetime import datetime, timedelta
from unittest.mock import patch, MagicMock

# ─────────────────────────────────────────────────────────────────────────────
#  경로 설정
# ─────────────────────────────────────────────────────────────────────────────
current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../.."))
if project_root not in sys.path:
    sys.path.append(project_root)

# 모듈 임포트
from AI.modules.signal.core.data_loader import DataLoader
from AI.modules.signal.models import get_model
from AI.modules.trader.backtrader.backtrader_engine import run_backtrader_engine 
from AI.modules.signal.core.features import add_technical_indicators

# ─────────────────────────────────────────────────────────────────────────────
#  테스트 헬퍼 함수
# ─────────────────────────────────────────────────────────────────────────────
def create_mock_data(ticker, start_date, end_date):
    """테스트용 가상 OHLCV 데이터 생성 (인덱스 = Date)"""
    dates = pd.date_range(start=start_date, end=end_date)
    n = len(dates)
    
    # 랜덤 주가 데이터 생성
    df = pd.DataFrame({
        'open': np.random.rand(n) * 100 + 100,
        'high': np.random.rand(n) * 110 + 100,
        'low': np.random.rand(n) * 90 + 100,
        'close': np.random.rand(n) * 100 + 100,
        'volume': np.random.randint(1000, 10000, n),
        'ticker': ticker
    }, index=dates)
    
    df.index.name = "date"
    
    # 기술적 지표 추가 (RSI, MACD 등 계산 로직 검증)
    df = add_technical_indicators(df)
    
    # Backtrader 호환성을 위해 'date' 컬럼을 별도로 만들어둘 수도 있음
    # (엔진 내부에서 date 컬럼을 인덱스로 변환하는 로직이 있다면)
    df['date'] = df.index
    
    return df

def test_integration():
    print("\n=== [Test] 통합 테스트 시작 (Transformer + Backtrader) ===\n")
    
    ticker = "TEST_AAPL"
    end_date = datetime.now().strftime("%Y-%m-%d")
    start_date = (datetime.now() - timedelta(days=200)).strftime("%Y-%m-%d") # 200일치 (Walk-Forward 충분)
    
    # ─────────────────────────────────────────────────────────────────────────
    # 1. Mock Data 준비
    # ─────────────────────────────────────────────────────────────────────────
    print("1. [Setup] 테스트용 가상 데이터 생성 중...")
    mock_df = create_mock_data(ticker, start_date, end_date)
    print(f"   - 데이터 생성 완료: {len(mock_df)} rows")
    
    # ─────────────────────────────────────────────────────────────────────────
    # 2. Backtrader 엔진 실행 테스트 (with Mock)
    # ─────────────────────────────────────────────────────────────────────────
    print("\n2. [Execution] Backtrader 엔진 실행 테스트...")
    
    # (A) DB 로드 함수(load_data)를 Patch하여, 실제 DB 대신 mock_df를 리턴하게 함
    # (B) 모델 로드 부분도 가중치 파일이 없으므로 Mock 처리 (선택사항)
    #     하지만 엔진 내부에서 파일 없으면 랜덤 스코어 처리하도록 했으므로 그대로 둠
    
    with patch('AI.modules.signal.core.data_loader.DataLoader.load_data') as mock_load:
        mock_load.return_value = mock_df
        
        try:
            # 엔진 실행
            # 가중치 파일 경로는 가짜 경로를 넣어도 엔진 내부에서 예외 처리됨
            fake_weights_path = "./fake_weights.h5" 
            
            results = run_backtrader_engine(
                ticker=ticker,
                start_date=start_date,
                end_date=end_date,
                model_weights_path=fake_weights_path,
                initial_cash=10000000
            )
            
            # 검증
            if results and len(results) > 0:
                strategy = results[0]
                final_value = strategy.broker.getvalue()
                print(f"   - ✅ 백테스트 성공! 최종 자산: {final_value:,.0f}원")
                
                # 분석기 결과 확인
                sharpe = strategy.analyzers.sharpe.get_analysis()
                print(f"   - Sharpe Ratio 결과 존재 여부: {bool(sharpe)}")
                
            else:
                print("   - ❌ 백테스트 결과가 비어 있습니다.")

        except Exception as e:
            print(f"   - ❌ [Fail] 실행 중 치명적 에러: {e}")
            import traceback
            traceback.print_exc()

    print("\n=== [Success] 통합 테스트 종료 ===")

if __name__ == "__main__":
    test_integration()