# AI/modules/signal/models/PatchTST/wrapper.py
import torch
import numpy as np
import pandas as pd
from AI.modules.signal.core.base_model import BaseSignalModel
#from .architecture import build_transformer_model

class PatchTSTWrapper(BaseSignalModel):
    """
    PatchTST 모델 Wrapper: 중장기 추세 및 패턴 분석 엔진 [명세서 3번 준수]
    """
    def __init__(self, model_path=None, config=None):
        super().__init__(model_path, config)
        self.model_name = "PatchTST_Trend_Specialist"
        
        # 명세서에 정의된 핵심 및 보조 입력 키 설정
        self.feature_cols = [
            'log_return', 'ma_trend_score', 'atr_14',
            'sector_return_rel', 'us10y_chg', 'dxy_chg', 'days_since_earnings'
        ]

    def preprocess(self, df: pd.DataFrame):
        """
        PatchTST 입력을 위한 데이터 추출 및 RevIN 정규화 준비 [명세서 4번 준수]
        """
        # 1. 명세서 키 기반 피처 추출
        # PatchTST는 Long Lookback이 특징이므로 충분한 시계열 데이터 필요
        data = df[self.feature_cols].values
        
        # 2. 결측치 처리 (ffill 규칙 준수 및 최종 0 채우기)
        data = np.nan_to_num(data, nan=0.0)
        
        # 3. 3D 텐서 변환 [Batch, Seq, Features]
        # 명세서 권장 사항에 따라 Long Lookback(예: 120일) 적용
        seq_len = self.config.get('seq_len', 120)
        if len(data) < seq_len:
            return None
            
        x = data[-seq_len:].reshape(1, seq_len, len(self.feature_cols))
        return torch.FloatTensor(x)

    def predict(self, df: pd.DataFrame):
        """
        PatchTST 시그널(signal_patch) 생성
        """
        x = self.preprocess(df)
        if x is None:
            return 0.0
            
        self.model.eval()
        with torch.no_grad():
            # 모델 내부적으로 RevIN 정규화가 수행됨
            output = self.model(x)
            # 예측값은 Trader 표준 스키마(prob_up)로 변환
            signal_patch = torch.sigmoid(output).item()
            
        return signal_patch

# 주석: PatchTST는 개별 시계열의 장기 패턴을 Patch 단위로 분석하여 signal_patch를 산출합니다.