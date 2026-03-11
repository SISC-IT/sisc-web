# AI/modules/features/processor.py
import pandas as pd
from AI.modules.features.market_derived import add_standard_technical_features, add_multi_timeframe_features
from AI.modules.features.event_features import add_date_distance, add_event_window_flags
from AI.modules.features.technical import compute_correlation_spike, compute_recent_loss_ema

class FeatureProcessor:
    """
    SISC 파생 피처 레이어 통합 관리 클래스.
    기존 features.py의 기능을 포함하며 명세서의 Standard Key를 생성합니다.
    """
    def __init__(self, df: pd.DataFrame):
        # 원본 데이터 복사 및 정렬
        self.df = df.copy()
        if 'date' in self.df.columns:
            self.df['date'] = pd.to_datetime(self.df['date'])
            self.df = self.df.sort_values('date')
            self.df.set_index('date', inplace=True)

    def execute_pipeline(self, event_info=None, sector_df=None):
        """전체 파생 피처 생성 파이프라인 실행"""
        
        # 1. 일봉 기준 표준 기술적 지표 및 수익률 계산
        self.df = add_standard_technical_features(self.df)
        
        # 2. 주봉/월봉 멀티 타임프레임 피처 결합
        self.df = add_multi_timeframe_features(self.df)
        
        # 3. 이벤트 기반 피처
        if event_info:
            self.df = add_date_distance(self.df, event_info.get('ipo_dates', pd.Series()), 'ipo')
            self.df = add_event_window_flags(self.df, event_info.get('fomc_dates', []), 'fomc')

        # 사용자님이 완벽하게 고치신 부분! 👍
        if 'vix_close' in self.df.columns:
            self.df['correlation_spike'] = compute_correlation_spike(self.df['close'], self.df['vix_close'])
        
        self.df['recent_loss_ema'] = compute_recent_loss_ema(self.df['close'], self.df['close'].shift(1))
            
        # 4. 데이터 정제
        self.df = self.finalize_data()
        self.df.reset_index(inplace=True)
        
        return self.df

    def finalize_data(self):
        """무한대 값 제거 및 결측치 0 채움 (수치 안정성 확보)"""
        import numpy as np
        self.df.replace([np.inf, -np.inf], np.nan, inplace=True)
        self.df = self.df.fillna(0)
        return self.df