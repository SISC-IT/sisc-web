# AI/modules/signal/core/dataset_builder.py
import pandas as pd
from typing import Union, Optional

from AI.modules.signal.core.data_loader import DataLoader
from AI.modules.features.processor import FeatureProcessor
from AI.modules.features.market_derived import add_market_changes, add_macro_changes

def apply_strict_nan_rules(df: pd.DataFrame) -> pd.DataFrame:
    """
    SISC 데이터 명세서에 따른 엄격한 결측치 처리 규칙을 적용합니다.
    """
    df_clean = df.copy()

    macro_cols = ['vix_close', 'vix_change_rate', 'us10y', 'us10y_chg', 'dxy_close', 'dxy_chg']
    available_macro = [col for col in macro_cols if col in df_clean.columns]
    
    if available_macro:
        df_clean[available_macro] = df_clean[available_macro].ffill()
    
    df_clean = df_clean.dropna().reset_index(drop=True)
    return df_clean

def get_standard_training_data(
    start_date_or_df: Union[str, pd.DataFrame], 
    end_date: Optional[str] = None
) -> pd.DataFrame:
    """
    SISC 파이프라인 표준 학습 데이터셋 생성기. (학습/추론 겸용)
    - 사용법 1 (학습용): get_standard_training_data('2020-01-01', '2024-01-01') -> DB 조회
    - 사용법 2 (추론용): get_standard_training_data(df) -> DB 생략하고 전처리만 수행
    """
    # 1. 입력 타입에 따른 분기 처리 (DB 로드 vs 직접 주입)
    if isinstance(start_date_or_df, pd.DataFrame):
        df = start_date_or_df.copy()
    else:
        loader = DataLoader()
        df = loader.load_data_from_db(start_date_or_df, end_date)
        if df is None or df.empty:
            raise ValueError(f"지정된 기간({start_date_or_df} ~ {end_date})의 데이터를 불러오지 못했습니다.")
    
    # 2. 파생 피처 레이어 계산 (1차: 기초 변화율 연산)
    df = add_market_changes(df)
    df = add_macro_changes(df)
    
    # 3. 파생 피처 레이어 계산 (2차: FeatureProcessor를 통한 심화 지표 일괄 연산)
    processor = FeatureProcessor(df)
    df = processor.execute_pipeline() 
    
    # 4. 결측치(NaN) 처리 규칙 적용
    df = apply_strict_nan_rules(df)
    
    return df