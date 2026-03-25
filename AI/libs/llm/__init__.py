# AI/libs/llm/__init__.py
from .base_client import BaseLLMClient
from .groq import GroqClient
from .ollama import OllamaClient

try:
    from .gemini import GeminiClient
except Exception as gemini_import_error:
    class GeminiClient:  # type: ignore[no-redef]
        def __init__(self, *args, **kwargs):
            raise ImportError(
                "GeminiClient requires the `google-genai` package. "
                "Install it with `pip install -U google-genai`."
            ) from gemini_import_error

__all__ = [
    "BaseLLMClient",
    "GroqClient",
    "OllamaClient",
    "GeminiClient"
]
