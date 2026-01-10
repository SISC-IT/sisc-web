# AI/modules/analysis/generator.py
"""
[리포트 생성기]
- LLM 클라이언트를 사용하여 시장 데이터에 대한 분석 리포트를 생성합니다.
- ReportBuilder를 통해 데이터를 해석한 뒤 프롬프트를 구성합니다.
"""

import sys
import os
from typing import Dict, Any

# 프로젝트 루트 경로 추가
current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../../.."))
if project_root not in sys.path:
    sys.path.append(project_root)

from AI.libs.llm import GroqClient, OllamaClient
from AI.modules.analysis.prompt import SYSTEM_PROMPT, USER_PROMPT_TEMPLATE
from AI.modules.analysis.report_builder import ReportBuilder

class ReportGenerator:
    def __init__(self, use_gpu_llm: bool = True):
        # Groq(빠름/고성능) 또는 Ollama(로컬) 선택
        if use_gpu_llm:
            self.llm = GroqClient(model_name="llama-3.3-70b-versatile")
        else:
            self.llm = OllamaClient(model_name="llama3")

    def generate_report(self, ticker: str, date: str, row_data: Dict[str, Any], ai_score: float, action: str) -> str:
        """
        단일 종목에 대한 분석 리포트 생성
        """
        if not self.llm.get_health():
            return "LLM 서비스 상태가 좋지 않아 리포트를 생성할 수 없습니다."

        # 1. 데이터 해석 (ReportBuilder 사용)
        analysis_context = ReportBuilder.analyze_indicators(row_data)

        # 2. 프롬프트 데이터 구성
        prompt_data = {
            "ticker": ticker,
            "date": date,
            "price": row_data.get("close", 0),
            "ma5": row_data.get("ma5", 0),
            "ma60": row_data.get("ma60", 0),
            "rsi": row_data.get("rsi", 50),
            "macd": row_data.get("macd", 0),
            "signal_line": row_data.get("signal_line", 0),
            "upper": row_data.get("upper_band", 0),
            "lower": row_data.get("lower_band", 0),
            "score": ai_score * 100,
            "action": action
        }
        
        # 기본 템플릿에 해석 정보를 덧붙임
        # prompt.py의 템플릿을 그대로 쓰되, 해석 힌트를 추가하고 싶다면 여기서 문자열 조작
        base_prompt = USER_PROMPT_TEMPLATE.format(**prompt_data)
        
        enhanced_prompt = f"""
        {base_prompt}

        [참고: 기술적 지표 해석 가이드]
        - 추세: {analysis_context.get('trend')}
        - RSI 상태: {analysis_context.get('rsi_status')}
        - MACD 상태: {analysis_context.get('macd_status')}
        - 볼린저 밴드: {analysis_context.get('bb_status')}
        
        위 해석 가이드를 참고하여, 초보 투자자도 이해하기 쉬운 논리적인 문장으로 작성해주세요.
        """
        
        # 3. LLM 호출
        report = self.llm.generate_text(
            prompt=enhanced_prompt,
            system_prompt=SYSTEM_PROMPT,
            temperature=0.7,
            max_tokens=1024
        )
        
        return report