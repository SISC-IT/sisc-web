# AI/libs/llm/base_client.py
"""
Shared base interface for all LLM clients.
"""

from abc import ABC, abstractmethod
from typing import Any, Optional


class BaseLLMClient(ABC):
    """Abstract base class for all LLM clients."""

    def __init__(self, api_key: Optional[str] = None, model_name: str = "default"):
        self.api_key = api_key
        self.model_name = model_name
        self.last_error: Optional[str] = None

    def clear_last_error(self) -> None:
        self.last_error = None

    def set_last_error(self, error: Any) -> None:
        if isinstance(error, Exception):
            self.last_error = str(error) or error.__class__.__name__
            return
        self.last_error = str(error)

    @abstractmethod
    def generate_text(self, prompt: str, system_prompt: Optional[str] = None, **kwargs) -> str:
        """Generate text from prompt."""
        raise NotImplementedError

    @abstractmethod
    def get_health(self) -> bool:
        """Return whether the LLM service is available."""
        raise NotImplementedError
