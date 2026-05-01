from __future__ import annotations

import os
import pickle
import sys
from typing import Any, Dict

import numpy as np
import pandas as pd
import torch


current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../../../../.."))
if project_root not in sys.path:
    sys.path.append(project_root)

from AI.modules.signal.core.base_model import BaseSignalModel
from AI.modules.signal.models.patchtst.architecture import PatchTST_Model
from AI.modules.signal.models.patchtst.feature_contract import (
    PATCHTST_DEFAULT_HORIZONS,
    PATCHTST_FEATURE_SET_VER,
    build_patchtst_metadata,
    get_patchtst_feature_columns,
    load_patchtst_metadata,
    require_patchtst_feature_columns,
    resolve_patchtst_metadata_path,
    save_patchtst_metadata,
    validate_patchtst_feature_columns,
    validate_patchtst_model_shape_contract,
)


FEATURE_COLUMNS = get_patchtst_feature_columns()
HORIZONS = list(PATCHTST_DEFAULT_HORIZONS)


class PatchTSTWrapper(BaseSignalModel):
    """PatchTST 추론 wrapper.

    `predict()`는 기존 호출 호환을 위해 확률 dict만 반환한다.
    평가 경로에서는 `predict_with_status()`를 사용해 fallback 여부를 함께 기록한다.
    """

    def __init__(self, config: Dict[str, Any]):
        super().__init__(config)
        requested_device = config.get("device")
        self.device = torch.device(
            requested_device
            if requested_device
            else ("cuda" if torch.cuda.is_available() else "cpu")
        )
        self.model = None
        self.scaler = None
        self.metadata: dict[str, Any] = {}
        self.legacy_artifact = False

        self.seq_len = int(config.get("seq_len", 120))
        self.horizons = list(config.get("horizons") or HORIZONS)
        self.feature_set_ver = str(config.get("feature_set_ver", PATCHTST_FEATURE_SET_VER))
        self.feature_columns = list(
            config.get("feature_columns")
            or config.get("feature_names")
            or FEATURE_COLUMNS
        )
        validate_patchtst_feature_columns(self.feature_columns)

        self.model_path = config.get("model_path")
        self.scaler_path = config.get("scaler_path")
        self.metadata_path = config.get("metadata_path")

        self.last_prediction_status = "ok"
        self.last_error_message = ""

    @property
    def feature_count(self) -> int:
        """현재 wrapper가 기대하는 feature 수를 반환한다."""
        return len(self.feature_columns)

    def _resolve_horizons(self, output_dim: int) -> list[int]:
        if output_dim <= 0:
            return []
        if len(self.horizons) == output_dim:
            return list(self.horizons)
        if len(self.horizons) > output_dim:
            return list(self.horizons[:output_dim])
        resolved = list(self.horizons or HORIZONS)
        while len(resolved) < output_dim:
            resolved.append(int(resolved[-1]) + 1)
        return resolved

    def _default_output(self) -> Dict[str, float]:
        """fallback 상황에서 중립 확률을 반환한다."""
        return {f"patchtst_{horizon}d": 0.5 for horizon in self.horizons}

    def _artifact_path(self) -> str:
        return str(self.model_path or self.config.get("model_path") or "")

    def _apply_metadata(self, metadata: dict[str, Any]) -> None:
        self.metadata = dict(metadata)
        self.feature_set_ver = str(metadata.get("feature_set_ver", self.feature_set_ver))
        self.feature_columns = list(metadata.get("feature_columns", self.feature_columns))
        validate_patchtst_feature_columns(self.feature_columns)

        expected_count = int(metadata.get("feature_count", len(self.feature_columns)))
        if expected_count != len(self.feature_columns):
            raise ValueError(
                "PatchTST metadata feature_count가 feature_columns 길이와 다릅니다. "
                f"feature_count={expected_count}, columns={len(self.feature_columns)}"
            )

        self.seq_len = int(metadata.get("seq_len", self.seq_len))
        self.horizons = list(metadata.get("horizons") or self.horizons)
        for key in ["patch_len", "stride"]:
            if key in metadata:
                self.config[key] = metadata[key]

        # metadata에는 원 저장 경로를 남기지만, 로드 시에는 호출자가 넘긴 실제 경로를 우선한다.
        if metadata.get("model_path") and not self.model_path:
            self.model_path = str(metadata["model_path"])
        if metadata.get("scaler_path") and not self.scaler_path:
            self.scaler_path = str(metadata["scaler_path"])

    def _apply_legacy_contract(self, saved_config: dict[str, Any]) -> None:
        """metadata가 없는 기존 artifact를 기본 feature 계약으로 해석한다."""
        self.legacy_artifact = True
        self.feature_set_ver = str(saved_config.get("feature_set_ver", PATCHTST_FEATURE_SET_VER))
        self.feature_columns = list(saved_config.get("feature_columns") or FEATURE_COLUMNS)
        validate_patchtst_feature_columns(self.feature_columns)
        self.seq_len = int(saved_config.get("seq_len", self.seq_len))
        self.horizons = list(saved_config.get("horizons") or self.horizons)

    def _validate_checkpoint_metadata_contract(
        self,
        *,
        saved_config: dict[str, Any],
        metadata: dict[str, Any],
    ) -> None:
        """checkpoint config와 metadata가 서로 다른 model shape을 가리키면 실패한다."""
        validate_patchtst_model_shape_contract(
            seq_len=metadata["seq_len"],
            patch_len=metadata["patch_len"],
            stride=metadata["stride"],
            horizons=list(metadata["horizons"]),
        )

        for key in ["seq_len", "patch_len", "stride"]:
            if key in saved_config and key in metadata:
                if int(saved_config[key]) != int(metadata[key]):
                    raise ValueError(
                        "PatchTST checkpoint config와 metadata가 다릅니다. "
                        f"{key}: checkpoint={saved_config[key]}, metadata={metadata[key]}"
                    )

        saved_horizons = saved_config.get("horizons")
        if saved_horizons is not None and list(saved_horizons) != list(metadata["horizons"]):
            raise ValueError(
                "PatchTST checkpoint horizons와 metadata horizons가 다릅니다. "
                f"checkpoint={list(saved_horizons)}, metadata={list(metadata['horizons'])}"
            )

        saved_features = saved_config.get("feature_columns") or saved_config.get("feature_names")
        if saved_features is not None and list(saved_features) != list(metadata["feature_columns"]):
            raise ValueError("PatchTST checkpoint feature_columns와 metadata feature_columns가 다릅니다.")

    def _load_metadata_for_artifact(
        self,
        *,
        model_path: str,
        scaler_path: str | None,
        metadata_path: str | None,
        saved_config: dict[str, Any] | None = None,
    ) -> None:
        resolved_metadata_path = resolve_patchtst_metadata_path(
            model_path=model_path,
            scaler_path=scaler_path,
            metadata_path=metadata_path,
        )
        self.metadata_path = resolved_metadata_path
        metadata = load_patchtst_metadata(resolved_metadata_path)
        if metadata is not None:
            if saved_config is not None:
                self._validate_checkpoint_metadata_contract(
                    saved_config=saved_config,
                    metadata=metadata,
                )
            self.legacy_artifact = False
            self._apply_metadata(metadata)

    def _validate_scaler_contract(self) -> None:
        if self.scaler is None:
            return

        scaler_feature_count = getattr(self.scaler, "n_features_in_", None)
        if scaler_feature_count is not None and int(scaler_feature_count) != len(self.feature_columns):
            raise ValueError(
                "PatchTST scaler feature 수가 metadata와 다릅니다. "
                f"scaler={int(scaler_feature_count)}, metadata={len(self.feature_columns)}"
            )

        scaler_features = getattr(self.scaler, "feature_names_in_", None)
        if scaler_features is not None:
            scaler_features = [str(column) for column in scaler_features]
            if scaler_features != self.feature_columns:
                raise ValueError(
                    "PatchTST scaler feature 순서가 metadata와 다릅니다. "
                    f"scaler={scaler_features}, metadata={self.feature_columns}"
                )

    def build(self, input_shape: tuple):
        seq_len, num_features = input_shape
        self.model = PatchTST_Model(
            seq_len=seq_len,
            enc_in=num_features,
            patch_len=int(self.config.get("patch_len", 16)),
            stride=int(self.config.get("stride", 8)),
            d_model=int(self.config.get("d_model", 128)),
            n_heads=int(self.config.get("n_heads", 4)),
            e_layers=int(self.config.get("e_layers", 3)),
            d_ff=int(self.config.get("d_ff", 256)),
            dropout=float(self.config.get("dropout", 0.1)),
            n_outputs=len(self.horizons),
        ).to(self.device)

    def train(self, X_train, y_train, X_val=None, y_val=None, **kwargs):
        from AI.modules.signal.models.patchtst.train import train as run_training

        run_training()

    def _prepare_feature_window(self, df: pd.DataFrame) -> np.ndarray:
        if df is None or df.empty:
            raise ValueError("PatchTST 입력 DataFrame이 비어 있습니다.")
        require_patchtst_feature_columns(df, feature_columns=self.feature_columns)

        if len(df) < self.seq_len:
            raise ValueError(
                f"PatchTST 추론에 필요한 row가 부족합니다: required={self.seq_len}, got={len(df)}"
            )
        if self.scaler is None:
            raise ValueError("PatchTST scaler가 로드되지 않았습니다.")

        window_frame = df[self.feature_columns].tail(self.seq_len).astype(np.float32)
        if not np.isfinite(window_frame.to_numpy(dtype=np.float32)).all():
            raise ValueError("PatchTST 입력 feature에 NaN 또는 무한대 값이 있습니다.")
        return self.scaler.transform(window_frame).astype(np.float32)

    def _predict_probabilities(self, df: pd.DataFrame) -> np.ndarray:
        if self.model is None:
            raise ValueError("PatchTST model이 로드되지 않았습니다.")

        values = self._prepare_feature_window(df)
        x = torch.FloatTensor(values).unsqueeze(0).to(self.device)

        self.model.eval()
        with torch.no_grad():
            logits = self.model(x)
            probs = torch.sigmoid(logits).squeeze(0).cpu().numpy()
        return np.asarray(probs, dtype=np.float32).reshape(-1)

    def predict_with_status(self, df: pd.DataFrame) -> Dict[str, Any]:
        """평가 경로에서 사용할 status 포함 예측 결과를 반환한다."""
        try:
            probs = self._predict_probabilities(df)
            horizons = self._resolve_horizons(int(probs.size))
            self.horizons = horizons
            output = {
                f"patchtst_{horizon}d": float(round(float(prob), 4))
                for horizon, prob in zip(horizons, probs)
            }
            if self.legacy_artifact:
                status = "fallback"
                error_message = (
                    "metadata sidecar가 없는 legacy artifact입니다. "
                    "평가 기본 집계에서 제외해야 합니다."
                )
            else:
                status = "ok"
                error_message = ""
        except Exception as exc:
            output = self._default_output()
            status = "fallback"
            error_message = str(exc)

        self.last_prediction_status = status
        self.last_error_message = error_message
        return {
            "output": output,
            "prediction_status": status,
            "error_message": error_message,
            "feature_set_ver": self.feature_set_ver,
            "seq_len": self.seq_len,
            "feature_count": self.feature_count,
            "artifact_path": self._artifact_path(),
            "metadata_path": str(self.metadata_path or ""),
            "legacy_artifact": self.legacy_artifact,
            "artifact_status": "legacy" if self.legacy_artifact else "metadata",
        }

    def predict(self, df: pd.DataFrame) -> Dict[str, float]:
        """기존 wrapper 호출 호환용 dict 반환 메서드."""
        return dict(self.predict_with_status(df)["output"])

    def save(self, filepath: str):
        if self.model is None:
            raise ValueError("저장할 PatchTST model이 없습니다.")

        target_path = os.path.abspath(filepath)
        os.makedirs(os.path.dirname(target_path), exist_ok=True)
        self.model_path = target_path
        torch.save(
            {
                "config": {
                    **self.config,
                    "feature_set_ver": self.feature_set_ver,
                    "feature_columns": list(self.feature_columns),
                    "horizons": list(self.horizons),
                    "seq_len": self.seq_len,
                },
                "state_dict": self.model.state_dict(),
            },
            target_path,
        )

        scaler_path = self.scaler_path or os.path.join(os.path.dirname(target_path), "patchtst_scaler.pkl")
        metadata_path = resolve_patchtst_metadata_path(
            model_path=target_path,
            scaler_path=scaler_path,
            metadata_path=self.metadata_path,
        )
        metadata = build_patchtst_metadata(
            config={
                **self.config,
                "feature_set_ver": self.feature_set_ver,
                "feature_columns": list(self.feature_columns),
                "horizons": list(self.horizons),
                "seq_len": self.seq_len,
            },
            model_path=target_path,
            scaler_path=scaler_path,
            feature_columns=self.feature_columns,
        )
        save_patchtst_metadata(metadata_path, metadata)
        self.metadata_path = metadata_path

    def load_scaler(self, filepath: str) -> None:
        scaler_path = os.path.abspath(filepath)
        if not os.path.exists(scaler_path):
            raise FileNotFoundError(f"PatchTST scaler 파일이 없습니다: {scaler_path}")
        with open(scaler_path, "rb") as f:
            self.scaler = pickle.load(f)
        self.scaler_path = scaler_path
        self._validate_scaler_contract()

    def load(self, filepath: str, scaler_path: str = None, metadata_path: str = None):
        target_path = os.path.abspath(filepath)
        if not os.path.exists(target_path):
            raise FileNotFoundError(f"PatchTST model 파일이 없습니다: {target_path}")

        self.model_path = target_path
        resolved_scaler_path = (
            os.path.abspath(scaler_path)
            if scaler_path
            else os.path.join(os.path.dirname(target_path), "patchtst_scaler.pkl")
        )

        checkpoint = torch.load(target_path, map_location=self.device)
        saved_config = dict(checkpoint.get("config", self.config))
        self.config = saved_config

        self._apply_legacy_contract(saved_config)
        self._load_metadata_for_artifact(
            model_path=target_path,
            scaler_path=resolved_scaler_path,
            metadata_path=metadata_path,
            saved_config=saved_config,
        )

        self.build((self.seq_len, len(self.feature_columns)))

        state_dict = checkpoint["state_dict"]
        if all(key.startswith("module.") for key in state_dict.keys()):
            state_dict = {key[len("module.") :]: value for key, value in state_dict.items()}

        self.model.load_state_dict(state_dict)
        self.model.eval()

        if os.path.exists(resolved_scaler_path):
            self.load_scaler(resolved_scaler_path)
        else:
            self.scaler_path = resolved_scaler_path

    def get_signals(self, df, **kwargs):
        """BaseSignalModel 추상 메서드 구현."""
        return self.predict(df)
