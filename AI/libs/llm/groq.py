# AI/libs/llm/groq.py
"""
[Groq 클라이언트 구현체]
- Groq API를 사용하여 빠른 추론을 수행합니다.
- 기존 AI/xai/modules/groq_client.py 의 역할을 대체합니다.
"""

import os
from groq import Groq
from typing import Optional
from .base_client import BaseLLMClient

class GroqClient(BaseLLMClient):
    def __init__(self, api_key: Optional[str] = None, model_name: str = "llama-3.3-70b-versatile"):
        # 환경변수 우선, 없으면 인자값 사용
        key = api_key or os.environ.get("GROQ_API_KEY")
        if not key:
            raise ValueError("Groq API Key가 설정되지 않았습니다.")
            
        super().__init__(api_key=key, model_name=model_name)
        self.client = Groq(api_key=key)

    def generate_text(self, prompt: str, system_prompt: Optional[str] = None, **kwargs) -> str:
        messages = []
        if system_prompt:
            messages.append({"role": "system", "content": system_prompt})
        
        messages.append({"role": "user", "content": prompt})

        try:
            completion = self.client.chat.completions.create(
                model=self.model_name,
                messages=messages,
                temperature=kwargs.get("temperature", 0.7),
                max_tokens=kwargs.get("max_tokens", 1024),
                top_p=kwargs.get("top_p", 1),
                stream=False,
                stop=None,
            )
            return completion.choices[0].message.content
        except Exception as e:
            print(f"[GroqClient][Error] 텍스트 생성 실패: {e}")
            return ""

    def get_health(self) -> bool:
        # Groq SDK에는 명시적인 헬스 체크가 없으므로 간단한 모델 리스트 조회 등으로 대체 가능
        return True