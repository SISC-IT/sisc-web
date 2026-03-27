from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path


ARTIFACT_ROOT_ENV_VAR = "AI_MODEL_WEIGHTS_DIR"
DEFAULT_ARTIFACT_ROOT = Path("AI/data/weights")
PROJECT_ROOT = Path(__file__).resolve().parents[4]


@dataclass(frozen=True, slots=True)
class ModelArtifactPaths:
    root_dir: str
    model_dir: str
    model_path: str
    scaler_path: str | None = None
    metadata_path: str | None = None


def _resolve_absolute(raw_path: str | Path) -> Path:
    path = Path(raw_path)
    if not path.is_absolute():
        path = PROJECT_ROOT / path
    return path.resolve()


def _normalize_mode(raw_mode: str | None) -> str:
    mode = (raw_mode or "prod").strip().lower()
    if mode in {"simulation", "sim", "test", "tests", "dev", "development", "qa"}:
        return "tests"
    if mode in {"live", "production", "prod"}:
        return "prod"
    return mode


def resolve_artifact_root(config_weights_dir: str | None = None) -> str:
    env_root = os.getenv(ARTIFACT_ROOT_ENV_VAR)
    selected_root = (
        env_root.strip()
        if env_root and env_root.strip()
        else (config_weights_dir or str(DEFAULT_ARTIFACT_ROOT))
    )
    return str(_resolve_absolute(selected_root))


def resolve_artifact_file(*relative_parts: str, config_weights_dir: str | None = None) -> str:
    if not relative_parts:
        raise ValueError("At least one path part is required.")
    artifact_root = Path(resolve_artifact_root(config_weights_dir))
    return str((artifact_root.joinpath(*relative_parts)).resolve())


def resolve_model_artifacts(
    model_name: str,
    mode: str | None = None,
    config_weights_dir: str | None = None,
    model_dir: str | None = None,
) -> ModelArtifactPaths:
    normalized_model = model_name.strip().lower()
    normalized_mode = _normalize_mode(mode)
    root_dir = Path(resolve_artifact_root(config_weights_dir))

    if normalized_model == "transformer":
        suffix = "_prod"
        mode_dir = "prod"
        if normalized_mode == "tests":
            suffix = "_test"
            mode_dir = "tests"

        resolved_model_dir = _resolve_absolute(model_dir) if model_dir else (root_dir / "transformer" / mode_dir)
        model_path = resolved_model_dir / f"multi_horizon_model{suffix}.keras"
        scaler_path = resolved_model_dir / f"multi_horizon_scaler{suffix}.pkl"
        return ModelArtifactPaths(
            root_dir=str(root_dir),
            model_dir=str(resolved_model_dir),
            model_path=str(model_path),
            scaler_path=str(scaler_path),
            metadata_path=None,
        )

    if normalized_model in {"itransformer", "i_transformer", "i-transformer"}:
        resolved_model_dir = _resolve_absolute(model_dir) if model_dir else (root_dir / "itransformer")
        return ModelArtifactPaths(
            root_dir=str(root_dir),
            model_dir=str(resolved_model_dir),
            model_path=str(resolved_model_dir / "multi_horizon_model.keras"),
            scaler_path=str(resolved_model_dir / "multi_horizon_scaler.pkl"),
            metadata_path=str(resolved_model_dir / "metadata.json"),
        )

    if normalized_model == "tcn":
        resolved_model_dir = _resolve_absolute(model_dir) if model_dir else (root_dir / "tcn")
        return ModelArtifactPaths(
            root_dir=str(root_dir),
            model_dir=str(resolved_model_dir),
            model_path=str(resolved_model_dir / "model.pt"),
            scaler_path=str(resolved_model_dir / "scaler.pkl"),
            metadata_path=str(resolved_model_dir / "metadata.json"),
        )

    # [수정] PatchTST: 실제 저장 파일명으로 통일 + scaler_path 추가
    if normalized_model == "patchtst":
        resolved_model_dir = _resolve_absolute(model_dir) if model_dir else (root_dir / "PatchTST")
        return ModelArtifactPaths(
            root_dir=str(root_dir),
            model_dir=str(resolved_model_dir),
            model_path=str(resolved_model_dir / "patchtst_model.pt"),       # PatchTST_best.pt → patchtst_model.pt
            scaler_path=str(resolved_model_dir / "patchtst_scaler.pkl"),    # 추가
            metadata_path=None,
        )

    raise ValueError(f"Unsupported model name for artifact resolution: {model_name}")
