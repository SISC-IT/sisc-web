# AI/libs/llm/gemini.py
"""
Google Gemini client implementation.
"""

import os
from typing import Optional

from google import genai
from google.genai import types

from .base_client import BaseLLMClient

DEFAULT_GEMINI_MODEL = os.environ.get("GEMINI_MODEL", "gemini-2.0-flash")


class GeminiClient(BaseLLMClient):
    def __init__(self, api_key: Optional[str] = None, model_name: str = DEFAULT_GEMINI_MODEL):
        key = api_key or os.environ.get("GEMINI_API_KEY")
        if not key:
            raise ValueError("Gemini API key is not configured. Set GEMINI_API_KEY.")

        super().__init__(api_key=key, model_name=model_name)
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
            raise RuntimeError(f"Gemini response blocked: {block_reason}")

        raise RuntimeError("Gemini response did not contain text.")

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
            text = self._extract_text(response)
            self.clear_last_error()
            return text
        except Exception as e:
            self.set_last_error(e)
            print(f"[GeminiClient][Error] Text generation failed: {self.last_error}")
            return ""

    def get_health(self) -> bool:
        try:
            models = self.client.models.list()
            target_names = {self.model_name, f"models/{self.model_name}"}
            for model in models:
                model_name = getattr(model, "name", "")
                if model_name in target_names:
                    self.clear_last_error()
                    return True
            self.set_last_error(f"Model not found: {self.model_name}")
            return False
        except Exception as e:
            self.set_last_error(e)
            print(f"[GeminiClient][Warning] Health check failed: {self.last_error}")
            return False
