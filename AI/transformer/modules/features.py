# transformer/modules/features.py
from __future__ import annotations
from typing import List
import numpy as np
import pandas as pd

# ===== 공개 상수 (모델 입력으로 들어갈 피처 목록) =====
# 절대 가격(Price)은 없고, 모두 비율/지표입니다.
FEATURES: List[str] = [
    "log_ret",      # 로그 수익률
    "ma_dist_5",    # 5일 이평선 괴리율
    "ma_dist_20",   # 20일 이평선 괴리율
    "ma_dist_60",   # 60일 이평선 괴리율
    "volatility",   # 변동성
    "vol_change",   # 거래량 변화율
    "rsi",          # RSI (0~1 스케일)
    "CLOSE_RAW"     # (학습 제외용) 라벨링 및 수익률 계산을 위해 남겨둠
]

# ===== 기술지표 유틸 =====
def _rsi_wilder(close: pd.Series, period: int = 14) -> pd.Series:
    """Wilder RSI 계산."""
    delta = close.diff()
    gain = delta.clip(lower=0.0)
    loss = -delta.clip(upper=0.0)
    avg_gain = gain.ewm(alpha=1/period, adjust=False).mean()
    avg_loss = loss.ewm(alpha=1/period, adjust=False).mean()
    rs = avg_gain / avg_loss.replace(0, np.nan)
    return 100.0 - (100.0 / (1.0 + rs))

def build_features(df: pd.DataFrame) -> pd.DataFrame:
    """
    범용 모델을 위한 피처 엔지니어링 (Stationary Features)
    - 절대 가격(100원, 100만원 등)을 모두 제거하고 '변화율' 위주로 구성
    """
    # 컬럼 소문자 매핑 및 필요한 컬럼 확인
    cols = {c.lower(): c for c in df.columns}
    need = ["close", "volume"] # 최소 필요 컬럼
    
    # 데이터프레임 복사 및 컬럼명 통일
    df_processed = df.copy()
    mapping = {}
    for k in need:
        if k in cols:
            mapping[cols[k]] = k
    if mapping:
        df_processed = df_processed.rename(columns=mapping)

    C = df_processed["close"].astype(float)
    V = df_processed["volume"].astype(float)
    
    # 결과를 담을 DataFrame
    feats = pd.DataFrame(index=df_processed.index)

    # 1. 로그 수익률 (가격의 절대 레벨 제거)
    # 오늘 가격이 어제보다 몇 % 변했는지
    feats['log_ret'] = np.log(C / C.shift(1)).fillna(0)

    # 2. 이동평균 괴리율 (현재가가 이평선 대비 얼마나 떨어져 있는지 비율)
    for w in [5, 20, 60]:
        ma = C.rolling(window=w).mean()
        # (현재가 - 이평선) / 이평선 -> 비율로 변환
        feats[f'ma_dist_{w}'] = ((C - ma) / ma).fillna(0)

    # 3. 변동성 (최근 20일간의 가격 변화폭 표준편차)
    feats['volatility'] = feats['log_ret'].rolling(window=20).std().fillna(0)

    # 4. 거래량 변화율 (어제 대비 거래량 급등 여부)
    # 0으로 나누는 것 방지
    V_shifted = V.shift(1).replace(0, np.nan)
    feats['vol_change'] = (V / V_shifted - 1).fillna(0)
    
    # 5. RSI (0~100 사이 값이므로 100으로 나눠 0~1로 스케일링)
    rsi_val = _rsi_wilder(C, period=14)
    feats['rsi'] = (rsi_val / 100.0).fillna(0.5) # NaN이면 중간값 0.5

    # 6. 원본 종가 보존 (라벨링용, 모델 입력시엔 제외해야 함)
    feats["CLOSE_RAW"] = C

    # 앞부분 NaN 제거 (이평선 계산 등으로 생긴 빈 값)
    return feats.dropna()