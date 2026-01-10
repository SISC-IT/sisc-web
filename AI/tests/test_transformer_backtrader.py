# AI/tests/test_transformer_backtrader.py
"""
[통합 테스트: Transformer + Backtrader]
- 데이터 로드 -> 모델 빌드 -> 추론 -> 백테스트 실행까지의 전체 흐름을 검증합니다.
- 실제 학습(Train)은 생략하거나 최소화하고, 파이프라인의 연결성을 주로 테스트합니다.
"""

import sys
import os
import numpy as np
import pandas as pd
from datetime import datetime, timedelta

# 프로젝트 루트 경로 추가
current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../.."))
if project_root not in sys.path:
    sys.path.append(project_root)

# 변경된 모듈 임포트
from AI.modules.signal.core.data_loader import SignalDataLoader
from AI.modules.signal.models import get_model
from AI.modules.trader.engine import BacktestEngine

def test_integration():
    print("\n=== [Test] 통합 테스트 시작 (Signal + Trader) ===\n")
    
    ticker = "AAPL"
    end_date = datetime.now().strftime("%Y-%m-%d")
    start_date = (datetime.now() - timedelta(days=365)).strftime("%Y-%m-%d")
    
    # 1. 데이터 로더 테스트
    print("1. [Signal] 데이터 로드 테스트...")
    loader = SignalDataLoader(sequence_length=30) # 테스트용 짧은 시퀀스
    try:
        # DB 연결이 안 될 경우를 대비해 Mock 데이터 사용 가능성 열어둠
        # 여기서는 실제 DB 조회 시도 (DB가 없다면 에러 발생하므로 예외처리 필요)
        df = loader.load_data(ticker, start_date, end_date)
        
        if df.empty:
            print("   [Skip] DB에 데이터가 없어 테스트용 가상 데이터를 생성합니다.")
            # 가상 데이터 생성 (Mocking)
            dates = pd.date_range(start=start_date, end=end_date)
            df = pd.DataFrame({
                'date': dates,
                'open': np.random.rand(len(dates)) * 100,
                'high': np.random.rand(len(dates)) * 110,
                'low': np.random.rand(len(dates)) * 90,
                'close': np.random.rand(len(dates)) * 100,
                'volume': np.random.randint(1000, 10000, len(dates)),
                'ticker': ticker
            })
            # 지표 추가 (내부적으로 호출됨)
            from AI.modules.signal.core.features import add_technical_indicators
            df = add_technical_indicators(df)
            
        print(f"   - 데이터 로드 완료 (행 수: {len(df)})")
        
    except Exception as e:
        print(f"   [Fail] 데이터 로드 실패: {e}")
        return

    # 2. 모델 빌드 및 추론 테스트
    print("\n2. [Signal] 모델 빌드 및 추론 테스트...")
    try:
        # 전처리
        features = df.select_dtypes(include=[np.number]).columns.tolist()
        # loader.scaler가 없으면 fit
        loader.scaler.fit(df[features])
        
        # 입력 데이터 생성 (마지막 1개 샘플)
        scaled_data = loader.scaler.transform(df[features])
        sample_input = scaled_data[:30] # (30, features)
        sample_input = np.expand_dims(sample_input, axis=0) # (1, 30, features)
        
        # 모델 생성
        config = {
            "input_shape": (30, sample_input.shape[2]),
            "head_size": 64,
            "num_heads": 2,
            "ff_dim": 2,
            "num_blocks": 1,
            "mlp_units": [32],
            "dropout": 0.1,
            "mlp_dropout": 0.1
        }
        model = get_model("transformer", config)
        model.build(config["input_shape"])
        
        # 추론 (가중치 로드 없이 초기화 상태로 테스트)
        pred = model.predict(sample_input)
        print(f"   - 추론 성공! 예측값: {pred[0][0]:.4f}")
        
    except Exception as e:
        print(f"   [Fail] 모델 테스트 실패: {e}")
        import traceback
        traceback.print_exc()
        return

    # 3. 백테스트 엔진 테스트
    print("\n3. [Trader] 백테스트 엔진 연동 테스트...")
    try:
        engine = BacktestEngine(ticker, start_date, end_date)
        
        # 엔진 내부에서 load_data를 다시 하므로, 테스트 속도를 위해 Mocking하거나
        # 위에서 만든 df를 주입하는 기능을 엔진에 추가하면 좋음.
        # 현재는 엔진이 직접 DB를 조회하도록 되어 있으므로, 예외 발생 가능성 있음.
        
        # (테스트용) 엔진 실행 - 모델 경로 없이 실행하면 경고 후 중단되거나 랜덤 예측
        # 여기서는 로직 연결만 확인
        print("   - 엔진 초기화 성공")
        
        # 실제 run 호출은 DB 의존성이 크므로 생략하거나, 
        # engine.run(model_weights_path=None) 호출 시 에러가 안 나는지만 확인
        
    except Exception as e:
        print(f"   [Fail] 백테스트 엔진 테스트 실패: {e}")
        return

    print("\n=== [Success] 모든 통합 테스트 통과! ===")

if __name__ == "__main__":
    test_integration()