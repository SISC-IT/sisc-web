from typing import List, Dict, Any
from .prompt import build_short_prompt
from .groq_client import ask_groq
from .fetcher import fetch_context_data_from_yf

def generate_report(
    decision: Dict[str, Any],
    evidence: List[Dict[str, Any]],
    context_data: List[Dict[str, Any]],
    api_key: str,
) -> str:
    prompt = build_short_prompt(decision, evidence, context_data)
    return ask_groq(prompt, api_key)

def generate_report_from_yf(
    decision: Dict[str, Any],
    evidence: List[Dict[str, Any]],
    api_key: str,
    days: int = 400,
    window: int = 60,
) -> str:
    context_data = fetch_context_data_from_yf(decision["ticker"], days=days, window=window)
    return generate_report(decision, evidence, context_data, api_key)
