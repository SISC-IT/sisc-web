# AI/libs/llm/gemini.py
"""
[Google Gemini 클라이언트 구현체]
- Google Generative AI API를 사용하여 텍스트 생성을 수행합니다.
- 넉넉한 무료 티어와 뛰어난 한국어 성능을 활용합니다.
"""

import os
from typing import Optional

from google import genai
from google.genai import types

from .base_client import BaseLLMClient

DEFAULT_GEMINI_MODEL = os.environ.get("GEMINI_MODEL", "gemini-2.0-flash")

class GeminiClient(BaseLLMClient):
    def __init__(self, api_key: Optional[str] = None, model_name: str = DEFAULT_GEMINI_MODEL):
        # 환경변수 우선, 없으면 인자값 사용
        key = api_key or os.environ.get("GEMINI_API_KEY")
        if not key:
            raise ValueError("Gemini API Key가 설정되지 않았습니다. 'GEMINI_API_KEY' 환경변수를 등록해주세요.")
        
        super().__init__(api_key=key, model_name=model_name)
        
        # 최신 Google GenAI SDK는 명시적인 Client 진입점을 사용합니다.
        self.client = genai.Client(api_key=key)

    def _extract_text(self, response) -> str:
        text = getattr(response, "text", None)
        if text:
            return text.strip()

        candidates = getattr(response, "candidates", None) or []
        for candidate in candidates:
            content = getattr(candidate, "content", None)
            parts = getattr(content, "parts", None) or []
            chunks = []
            for part in parts:
                part_text = getattr(part, "text", None)
                if part_text:
                    chunks.append(part_text)
            if chunks:
                return "".join(chunks).strip()

        prompt_feedback = getattr(response, "prompt_feedback", None)
        block_reason = getattr(prompt_feedback, "block_reason", None)
        if block_reason:
            raise RuntimeError(f"Gemini 응답이 차단되었습니다: {block_reason}")

        raise RuntimeError("Gemini 응답에서 텍스트를 찾을 수 없습니다.")

    def generate_text(self, prompt: str, system_prompt: Optional[str] = None, **kwargs) -> str:
        try:
            config = types.GenerateContentConfig(
                temperature=kwargs.get("temperature", 0.7),
                max_output_tokens=kwargs.get("max_tokens", 1024),
                top_p=kwargs.get("top_p", 1.0),
            )
            if system_prompt:
                config.system_instruction = system_prompt

            response = self.client.models.generate_content(
                model=self.model_name,
                contents=prompt,
                config=config,
            )
            return self._extract_text(response)
            
        except Exception as e:
            print(f"[GeminiClient][Error] 텍스트 생성 실패: {e}")
            return ""

    def get_health(self) -> bool:
        # 모델 목록 조회를 통해 API 키 및 모델 접근 가능 여부를 점검합니다.
        try:
            models = self.client.models.list()
            target_names = {self.model_name, f"models/{self.model_name}"}
            for model in models:
                model_name = getattr(model, "name", "")
                if model_name in target_names:
                    return True
            return False
        except Exception as e:
            print(f"[GeminiClient][Warning] health check 실패: {e}")
            return False
