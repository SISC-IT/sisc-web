# AI/pipelines/components/data_processor.py

import pandas as pd
from datetime import datetime
from AI.modules.signal.core.data_loader import DataLoader
from AI.modules.features.legacy.technical_features import add_technical_indicators, add_multi_timeframe_features

def load_and_preprocess_data(loader: DataLoader, target_tickers: list, exec_date_str: str, strategy_config: dict) -> dict:
    """
    [데이터 로딩 및 전처리 담당]
    대상 종목들의 시장 데이터를 로드하고 기술적 지표를 전처리하여 맵(Map) 형태로 반환합니다.
    
    Args:
        loader (DataLoader): DB 통신용 데이터 로더
        target_tickers (list): 전처리할 대상 종목 심볼 리스트
        exec_date_str (str): 시뮬레이션/기준이 되는 목표 날짜 (YYYY-MM-DD)
        strategy_config (dict): 전략 설정 (seq_len 등 검증용)
        
    Returns:
        dict: 종목 심볼을 키로, 전처리된 DataFrame을 값으로 가지는 딕셔너리
    """
    print(f"3. 데이터 로딩 및 전처리 중 ({len(target_tickers)}종목)...")
    data_map = {}
    
    # 여유 기간을 두고 과거 데이터를 일괄 로드 (I/O 병목 방지)
    bulk_df = loader.load_data_from_db(start_date="2023-01-01", end_date=exec_date_str, tickers=target_tickers)
    target_timestamp = pd.to_datetime(exec_date_str)
    
    if not bulk_df.empty:
        for ticker in target_tickers:
            # 종목별로 데이터를 슬라이싱하여 깊은 복사(copy)
            df = bulk_df[bulk_df['ticker'] == ticker].copy()
            if df.empty: continue
                
            try:
                # 파생 변수 및 다중 타임프레임 기술적 지표 추가
                df = add_technical_indicators(df)
                df = add_multi_timeframe_features(df)
                df.set_index('date', inplace=True)
                
                # 시뮬레이션 정합성을 위해 target_date 이후의 미래 데이터 차단 (Look-ahead bias 방지)
                df = df.loc[:target_timestamp] 
                
                # 해당 일자에 거래 데이터가 없는 경우(휴장일 등) 해당 종목 스킵
                if df.empty or df.index[-1] != target_timestamp:
                    continue
                
                # 모델 추론을 위한 최소 시퀀스 길이(seq_len)를 충족하는 데이터만 맵에 담기
                if len(df) >= strategy_config['seq_len']:
                    data_map[ticker] = df
            except Exception as e:
                print(f"   [Error] {ticker} 데이터 전처리 중 오류: {e}")

    return data_map