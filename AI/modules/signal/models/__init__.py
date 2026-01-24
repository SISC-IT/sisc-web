# AI/modules/signal/models/__init__.py
from typing import Dict, Any
from AI.modules.signal.core.base_model import BaseSignalModel
# 절대 경로로 수정하여 모듈 찾기 에러 방지
from AI.modules.signal.models.PatchTST.wrapper import TransformerSignalModel

# 모델 레지스트리
MODEL_REGISTRY = {
    "transformer": TransformerSignalModel,
}

def get_model(model_name: str, config: Dict[str, Any]) -> BaseSignalModel:
    """
    모델 이름과 설정값을 받아 해당 모델 인스턴스를 반환합니다.
    """
    model_class = MODEL_REGISTRY.get(model_name.lower())
    
    if not model_class:
        raise ValueError(f"지원하지 않는 모델입니다: {model_name}. 가능한 모델: {list(MODEL_REGISTRY.keys())}")
        
    return model_class(config)