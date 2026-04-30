# AI/modules/signal/models/patchtst/wrapper.py
"""
PatchTST Wrapper
-----------------------------------------------
역할: 학습된 PatchTST 모델을 파이프라인(daily_routine.py)과 연결

[코드래빗 리뷰 반영]
- FEATURE_COLUMNS 순서를 train.py와 완전히 동일하게 통일
- save(): config + state_dict 함께 저장
- load(): 저장된 config로 모델 구조 재현 후 가중치 로드
-----------------------------------------------
"""
import os
import sys
import pickle
import numpy as np
import torch
import pandas as pd
from typing import Dict, Any

# 경로 설정
current_dir  = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../../../../.."))
if project_root not in sys.path:
    sys.path.append(project_root)

from AI.modules.signal.core.base_model import BaseSignalModel
from AI.modules.signal.models.patchtst.architecture import PatchTST_Model

# ─────────────────────────────────────────────────────────────────────────────
# [수정] train.py와 완전히 동일한 순서로 정의
# 순서가 다르면 스케일러 통계가 잘못 적용되어 예측값이 틀림
# ─────────────────────────────────────────────────────────────────────────────
FEATURE_COLUMNS = [
    # 일봉 (11개) - train.py와 동일한 순서
    'log_return',
    'ma5_ratio', 'ma20_ratio', 'ma60_ratio',
    'rsi', 'bb_position', 'macd_ratio',
    'open_ratio', 'high_ratio', 'low_ratio',
    'vol_change',

    # 주봉 (4개)
    'week_ma20_ratio', 'week_rsi', 'week_bb_pos', 'week_vol_change',

    # 월봉 (2개)
    'month_ma12_ratio', 'month_rsi',
]

HORIZONS = [1, 3, 5, 7]


class PatchTSTWrapper(BaseSignalModel):
    """
    PatchTST 모델의 파이프라인 어댑터

    사용법:
        wrapper = PatchTSTWrapper(config)
        wrapper.load(model_path, scaler_path)
        result = wrapper.predict(df)
        # {"patchtst_1d": 0.72, "patchtst_3d": 0.68, ...}
    """

    def __init__(self, config: Dict[str, Any]):
        super().__init__(config)
        self.device  = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
        self.model   = None
        self.scaler  = None
        self.seq_len = config.get('seq_len', 120)

    # ── 1. build() ───────────────────────────────────────────────────────────
    def build(self, input_shape: tuple):
        seq_len, num_features = input_shape
        self.model = PatchTST_Model(
            seq_len   = seq_len,
            enc_in    = num_features,
            patch_len = self.config.get('patch_len', 16),
            stride    = self.config.get('stride', 8),
            d_model   = self.config.get('d_model', 128),
            n_heads   = self.config.get('n_heads', 4),
            e_layers  = self.config.get('e_layers', 3),
            d_ff      = self.config.get('d_ff', 256),
            dropout   = self.config.get('dropout', 0.1),
            n_outputs = len(HORIZONS)
        ).to(self.device)
        print(f"[PatchTST] Built: input {input_shape} → output [{len(HORIZONS)}]")

    # ── 2. train() ───────────────────────────────────────────────────────────
    def train(self, X_train, y_train, X_val=None, y_val=None, **kwargs):
        from AI.modules.signal.models.patchtst.train import train as run_training
        run_training()

    # ── 3. predict() ─────────────────────────────────────────────────────────
    def predict(self, df: pd.DataFrame) -> Dict[str, float]:
        """
        입력: df - 특정 종목의 최근 데이터프레임 (최소 seq_len일 이상)
        출력: {"patchtst_1d": 0.72, "patchtst_3d": 0.68, ...}
        """
        if self.model is None or self.scaler is None:
            print("[PatchTST] 모델 또는 스케일러 미로드")
            return self._default_output()

        # Step 1. 피처 선택 (train.py와 동일한 순서)
        df_feat = pd.DataFrame(index=df.index)
        for col in FEATURE_COLUMNS:
            if col in df.columns:
                df_feat[col] = df[col]
            else:
                df_feat[col] = 0.0  # 누락 피처는 0으로 채움

        # Step 2. 데이터 길이 확인
        if len(df_feat) < self.seq_len:
            print(f"[PatchTST] 데이터 부족: {len(df_feat)} < {self.seq_len}")
            return self._default_output()

        # Step 3. 스케일링 (transform만, fit 금지)
        values = df_feat[FEATURE_COLUMNS].values
        values = self.scaler.transform(values)

        # Step 4. 마지막 seq_len일치 자르기 + 배치 차원 추가
        last_seq = values[-self.seq_len:]                    # [120, 17]
        X = torch.FloatTensor(last_seq).unsqueeze(0).to(self.device)  # [1, 120, 17]

        # Step 5. 추론
        self.model.eval()
        with torch.no_grad():
            logits = self.model(X)              # [1, 4]
            probs  = torch.sigmoid(logits)      # 확률로 변환
            probs  = probs.squeeze(0).cpu().numpy()  # [4]

        # Step 6. 딕셔너리 반환
        return {
            f"patchtst_{h}d": float(round(probs[i], 4))
            for i, h in enumerate(HORIZONS)
        }

    # ── 4. save() ────────────────────────────────────────────────────────────
    def save(self, filepath: str):
        """
        [수정] config + state_dict 함께 저장
        load() 시 config로 모델 구조 재현 가능
        """
        if self.model is None:
            print("[PatchTST] 저장할 모델 없음")
            return
        os.makedirs(os.path.dirname(filepath), exist_ok=True)
        torch.save({
            'config'    : self.config,
            'state_dict': self.model.state_dict()
        }, filepath)
        print(f"[PatchTST] 모델 저장: {filepath}")

    # ── 5. load() ────────────────────────────────────────────────────────────
    def load(self, filepath: str, scaler_path: str = None):
        """
        [수정] 저장된 config로 모델 구조 재현 후 가중치 로드
        → position_embedding, head 등 구조가 정확히 일치함
        """
        checkpoint = torch.load(filepath, map_location=self.device)

        # 저장된 config로 모델 구조 재현
        saved_config = checkpoint.get('config', self.config)
        self.config  = saved_config
        self.seq_len = saved_config.get('seq_len', 120)

        self.build((self.seq_len, len(FEATURE_COLUMNS)))

        # 가중치 로드
        state_dict = checkpoint['state_dict']

        # DataParallel로 학습된 경우 module. 접두사 제거
        if all(k.startswith('module.') for k in state_dict.keys()):
            state_dict = {k[len('module.'):]: v for k, v in state_dict.items()}

        self.model.load_state_dict(state_dict)
        self.model.eval()
        print(f"[PatchTST] 모델 로드: {filepath}")

        # 스케일러 로드
        if scaler_path and os.path.exists(scaler_path):
            with open(scaler_path, 'rb') as f:
                self.scaler = pickle.load(f)
            print(f"[PatchTST] 스케일러 로드: {scaler_path}")
        else:
            print("[PatchTST] 스케일러 없음 - predict 불가")

    # ── 헬퍼 ─────────────────────────────────────────────────────────────────
    def _default_output(self) -> Dict[str, float]:
        """오류 시 중립값 반환 (파이프라인 중단 방지)"""
        return {f"patchtst_{h}d": 0.5 for h in HORIZONS}
    
    def get_signals(self, df, **kwargs):
        """BaseSignalModel 추상 메서드 구현 - predict()로 위임"""
        return self.predict(df)
