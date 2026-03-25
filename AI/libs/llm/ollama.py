# AI/libs/llm/ollama.py
"""
Ollama local client implementation.
"""

import os
from typing import Optional

import requests

from .base_client import BaseLLMClient


class OllamaClient(BaseLLMClient):
    def __init__(
        self,
        base_url: str = "http://localhost:11434",
        model_name: str = os.environ.get("OLLAMA_MODEL", "llama3-ko"),
    ):
        super().__init__(model_name=model_name)
        self.base_url = base_url

    def generate_text(self, prompt: str, system_prompt: Optional[str] = None, **kwargs) -> str:
        url = f"{self.base_url}/api/generate"
        full_prompt = prompt
        if system_prompt:
            full_prompt = f"System: {system_prompt}\n\nUser: {prompt}"

        payload = {
            "model": self.model_name,
            "prompt": full_prompt,
            "stream": False,
            "options": {
                "temperature": kwargs.get("temperature", 0.7),
            },
        }

        try:
            response = requests.post(url, json=payload)
            response.raise_for_status()
            result = response.json()
            text = result.get("response", "")
            self.clear_last_error()
            return text
        except Exception as e:
            self.set_last_error(e)
            print(f"[OllamaClient][Error] Text generation failed: {self.last_error}")
            return ""

    def get_health(self) -> bool:
        try:
            res = requests.get(self.base_url, timeout=5)
            return res.status_code == 200
        except Exception as e:
            self.set_last_error(e)
            return False
