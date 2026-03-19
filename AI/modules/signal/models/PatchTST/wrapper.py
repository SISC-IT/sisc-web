# AI/modules/signal/models/PatchTST/wrapper.py
"""
PatchTST Wrapper
-----------------------------------------------
역할: 학습된 PatchTST 모델을 파이프라인(daily_routine.py)과 연결

파이프라인이 이 wrapper를 쓰는 방식:
  wrapper = PatchTSTWrapper(config)
  wrapper.load(model_path, scaler_path)
  result = wrapper.predict(df)
  # result: {"patchtst_1d": 0.72, "patchtst_3d": 0.68, ...}

BaseSignalModel을 상속받아야 한다.
→ build / train / predict / save / load 5개 메서드 구현 필수
-----------------------------------------------
"""
import os
import sys
import pickle
import numpy as np
import torch
import torch.nn as nn
import pandas as pd
from typing import Dict, Any, Optional

# 프로젝트 루트 경로 설정
current_dir  = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../../../.."))
if project_root not in sys.path:
    sys.path.append(project_root)

from AI.modules.signal.core.base_model import BaseSignalModel
from .architecture import PatchTST_Model

# train.py와 동일한 피처 정의 (반드시 일치해야 함)
FEATURE_COLUMNS = [
    'log_return',
    'ma20_ratio', 'ma60_ratio',
    'rsi', 'bb_position', 'macd_ratio',
    'open_ratio', 'high_ratio', 'low_ratio',
    'vol_change', 'ma5_ratio',
    # 개선안: 주봉/월봉
    'week_ma20_ratio', 'week_rsi', 'week_bb_pos', 'week_vol_change',
    'month_ma12_ratio', 'month_rsi',
]

HORIZONS = [1, 3, 5, 7]  # 예측 horizon (train.py와 동일해야 함)


class PatchTSTWrapper(BaseSignalModel):
    """
    PatchTST 모델의 파이프라인 어댑터

    핵심 역할:
    1. df(데이터프레임)에서 필요한 피처만 골라내기
    2. 학습 때 저장한 scaler로 정규화
    3. 모델 입력 형태 [1, seq_len, features]로 변환
    4. 추론 후 딕셔너리로 반환
    """

    def __init__(self, config: Dict[str, Any]):
        """
        config 예시:
        {
            "seq_len"   : 120,
            "patch_len" : 16,
            "stride"    : 8,
            "d_model"   : 128,
            "n_heads"   : 4,
            "e_layers"  : 3,
            "d_ff"      : 256,
            "dropout"   : 0.1,
        }
        """
        super().__init__(config)
        # GPU가 있으면 GPU, 없으면 CPU
        self.device  = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
        self.model   = None   # build() 또는 load() 호출 시 생성
        self.scaler  = None   # load() 호출 시 로드
        self.seq_len = config.get('seq_len', 120)

    # ── 1. build() ───────────────────────────────────────────────────────────
    def build(self, input_shape: tuple):
        """
        모델 구조 생성
        input_shape: (seq_len, num_features) 예) (120, 17)
        """
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
            n_outputs = len(HORIZONS)  # 4
        ).to(self.device)

        print(f"PatchTST built: input {input_shape} → output [{len(HORIZONS)}]")

    # ── 2. train() ───────────────────────────────────────────────────────────
    def train(self, X_train, y_train, X_val=None, y_val=None, **kwargs):
        """
        wrapper에서 직접 학습하는 경우 (보통은 train.py를 직접 실행)
        train.py의 학습 로직을 호출하는 방식으로 연결
        """
        from .train import train as run_training
        run_training()

    # ── 3. predict() ─────────────────────────────────────────────────────────
    def predict(self, df: pd.DataFrame) -> Dict[str, float]:
        """
        파이프라인에서 호출하는 핵심 메서드

        입력: df - 특정 종목의 최근 데이터프레임
              (최소 seq_len(120)일 이상의 행 필요)

        출력: {
            "patchtst_1d": 0.72,  # 1일 후 상승 확률
            "patchtst_3d": 0.68,  # 3일 후 상승 확률
            "patchtst_5d": 0.61,  # 5일 후 상승 확률
            "patchtst_7d": 0.58   # 7일 후 상승 확률
        }
        """
        if self.model is None or self.scaler is None:
            print("[PatchTST] 모델 또는 스케일러가 로드되지 않았습니다.")
            return self._default_output()

        # ── Step 1. 피처 선택 ────────────────────────────────────────────────
        # df에서 이 모델에 필요한 컬럼만 골라낸다.
        # 없는 컬럼이 있으면 해당 컬럼을 0으로 채운다.
        available = [c for c in FEATURE_COLUMNS if c in df.columns]

        if len(available) < len(FEATURE_COLUMNS):
            missing = set(FEATURE_COLUMNS) - set(available)
            print(f"[PatchTST] 피처 누락: {missing} → 0으로 채움")

        df_feat = df[available].copy()
        for col in FEATURE_COLUMNS:
            if col not in df_feat.columns:
                df_feat[col] = 0.0

        df_feat = df_feat[FEATURE_COLUMNS]  # 순서 고정

        # ── Step 2. 데이터 길이 확인 ─────────────────────────────────────────
        if len(df_feat) < self.seq_len:
            print(f"[PatchTST] 데이터 부족: {len(df_feat)}일 < {self.seq_len}일")
            return self._default_output()

        # ── Step 3. 스케일링 ─────────────────────────────────────────────────
        # 학습 때 fit한 scaler로 transform만 (fit 금지!)
        # fit을 다시 하면 학습 때와 다른 기준으로 정규화돼서 모델이 망가짐
        values = df_feat.values                          # numpy array로 변환
        values = self.scaler.transform(values)           # 정규화

        # ── Step 4. 시퀀스 자르기 ────────────────────────────────────────────
        # 마지막 seq_len(120)일치 데이터만 사용
        last_seq = values[-self.seq_len:]                # [120, 17]

        # 배치 차원 추가: [120, 17] → [1, 120, 17]
        # 모델은 배치 단위로 입력받기 때문에 1개짜리 배치를 만들어줌
        X = torch.FloatTensor(last_seq).unsqueeze(0).to(self.device)

        # ── Step 5. 추론 ─────────────────────────────────────────────────────
        self.model.eval()  # 평가 모드: dropout 비활성화
        with torch.no_grad():
            logits = self.model(X)           # [1, 4] - 4개 logits
            probs  = torch.sigmoid(logits)   # logits → 0~1 확률로 변환
            probs  = probs.squeeze(0)        # [1, 4] → [4]
            probs  = probs.cpu().numpy()     # GPU → CPU → numpy

        # ── Step 6. 딕셔너리로 반환 ──────────────────────────────────────────
        return {
            f"patchtst_{h}d": float(round(probs[i], 4))
            for i, h in enumerate(HORIZONS)
        }
        # 결과 예시: {"patchtst_1d": 0.72, "patchtst_3d": 0.68, ...}

    # ── 4. save() ────────────────────────────────────────────────────────────
    def save(self, filepath: str):
        """모델 가중치 저장"""
        if self.model is None:
            print("[PatchTST] 저장할 모델이 없습니다.")
            return
        os.makedirs(os.path.dirname(filepath), exist_ok=True)
        torch.save(self.model.state_dict(), filepath)
        print(f"[PatchTST] 모델 저장: {filepath}")

    # ── 5. load() ────────────────────────────────────────────────────────────
    def load(self, filepath: str, scaler_path: str = None):
        """
        모델 가중치 + 스케일러 로드
        daily_routine.py에서 서비스 시작할 때 호출
        """
        # 모델 구조 먼저 생성 (가중치를 담을 껍데기)
        if self.model is None:
            self.build((self.seq_len, len(FEATURE_COLUMNS)))

        # 저장된 가중치를 껍데기에 덮어씌움
        self.model.load_state_dict(
            torch.load(filepath, map_location=self.device)
        )
        self.model.eval()
        print(f"[PatchTST] 모델 로드: {filepath}")

        # 스케일러 로드 (predict에서 transform에 사용)
        if scaler_path and os.path.exists(scaler_path):
            with open(scaler_path, 'rb') as f:
                self.scaler = pickle.load(f)
            print(f"[PatchTST] 스케일러 로드: {scaler_path}")
        else:
            print("[PatchTST] 스케일러 없음 - predict 불가")

    # ── 헬퍼 ─────────────────────────────────────────────────────────────────
    def _default_output(self) -> Dict[str, float]:
        """오류 발생 시 중립값(0.5) 반환 - 파이프라인이 멈추지 않도록"""
        return {f"patchtst_{h}d": 0.5 for h in HORIZONS}
