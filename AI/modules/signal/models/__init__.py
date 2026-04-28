from typing import Any, Dict

from AI.modules.signal.core.base_model import BaseSignalModel

AVAILABLE_MODELS = ("transformer", "itransformer", "tcn", "patchtst")


def _resolve_model_class(model_name: str):
    normalized = model_name.lower()
    if normalized == "transformer":
        from AI.modules.signal.models.transformer.wrapper import TransformerSignalModel

        return TransformerSignalModel
    if normalized == "patchtst":
        from AI.modules.signal.models.patchtst.wrapper import PatchTSTWrapper

        return PatchTSTWrapper
    if normalized in {"itransformer", "i_transformer", "i-transformer"}:
        from AI.modules.signal.models.itransformer.wrapper import ITransformerWrapper

        return ITransformerWrapper
    if normalized == "tcn":
        from AI.modules.signal.models.TCN.wrapper import TCNWrapper

        return TCNWrapper
    return None


def get_model(model_name: str, config: Dict[str, Any]) -> BaseSignalModel:
    model_class = _resolve_model_class(model_name)
    if model_class is None:
        raise ValueError(
            f"Unsupported model: {model_name}. Available models: {list(AVAILABLE_MODELS)}"
        )
    return model_class(config)
