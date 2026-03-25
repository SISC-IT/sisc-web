# AI/modules/analysis/generator.py
"""
XAI report generator built on top of an LLM client.
"""

import os
import sys
from typing import Any, Dict


current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../../.."))
if project_root not in sys.path:
    sys.path.append(project_root)

from AI.modules.analysis.prompt import SYSTEM_PROMPT, USER_PROMPT_TEMPLATE
from AI.modules.analysis.report_builder import ReportBuilder


class ReportGenerator:
    def __init__(self, use_api_llm: bool = True):
        if use_api_llm:
            from AI.libs.llm.gemini import DEFAULT_GEMINI_MODEL, GeminiClient
            self.llm = GeminiClient(model_name=DEFAULT_GEMINI_MODEL)
        else:
            from AI.libs.llm.ollama import OllamaClient
            self.llm = OllamaClient()

        self._xai_disabled = False
        self._xai_disable_reason = ""
        self.max_output_tokens = int(os.environ.get("XAI_MAX_OUTPUT_TOKENS", "384"))

    @staticmethod
    def _looks_like_quota_error(message: str) -> bool:
        normalized = (message or "").lower()
        quota_markers = (
            "429",
            "resource_exhausted",
            "quota",
            "rate limit",
            "rate_limit",
            "too many requests",
            "insufficient_quota",
            "tokens per minute",
            "requests per minute",
            "billing",
            "exceeded",
            "blocked",
        )
        return any(marker in normalized for marker in quota_markers)

    def _disable_xai(self, reason: str) -> None:
        if self._xai_disabled:
            return
        self._xai_disabled = True
        self._xai_disable_reason = reason
        print(
            "[ReportGenerator][Warning] XAI disabled for this run due to quota/rate-limit issue: "
            f"{reason}"
        )

    def generate_report(
        self,
        ticker: str,
        date: str,
        row_data: Dict[str, Any],
        ai_score: float,
        action: str,
    ) -> str:
        if self._xai_disabled:
            return ""

        try:
            analysis_context = ReportBuilder.analyze_indicators(row_data)
        except Exception as e:
            analysis_context = {
                "trend": "N/A",
                "rsi_status": "N/A",
                "macd_status": "N/A",
                "bb_status": "N/A",
            }
            print(f"[ReportGenerator][Warning] indicator analysis failed: {e}")

        prompt_data = {
            "ticker": ticker,
            "date": date,
            "price": row_data.get("close", 0),
            "ma5": row_data.get("ma5_ratio", 0) * row_data.get("close", 1),
            "ma60": row_data.get("ma60_ratio", 0) * row_data.get("close", 1),
            "rsi": row_data.get("rsi", 50),
            "macd": row_data.get("macd_ratio", 0),
            "signal_line": 0,
            "upper": row_data.get("close", 0) * (1 + row_data.get("bb_position", 0)),
            "lower": row_data.get("close", 0) * (1 - row_data.get("bb_position", 0)),
            "score": ai_score * 100,
            "action": action,
        }

        try:
            base_prompt = USER_PROMPT_TEMPLATE.format(**prompt_data)
        except KeyError:
            base_prompt = (
                "Please explain this trade signal in simple language.\n"
                f"Ticker: {ticker}, Date: {date}, Price: {prompt_data['price']}, "
                f"Signal: {action}, AI Score: {prompt_data['score']:.1f}"
            )

        enhanced_prompt = f"""
{base_prompt}

[Indicator Context]
- Trend: {analysis_context.get('trend', 'neutral')}
- RSI: {analysis_context.get('rsi_status', 'neutral')}
- MACD: {analysis_context.get('macd_status', 'neutral')}
- Bollinger Band: {analysis_context.get('bb_status', 'neutral')}
"""

        report = self.llm.generate_text(
            prompt=enhanced_prompt,
            system_prompt=SYSTEM_PROMPT,
            temperature=0.7,
            max_tokens=self.max_output_tokens,
        )

        if report:
            return report

        last_error = getattr(self.llm, "last_error", "") or ""
        if last_error and self._looks_like_quota_error(last_error):
            self._disable_xai(last_error)
        else:
            print(
                f"[ReportGenerator][Warning] empty report (model={self.llm.model_name}, "
                f"ticker={ticker}, error={last_error or 'unknown'})"
            )

        return ""
