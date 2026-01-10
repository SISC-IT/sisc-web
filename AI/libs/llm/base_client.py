# AI/libs/llm/base_client.py
"""
[LLM 클라이언트 인터페이스]
- 모든 LLM 서비스(Groq, Ollama 등)가 준수해야 할 공통 규약을 정의합니다.
- 이를 통해 모델 교체 시 코드 수정을 최소화할 수 있습니다.
"""

from abc import ABC, abstractmethod
from typing import Dict, Any, Optional

class BaseLLMClient(ABC):
    """모든 LLM 클라이언트의 추상 기본 클래스"""

    def __init__(self, api_key: Optional[str] = None, model_name: str = "default"):
        self.api_key = api_key
        self.model_name = model_name

    @abstractmethod
    def generate_text(self, prompt: str, system_prompt: Optional[str] = None, **kwargs) -> str:
        """
        단일 프롬프트를 입력받아 텍스트 응답을 생성합니다.
        
        Args:
            prompt (str): 사용자 입력 프롬프트
            system_prompt (str, optional): 시스템 프롬프트 (역할 정의 등)
            **kwargs: 모델별 추가 파라미터 (temperature 등)
            
        Returns:
            str: 생성된 텍스트
        """
        pass
    
    @abstractmethod
    def get_health(self) -> bool:
        """서비스 상태를 확인합니다."""
        pass