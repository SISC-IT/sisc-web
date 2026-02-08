# AI/modules/signal/models/TCN/wrapper.py
import torch
import numpy as np
import pandas as pd
from ..base_model import BaseModel # 기존 base_model 계승

class TCNWrapper(BaseModel):
    """
    TCN 모델 Wrapper: 단기 패턴 및 전환점 포착 엔진 [명세서 3번 준수]
    """
    def __init__(self, model_path=None, config=None):
        super().__init__(model_path, config)
        self.model_name = "TCN_Local_Pattern"
        # 명세서에 정의된 핵심 및 보조 입력 키 정의
        self.feature_cols = [
            'log_return', 'vol_change', 'rsi_14', 'macd_ratio', 
            'bollinger_ub', 'bollinger_lb', 'days_to_earnings',
            'vix_change_rate', 'sector_return_rel'
        ]

    def preprocess(self, df: pd.DataFrame):
        """
        TCN 입력을 위한 데이터 추출 및 텐서 변환 [명세서 4번 데이터 검증 준수]
        """
        # 1. 명세서 키 기반 피처 추출
        data = df[self.feature_cols].values
        
        # 2. 결측치 처리 (명세서 규칙: 텐서 생성 시점 NaN 제거)
        data = np.nan_to_num(data, nan=0.0)
        
        # 3. 3D 텐서 변환 [Batch, Seq, Features]
        # TCN은 국소 패턴을 보므로 상대적으로 짧은 Lookback 사용 (예: 30일)
        seq_len = self.config.get('seq_len', 30)
        if len(data) < seq_len:
            return None
            
        x = data[-seq_len:].reshape(1, seq_len, len(self.feature_cols))
        return torch.FloatTensor(x)

    def predict(self, df: pd.DataFrame):
        """
        TCN 시그널(signal_tcn) 생성
        """
        x = self.preprocess(df)
        if x is None:
            return 0.0
            
        self.model.eval()
        with torch.no_grad():
            # 모델 출력은 Trader 표준 스키마인 prob_up으로 어댑팅됨
            output = self.model(x)
            signal_tcn = torch.sigmoid(output).item()
            
        return signal_tcn

# 주석: TCN은 시장 전체의 흐름보다 개별 시계열의 기술적 패턴에 집중하여 signal_tcn을 산출합니다.