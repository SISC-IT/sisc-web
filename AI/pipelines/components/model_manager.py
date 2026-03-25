import os
import traceback
from typing import Any, Dict, List, Optional

from AI.config import DataConfig, ModelConfig
from AI.modules.signal.core.data_loader import DataLoader
from AI.modules.signal.core.artifact_paths import (
    resolve_artifact_root,
    resolve_model_artifacts,
)
from AI.modules.signal.models import get_model


def _build_wrapper_if_needed(wrapper: Any, data_config: DataConfig, fallback_features: List[str]) -> None:
    if getattr(wrapper, "model", None) is not None:
        return

    required_features: List[str] = []
    if hasattr(wrapper, "get_required_features"):
        required_features = wrapper.get_required_features()

    feature_count = len(required_features or fallback_features)
    if feature_count <= 0:
        scaler = getattr(wrapper, "scaler", None)
        if scaler is not None and hasattr(scaler, "n_features_in_"):
            try:
                feature_count = int(scaler.n_features_in_)
            except (TypeError, ValueError):
                feature_count = 0

    if feature_count <= 0:
        raise ValueError("Unable to determine model input feature count for build().")

    wrapper.build(input_shape=(data_config.seq_len, feature_count))


def _resolve_model_artifacts_legacy(model_name: str, model_config: ModelConfig) -> tuple[str, str]:
    is_keras_model = model_name.lower() == "transformer"
    ext = ".keras" if is_keras_model else ".pt"

    weights_file = model_config.weights_file.format(model_name=model_name, ext=ext)
    weights_path = os.path.join(model_config.weights_dir, model_name, weights_file)
    scaler_path = os.path.join(model_config.weights_dir, model_name, model_config.scaler_file)
    return weights_path, scaler_path


def _resolve_model_artifacts(
    model_name: str,
    model_config: ModelConfig,
    run_mode: str | None,
) -> tuple[str, Optional[str], Optional[str], str]:
    try:
        paths = resolve_model_artifacts(
            model_name=model_name,
            mode=run_mode,
            config_weights_dir=model_config.weights_dir,
        )
        return paths.model_path, paths.scaler_path, paths.metadata_path, paths.model_dir
    except ValueError:
        weights_path, scaler_path = _resolve_model_artifacts_legacy(model_name, model_config)
        return weights_path, scaler_path, None, os.path.dirname(weights_path)


def initialize_models(
    loader: DataLoader,
    data_config: DataConfig,
    model_config: ModelConfig,
    feature_columns: Optional[List[str]],
    active_models: List[str],
    run_mode: str | None = "tests",
) -> Dict[str, Any]:
    """
    Initialize active model wrappers and let each wrapper restore its own
    inference schema from checkpoint/scaler metadata when available.
    """
    model_wrappers: Dict[str, Any] = {}

    real_n_tickers = len(loader.ticker_to_id)
    real_n_sectors = len(loader.sector_to_id)
    fallback_features = list(feature_columns or data_config.feature_columns or [])
    artifact_root = resolve_artifact_root(model_config.weights_dir)

    if not os.path.isdir(artifact_root):
        raise FileNotFoundError(
            f"Artifact root directory does not exist: {artifact_root}. "
            "Set AI_MODEL_WEIGHTS_DIR or model.weights_dir to a valid mount path."
        )

    print(f"[ModelManager] Initializing models... targets: {active_models}")
    print(f"[ModelManager] Artifact root: {artifact_root}")

    for model_name in active_models:
        weights_path: str
        scaler_path: Optional[str]
        metadata_path: Optional[str]
        model_dir: str
        weights_path, scaler_path, metadata_path, model_dir = _resolve_model_artifacts(
            model_name=model_name,
            model_config=model_config,
            run_mode=run_mode,
        )

        wrapper_config = {
            "seq_len": data_config.seq_len,
            "features": fallback_features,
            "feature_columns": fallback_features,
            "n_tickers": real_n_tickers,
            "n_sectors": real_n_sectors,
            "weights_dir": model_dir,
            "model_path": weights_path,
        }
        if scaler_path:
            wrapper_config["scaler_path"] = scaler_path
        if metadata_path:
            wrapper_config["metadata_path"] = metadata_path

        try:
            wrapper = get_model(model_name, wrapper_config)
            scaler_loaded = False

            print(
                f"[{model_name.upper()}] artifact paths -> "
                f"model: {weights_path}, scaler: {scaler_path}, metadata: {metadata_path}"
            )

            if not os.path.exists(weights_path):
                print(f"[Skip] [{model_name.upper()}] weights not found: {weights_path}")
                continue

            if scaler_path and os.path.exists(scaler_path):
                if hasattr(wrapper, "load_scaler") and callable(getattr(wrapper, "load_scaler")):
                    wrapper.load_scaler(scaler_path)
                    scaler_loaded = True
                    resolved_features = (
                        wrapper.get_required_features() if hasattr(wrapper, "get_required_features") else []
                    )
                    if resolved_features:
                        print(f"[{model_name.upper()}] inference features: {resolved_features}")
                    print(f"[{model_name.upper()}] scaler loaded")
                else:
                    print(f"[Skip] [{model_name.upper()}] scaler loader not implemented")
            else:
                print(f"[Skip] [{model_name.upper()}] scaler not found")

            try:
                if getattr(wrapper, "supports_model_load_before_build", False):
                    try:
                        wrapper.load(weights_path)
                    except Exception:
                        _build_wrapper_if_needed(wrapper, data_config, fallback_features)
                        wrapper.load(weights_path)
                else:
                    _build_wrapper_if_needed(wrapper, data_config, fallback_features)
                    if hasattr(wrapper, "load") and callable(getattr(wrapper, "load")):
                        wrapper.load(weights_path)
                    else:
                        wrapper.model.load_weights(weights_path)

                print(f"[{model_name.upper()}] model weights loaded")
            except Exception as load_error:
                print(f"[{model_name.upper()}] initialization failed while loading weights: {load_error}")
                raise

            if not scaler_loaded:
                if scaler_path and os.path.exists(scaler_path):
                    if hasattr(wrapper, "load_scaler") and callable(getattr(wrapper, "load_scaler")):
                        wrapper.load_scaler(scaler_path)
                        resolved_features = (
                            wrapper.get_required_features() if hasattr(wrapper, "get_required_features") else []
                        )
                        if resolved_features:
                            print(f"[{model_name.upper()}] inference features: {resolved_features}")
                        print(f"[{model_name.upper()}] scaler loaded")
                    else:
                        print(f"[Skip] [{model_name.upper()}] scaler loader not implemented")
                else:
                    print(f"[Skip] [{model_name.upper()}] scaler not found")

            model_wrappers[f"{model_name}_v1"] = wrapper
        except Exception as e:
            print(f"[{model_name.upper()}] initialization failed: {e}")
            traceback.print_exc()

    return model_wrappers
