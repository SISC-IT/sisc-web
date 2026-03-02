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
    def __init__(self, use_api_llm: bool = True):
        # Groq(빠름/고성능) 또는 Ollama(로컬) 선택
        if use_api_llm:
            self.llm = GroqClient(model_name="llama-3.3-70b-versatile")
        else:
            self.llm = OllamaClient(model_name="llama3")

    def generate_report(self, ticker: str, date: str, row_data: Dict[str, Any], ai_score: float, action: str) -> str:
        """
        단일 종목에 대한 분석 리포트 생성
        """
        if not self.llm.get_health():
            return "LLM 서비스 상태가 좋지 않아 리포트를 생성할 수 없습니다."

        # 1. 데이터 해석 (ReportBuilder 사용 - 내부적으로 변수명 동기화 필요 시 점검)
        try:
            analysis_context = ReportBuilder.analyze_indicators(row_data)
        except Exception as e:
            analysis_context = {"trend": "분석불가", "rsi_status": "알수없음", "macd_status": "알수없음", "bb_status": "알수없음"}
            print(f"[Warning] ReportBuilder 지표 분석 실패: {e}")

        # 2. 프롬프트 데이터 구성
        # [수정] 실제 add_technical_indicators가 생성하는 컬럼명으로 매핑 (없으면 기본값)
        prompt_data = {
            "ticker": ticker,
            "date": date,
            "price": row_data.get("close", 0),
            "ma5": row_data.get("ma5_ratio", 0) * row_data.get("close", 1), # 비율(ratio)에서 추정값으로 복원
            "ma60": row_data.get("ma60_ratio", 0) * row_data.get("close", 1),
            "rsi": row_data.get("rsi", 50),
            "macd": row_data.get("macd_ratio", 0), # 실제로는 MACD Ratio 값
            "signal_line": 0, # signal_line은 지표 생성기에 없으므로 0 처리
            "upper": row_data.get("close", 0) * (1 + row_data.get("bb_position", 0)), # 임의 복원
            "lower": row_data.get("close", 0) * (1 - row_data.get("bb_position", 0)),
            "score": ai_score * 100,
            "action": action
        }
        
        # 기본 템플릿에 해석 정보를 덧붙임
        try:
            base_prompt = USER_PROMPT_TEMPLATE.format(**prompt_data)
        except KeyError as e:
            # 템플릿에 필요한 변수가 부족할 때 방어 로직
            base_prompt = f"다음 주식의 매매 신호를 분석해 주세요.\n종목: {ticker}, 날짜: {date}, 가격: {prompt_data['price']}, 신호: {action}, AI 스코어: {prompt_data['score']:.1f}"
        
        enhanced_prompt = f"""
        {base_prompt}

        [참고: 기술적 지표 해석 가이드]
        - 추세: {analysis_context.get('trend', '중립')}
        - RSI 상태: {analysis_context.get('rsi_status', '중립')}
        - MACD 상태: {analysis_context.get('macd_status', '중립')}
        - 볼린저 밴드: {analysis_context.get('bb_status', '중립')}
        
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