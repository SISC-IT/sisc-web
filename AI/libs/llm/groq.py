# AI/libs/llm/groq.py
"""
Groq client implementation.
"""

import os
from typing import Optional

from groq import Groq

from .base_client import BaseLLMClient


class GroqClient(BaseLLMClient):
    def __init__(self, api_key: Optional[str] = None, model_name: str = "llama-3.3-70b-versatile"):
        key = api_key or os.environ.get("GROQ_API_KEY")
        if not key:
            raise ValueError("Groq API key is not configured.")

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
            text = completion.choices[0].message.content or ""
            self.clear_last_error()
            return text
        except Exception as e:
            self.set_last_error(e)
            print(f"[GroqClient][Error] Text generation failed: {self.last_error}")
            return ""

    def get_health(self) -> bool:
        return True
