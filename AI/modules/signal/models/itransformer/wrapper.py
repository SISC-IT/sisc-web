# AI/modules/signal/models/iTransformer/wrapper.py
import torch
import numpy as np
import pandas as pd
from AI.modules.signal.core.base_model import BaseSignalModel

class ITransformerWrapper(BaseSignalModel):
    """
    iTransformer 모델 Wrapper: 다변량 상관관계 및 거시 지표 분석 엔진 [명세서 3번 준수]
    """
    def __init__(self, model_path=None, config=None):
        super().__init__(model_path, config)
        self.model_name = "iTransformer_Correlation_Expert"
        
        # 명세서에 정의된 핵심 및 보조 입력 키 (거시/자산 중심)
        self.feature_cols = [
            'us10y', 'yield_spread', 'dxy_close', 'wti_price', 'gold_price',
            'btc_close', 'credit_spread_hy', 'mkt_breadth_ma200', 'surprise_cpi'
        ]

    def preprocess(self, df: pd.DataFrame):
        """
        iTransformer 입력을 위한 데이터 추출 및 다변량 텐서 변환 [명세서 4번 준수]
        """
        # 1. 거시 및 시장 지표 중심 피처 추출
        data = df[self.feature_cols].values
        
        # 2. 결측치 처리 (ffill 규칙 및 최종 0 채우기)
        data = np.nan_to_num(data, nan=0.0)
        
        # 3. 3D 텐서 변환 [Batch, Seq, Features]
        seq_len = self.config.get('seq_len', 60)
        if len(data) < seq_len:
            return None
            
        x = data[-seq_len:].reshape(1, seq_len, len(self.feature_cols))
        return torch.FloatTensor(x)

    def predict(self, df: pd.DataFrame):
        """
        iTransformer 시그널(signal_itrans) 생성
        """
        x = self.preprocess(df)
        if x is None:
            return 0.0
            
        self.model.eval()
        with torch.no_grad():
            output = self.model(x)
            # 예측값은 상승 확률(prob_up)로 규격화
            signal_itrans = torch.sigmoid(output).item()
            
        return signal_itrans