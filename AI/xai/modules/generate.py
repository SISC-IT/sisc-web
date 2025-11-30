# AI/xai/modules/generate.py

from typing import List, Dict, Any
from .groq_client import ask_groq
from .fetcher import fetch_context_data_from_yf


def build_payload(
    decision: Dict[str, Any],
    evidence: List[Dict[str, Any]],
    context_data: List[Dict[str, Any]],
) -> Dict[str, Any]:
    """
    XAI 생성용 dict payload 생성.

    decision 구조 예:
        {
            "ticker": "AAPL",
            "date": "2024-01-10",
            "signal": "BUY",
            "price": 187.12
        }

    evidence 구조 예:
        [
            {"feature_name": "...", "contribution": 0.23},
            ...
        ]

    context_data: YF 또는 사전 수집된 맥락 데이터
    """

    payload = {
        "ticker": decision.get("ticker"),
        "date": decision.get("date"),
        "signal": decision.get("signal"),
        "price": decision.get("price"),
        "evidence": evidence,
        "context": context_data,
    }
    return payload


def generate_report(
    decision: Dict[str, Any],
    evidence: List[Dict[str, Any]],
    context_data: List[Dict[str, Any]],
    api_key: str,
) -> str:
    """
    XAI 리포트 생성 (dict 기반 ask_groq 전용)
    """

    payload = build_payload(decision, evidence, context_data)
    result = ask_groq(payload, api_key)
    return result


def generate_report_from_yf(
    decision: Dict[str, Any],
    evidence: List[Dict[str, Any]],
    api_key: str,
    days: int = 400,
    window: int = 60,
) -> str:
    """
    YFinance 기반 컨텍스트 데이터를 자동 수집하여 리포트 생성
    """

    context_data = fetch_context_data_from_yf(
        decision["ticker"],
        days=days,
        window=window
    )

    return generate_report(decision, evidence, context_data, api_key)
