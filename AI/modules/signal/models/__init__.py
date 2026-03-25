from typing import Any, Dict

from AI.modules.signal.core.base_model import BaseSignalModel

AVAILABLE_MODELS = ("transformer", "patchtst")


def _resolve_model_class(model_name: str):
    normalized = model_name.lower()
    if normalized == "transformer":
        from AI.modules.signal.models.transformer.wrapper import TransformerSignalModel

        return TransformerSignalModel
    if normalized == "patchtst":
        from AI.modules.signal.models.PatchTST.wrapper import PatchTSTWrapper

        return PatchTSTWrapper
    return None


def get_model(model_name: str, config: Dict[str, Any]) -> BaseSignalModel:
    model_class = _resolve_model_class(model_name)
    if model_class is None:
        raise ValueError(
            f"Unsupported model: {model_name}. Available models: {list(AVAILABLE_MODELS)}"
        )
    return model_class(config)
