# AI/modules/finder/TFT_Risk_Manager.py
import pandas as pd
import numpy as np
from ..features.processor import FeatureProcessor

class TFTRiskManager:
    """
    2차 Finder: TFT(Temporal Fusion Transformer) 기반 리스크 관리 모듈.
    시장 및 종목의 급락 확률(crash_prob)을 계산합니다.
    """
    def __init__(self, model_version="v2.1.0"):
        self.model_ver = model_version
        # 급락 기준 정의 (예: 1일 -5% 이하)
        self.crash_threshold = -0.05 

    def predict_crash_probability(self, feature_df: pd.DataFrame):
        """
        입력된 파생 피처를 바탕으로 급락 확률 산출 [명세서 3번 준수]
        """
        # 1. 핵심 입력 데이터 추출 (Primary)
        primary_keys = [
            'vix_z_score', 'us10y_chg', 'yield_spread', 'dxy_chg', 
            'wti_price', 'debt_ratio', 'interest_coverage', 'atr_rank', 'ret_1d'
        ]
        
        # 2. 보조 입력 데이터 추출 (Secondary)
        secondary_keys = [
            'credit_spread_hy', 'mkt_breadth_nh_nl', 'surprise_earnings', 'btc_close'
        ]
        
        input_data = feature_df[primary_keys + secondary_keys]
        
        # 3. 리스크 스코어링 알고리즘 (TFT 모델 추론부 - 예시 로직)
        # 실제 구현 시 학습된 TFT 가중치를 로드하여 연산합니다.
        risk_score = self._calculate_tft_inference(input_data)
        
        return risk_score

    def filter_survivors(self, df: pd.DataFrame, risk_threshold=0.7):
        """
        급락 확률이 높은 종목을 제거하고 생존 종목(survivors_c2) 반환
        """
        df['crash_prob'] = self.predict_crash_probability(df)
        
        # 확률이 문턱값(threshold)보다 낮은 종목만 생존
        survivors_c2 = df[df['crash_prob'] < risk_threshold].copy()
        
        return survivors_c2, df['crash_prob']

    def _calculate_tft_inference(self, data):
        """TFT 모델의 추론 엔진 (가중치 연산 대행)"""
        # 상세 주석: 거시 지표 변화율과 변동성 Z-score의 가중 합산으로 리스크 레벨 산출
        # 실제 운영 환경에서는 Keras/PyTorch 모델 객체가 호출됩니다.
        mock_prob = np.clip(data['vix_z_score'] * 0.4 + data['atr_rank'] * 0.3, 0, 1)
        return mock_prob

# 주석: 본 모듈의 결과물인 survivors_c2는 다음 단계인 TCN/PatchTST의 입력 후보가 됩니다.