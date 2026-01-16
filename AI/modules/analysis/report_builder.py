# AI/modules/analysis/report_builder.py
"""
[리포트 빌더]
- 기술적 지표 수치를 입력받아, LLM이 이해하기 쉬운 문맥(Context) 정보로 변환합니다.
- 예: RSI 75 -> "과매수 상태이므로 조정 가능성 있음"
- 이 모듈을 통해 LLM의 할루시네이션을 줄이고 분석의 정확도를 높입니다.
"""

from typing import Dict, Any

class ReportBuilder:
    @staticmethod
    def analyze_indicators(data: Dict[str, float]) -> Dict[str, str]:
        """
        지표별 상태 해석 로직
        """
        analysis = {}
        
        # 1. 이동평균선 (추세 판단)
        price = data.get("close", 0)
        ma5 = data.get("ma5", 0)
        ma60 = data.get("ma60", 0)
        
        if ma5 > ma60:
            if price > ma5:
                analysis["trend"] = "강한 상승 추세 (정배열)"
            else:
                analysis["trend"] = "상승 추세 중 일시 조정"
        else:
            if price < ma5:
                analysis["trend"] = "강한 하락 추세 (역배열)"
            else:
                analysis["trend"] = "하락 추세 중 기술적 반등 시도"

        # 2. RSI (과열/침체)
        rsi = data.get("rsi", 50)
        if rsi >= 70:
            analysis["rsi_status"] = "과매수 구간 (매물 출회 주의)"
        elif rsi <= 30:
            analysis["rsi_status"] = "과매도 구간 (저가 매수 유효)"
        else:
            analysis["rsi_status"] = "중립 구간"

        # 3. MACD (모멘텀)
        macd = data.get("macd", 0)
        signal = data.get("signal_line", 0)
        if macd > signal:
            analysis["macd_status"] = "매수 신호 (MACD > Signal)"
        else:
            analysis["macd_status"] = "매도 신호 (MACD < Signal)"

        # 4. 볼린저 밴드 (변동성)
        upper = data.get("upper_band", 0)
        lower = data.get("lower_band", 0)
        
        if price >= upper:
            analysis["bb_status"] = "상단 밴드 돌파 (추가 상승 또는 조정 갈림길)"
        elif price <= lower:
            analysis["bb_status"] = "하단 밴드 이탈 (반등 기대)"
        else:
            analysis["bb_status"] = "밴드 내 등락 (안정적 흐름)"

        return analysis

    @staticmethod
    def build_context_string(ticker: str, ai_score: float, action: str, data: Dict[str, float]) -> str:
        """
        LLM에게 전달할 최종 분석 요약 문자열 생성
        """
        interpretations = ReportBuilder.analyze_indicators(data)
        
        context = f"""
        [상세 분석 데이터]
        1. 추세: {interpretations.get('trend')}
        2. 모멘텀(RSI): {interpretations.get('rsi_status')} (RSI: {data.get('rsi', 0):.2f})
        3. 신호(MACD): {interpretations.get('macd_status')}
        4. 변동성(Bollinger): {interpretations.get('bb_status')}
        
        [AI 모델 판단]
        - 상승 예측 확률: {ai_score*100:.1f}%
        - 제안 포지션: {action}
        """
        return context