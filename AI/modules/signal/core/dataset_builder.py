# AI/modules/signal/core/dataset_builder.py

from AI.modules.signal.core.data_loader import DataLoader
from AI.modules.features.technical import add_technical_indicators
from AI.modules.features.market_derived import add_macro_features

def get_standard_training_data(start_date: str, end_date: str) -> pd.DataFrame:
    """
    데이터 수집/전처리 코드를 짤 필요 없이 이 함수만 호출하면 됩니다.
    명세서에 정의된 모든 원천/파생 피처가 포함된 DataFrame을 반환합니다.
    """
    # 1. DB에서 원천 데이터(Raw) 로드
    loader = DataLoader()
    raw_df = loader.load_data_from_db(start_date, end_date)
    
    # 2. 파생 피처 레이어 계산
    # (팀장님이 기존 features 모듈을 활용해 모든 지표를 미리 계산해서 붙여줌)
    df = add_technical_indicators(raw_df)   # rsi_14, macd 등 추가
    df = add_macro_features(df)             # vix_z_score, us10y_chg 등 추가
    
    # 3. 결측치(NaN) 처리
    # Market은 Drop, Macro는 ffill 등...
    df = apply_strict_nan_rules(df)
    
    return df