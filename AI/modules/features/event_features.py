# AI/modules/signal/core/features/event_features.py
import pandas as pd

def add_date_distance(df: pd.DataFrame, event_dates: pd.Series, col_name: str) -> pd.DataFrame:
    """asof_date 기준 특정 이벤트 후 경과일 계산 [명세서 준수]"""
    # event_dates는 각 행(날짜)별로 가장 최근의 이벤트 날짜를 가지고 있어야 함
    df[f'days_since_{col_name}'] = (df.index - pd.to_datetime(event_dates)).dt.days
    # 음수(미래)는 0 처리
    df[f'days_since_{col_name}'] = df[f'days_since_{col_name}'].clip(lower=0)
    return df

def add_event_window_flags(df: pd.DataFrame, event_dates_list: list, col_name: str) -> pd.DataFrame:
    """FOMC/CPI 전후 1일 여부 (True/False) [명세서 준수]"""
    # 이벤트 당일 플래그
    df[f'event_window_flag_{col_name}'] = df.index.isin(event_dates_list)
    # 전후 1일로 확장 (rolling max)
    df[f'event_window_flag_{col_name}'] = df[f'event_window_flag_{col_name}'].rolling(window=3, center=True).max().fillna(0).astype(bool)
    return df