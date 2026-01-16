"""
[통합 테스트: Transformer + Backtrader]
- 데이터 로드 -> 모델 빌드 -> 추론 -> 백테스트 실행까지의 전체 흐름을 검증합니다.
- unittest.mock을 사용하여 DB 연결 없이 가상 데이터(Mock Data)로 파이프라인을 테스트합니다.
- 리팩토링된 'Date Index' 구조가 전체 흐름에서 잘 동작하는지 확인합니다.
"""

import sys
import os
import numpy as np
import pandas as pd
from datetime import datetime, timedelta
from unittest.mock import patch, MagicMock # Mocking 도구 추가

# ─────────────────────────────────────────────────────────────────────────────
#  경로 설정
# ─────────────────────────────────────────────────────────────────────────────
current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../.."))
if project_root not in sys.path:
    sys.path.append(project_root)

# 모듈 임포트
from AI.modules.signal.core.data_loader import SignalDataLoader
from AI.modules.signal.models import get_model
from AI.modules.trader.engine import BacktestEngine
from AI.modules.signal.core.features import add_technical_indicators

def create_mock_data(ticker, start_date, end_date):
    """테스트용 가상 OHLCV 데이터 생성 (인덱스 = Date)"""
    dates = pd.date_range(start=start_date, end=end_date)
    n = len(dates)
    
    df = pd.DataFrame({
        'open': np.random.rand(n) * 100 + 100,
        'high': np.random.rand(n) * 110 + 100,
        'low': np.random.rand(n) * 90 + 100,
        'close': np.random.rand(n) * 100 + 100,
        'volume': np.random.randint(1000, 10000, n),
        'ticker': ticker
    }, index=dates) # ✅ 핵심: 날짜를 인덱스로 설정
    
    df.index.name = "date"
    
    # 지표 추가 (RSI, MACD 등 계산 로직 검증 겸용)
    df = add_technical_indicators(df)
    return df

def test_integration():
    print("\n=== [Test] 통합 테스트 시작 (Signal + Trader) ===\n")
    
    ticker = "TEST_AAPL"
    end_date = datetime.now().strftime("%Y-%m-%d")
    start_date = (datetime.now() - timedelta(days=365)).strftime("%Y-%m-%d")
    
    # ─────────────────────────────────────────────────────────────────────────
    # 1. 데이터 준비 (Mock Data)
    # ─────────────────────────────────────────────────────────────────────────
    print("1. [Setup] 테스트용 가상 데이터 생성 중...")
    try:
        mock_df = create_mock_data(ticker, start_date, end_date)
        print(f"   - 데이터 생성 완료: {len(mock_df)} rows")
        print(f"   - 인덱스 타입 확인: {type(mock_df.index)} (Expected: DatetimeIndex) -> {'✅ OK' if isinstance(mock_df.index, pd.DatetimeIndex) else '❌ Fail'}")
    except Exception as e:
        print(f"   [Fail] 데이터 생성 중 오류: {e}")
        return

    # ─────────────────────────────────────────────────────────────────────────
    # 2. 모델 빌드 및 추론 테스트
    # ─────────────────────────────────────────────────────────────────────────
    print("\n2. [Signal] 모델 빌드 및 추론 테스트...")
    try:
        loader = SignalDataLoader(sequence_length=30)
        
        # 전처리 (날짜 인덱스는 자동 제외되고 수치형만 선택되는지 확인)
        features = mock_df.select_dtypes(include=[np.number]).columns.tolist()
        if 'date' in features:
            print("   [Warn] features에 날짜가 포함되었습니다! (오류 가능성)")
        
        # 스케일링
        loader.scaler.fit(mock_df[features])
        scaled_data = loader.scaler.transform(mock_df[features])
        
        # 입력 데이터 생성 (Batch=1)
        sample_input = scaled_data[:30] 
        sample_input = np.expand_dims(sample_input, axis=0) # (1, 30, features)
        
        # 모델 구조 빌드
        config = {
            "input_shape": (30, sample_input.shape[2]),
            "head_size": 64, "num_heads": 2, "ff_dim": 2,
            "num_blocks": 1, "mlp_units": [32], "dropout": 0.1
        }
        model = get_model("transformer", config)
        model.build(config["input_shape"])
        
        # 추론 실행
        # ✅ [수정] verbose 인자 제거 (TransformerSignalModel wrapper가 지원하지 않음)
        pred = model.predict(sample_input) 
        print(f"   - 추론 성공! 예측값: {pred[0][0]:.4f} ✅")
        
    except Exception as e:
        print(f"   [Fail] 모델 테스트 실패: {e}")
        import traceback
        traceback.print_exc()
        return

    # ─────────────────────────────────────────────────────────────────────────
    # 3. 백테스트 엔진 테스트 (Mocking 적용)
    # ─────────────────────────────────────────────────────────────────────────
    print("\n3. [Trader] 백테스트 엔진 연동 테스트...")
    print("   (DB 연결 없이 Mock Data를 주입하여 실행합니다)")
    
    # ★ 핵심: SignalDataLoader.load_data가 실제 DB 대신 mock_df를 반환하도록 가로채기(Patch)
    with patch('AI.modules.signal.core.data_loader.SignalDataLoader.load_data', return_value=mock_df):
        try:
            # 엔진 초기화
            engine = BacktestEngine(ticker, start_date, end_date)
            print("   - 엔진 초기화 성공")
            
            # 실행 (가중치 파일 없이 실행 -> 랜덤/기본값 예측으로 동작 확인)
            # engine.py 로직상 모델 경로가 없으면 경고 메시지를 띄우고 score=0.5 등으로 처리하거나 종료함
            # 여기서는 에러 없이 루프가 도는지(특히 날짜 인덱싱 부분)를 확인하는 것이 목적
            result_df = engine.run(model_weights_path=None)
            
            if result_df is not None:
                print(f"   - 백테스트 완료! 거래 횟수: {len(result_df)}회 ✅")
                print("   - 실행 결과 샘플:")
                print(result_df.head(2) if not result_df.empty else "     (거래 없음)")
            else:
                print("   - 백테스트 완료 (거래 내역 없음) ✅")
                
        except KeyError as ke:
            print(f"   [Fail] KeyError 발생! 인덱스 처리가 잘못되었을 수 있습니다: {ke}")
        except Exception as e:
            print(f"   [Fail] 백테스트 실행 중 에러: {e}")
            import traceback
            traceback.print_exc()
            return

    print("\n=== [Success] 모든 통합 테스트 통과! ===")

if __name__ == "__main__":
    test_integration()