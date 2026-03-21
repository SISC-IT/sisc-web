# AI/libs/llm/gemini.py
"""
[Google Gemini 클라이언트 구현체]
- Google Generative AI API를 사용하여 텍스트 생성을 수행합니다.
- 넉넉한 무료 티어와 뛰어난 한국어 성능을 활용합니다.
"""

import os
import google.generativeai as genai
from typing import Optional
from .base_client import BaseLLMClient

class GeminiClient(BaseLLMClient):
    def __init__(self, api_key: Optional[str] = None, model_name: str = "gemini-1.5-flash"):
        # 환경변수 우선, 없으면 인자값 사용
        key = api_key or os.environ.get("GEMINI_API_KEY")
        if not key:
            raise ValueError("Gemini API Key가 설정되지 않았습니다. 'GEMINI_API_KEY' 환경변수를 등록해주세요.")
            
        super().__init__(api_key=key, model_name=model_name)
        
        # Gemini API 초기화
        genai.configure(api_key=key)
        self.model = genai.GenerativeModel(self.model_name)

    def generate_text(self, prompt: str, system_prompt: Optional[str] = None, **kwargs) -> str:
        try:
            # system_prompt가 있을 경우 본문 프롬프트와 합치거나, 
            # 모델 초기화 시 system_instruction으로 전달할 수 있습니다.
            # 여기서는 범용성을 위해 프롬프트 텍스트에 조합하는 방식을 사용합니다.
            full_prompt = prompt
            if system_prompt:
                full_prompt = f"System: {system_prompt}\n\nUser: {prompt}"

            # 추가 설정 파라미터 (temperature 등)
            generation_config = genai.GenerationConfig(
                temperature=kwargs.get("temperature", 0.7),
                max_output_tokens=kwargs.get("max_tokens", 1024),
                top_p=kwargs.get("top_p", 1.0),
            )

            response = self.model.generate_content(
                full_prompt,
                generation_config=generation_config
            )
            return response.text
            
        except Exception as e:
            print(f"[GeminiClient][Error] 텍스트 생성 실패: {e}")
            return "리포트 생성 중 오류가 발생했습니다."

    def get_health(self) -> bool:
        # 간단한 모델 목록 조회를 통해 API 키 및 서비스 상태 체크
        try:
            models = genai.list_models()
            return any(self.model_name in m.name for m in models)
        except:
            return False