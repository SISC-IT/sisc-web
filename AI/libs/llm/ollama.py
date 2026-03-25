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
        model_name: Optional[str] = None,
    ):
        env_model_name = os.environ.get("OLLAMA_MODEL")
        resolved_model_name = model_name or env_model_name or "llama3:latest"
        super().__init__(model_name=resolved_model_name)
        self.base_url = base_url
        self._model_explicitly_set = bool(model_name or env_model_name)

    def _list_local_models(self) -> list[str]:
        try:
            response = requests.get(f"{self.base_url}/api/tags", timeout=5)
            response.raise_for_status()
            result = response.json()
            return [model.get("name", "") for model in result.get("models", []) if model.get("name")]
        except Exception:
            return []

    def _ensure_model_available(self) -> bool:
        local_models = self._list_local_models()
        if not local_models:
            self.set_last_error(
                "No local Ollama model found. Pull one first (e.g. `ollama pull llama3:latest`)."
            )
            return False

        if self.model_name in local_models:
            return True

        if self._model_explicitly_set:
            self.set_last_error(
                f"Model '{self.model_name}' is not installed. Installed models: {', '.join(local_models)}"
            )
            return False

        fallback_model = local_models[0]
        print(
            f"[OllamaClient][Warning] Default model '{self.model_name}' is unavailable. "
            f"Using '{fallback_model}' instead."
        )
        self.model_name = fallback_model
        return True

    def generate_text(self, prompt: str, system_prompt: Optional[str] = None, **kwargs) -> str:
        url = f"{self.base_url}/api/generate"

        if not self._ensure_model_available():
            print(f"[OllamaClient][Error] Text generation failed: {self.last_error}")
            return ""

        payload = {
            "model": self.model_name,
            "prompt": prompt,
            "stream": False,
            "options": {
                "temperature": kwargs.get("temperature", 0.7),
            },
        }
        if system_prompt:
            payload["system"] = system_prompt

        try:
            response = requests.post(url, json=payload, timeout=kwargs.get("timeout", 120))
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
            res = requests.get(f"{self.base_url}/api/tags", timeout=5)
            is_healthy = res.status_code == 200
            if is_healthy:
                self.clear_last_error()
            return is_healthy
        except Exception as e:
            self.set_last_error(e)
            return False
