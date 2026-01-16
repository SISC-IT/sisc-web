# AI/libs/llm/ollama.py
"""
[Ollama 클라이언트 구현체]
- 로컬 LLM 서버(Ollama)와 통신합니다.
- 기존 AI/libs/llm_clients/ollama_client.py 의 역할을 대체합니다.
"""

import requests
import json
from typing import Optional
from .base_client import BaseLLMClient

class OllamaClient(BaseLLMClient):
    def __init__(self, base_url: str = "http://localhost:11434", model_name: str = "llama3"):
        super().__init__(model_name=model_name)
        self.base_url = base_url

    def generate_text(self, prompt: str, system_prompt: Optional[str] = None, **kwargs) -> str:
        url = f"{self.base_url}/api/generate"
        
        # 시스템 프롬프트가 있다면 사용자 프롬프트 앞에 붙여서 전송 (Ollama API 버전에 따라 다름)
        full_prompt = prompt
        if system_prompt:
            full_prompt = f"System: {system_prompt}\n\nUser: {prompt}"

        payload = {
            "model": self.model_name,
            "prompt": full_prompt,
            "stream": False,
            "options": {
                "temperature": kwargs.get("temperature", 0.7)
            }
        }

        try:
            response = requests.post(url, json=payload)
            response.raise_for_status()
            result = response.json()
            return result.get("response", "")
        except Exception as e:
            print(f"[OllamaClient][Error] 텍스트 생성 실패: {e}")
            return ""

    def get_health(self) -> bool:
        try:
            res = requests.get(self.base_url, timeout=5)
            return res.status_code == 200
        except Exception:
             return False