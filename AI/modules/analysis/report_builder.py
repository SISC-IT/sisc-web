# AI/modules/analysis/report_builder.py
"""
[리포트 빌더]
- 기술적 지표 수치(비율/정규화 값)를 입력받아, LLM이 이해하기 쉬운 문맥(Context) 정보로 변환합니다.
- 예: RSI 75 -> "과매수 상태이므로 조정 가능성 있음"
- 이 모듈을 통해 LLM의 할루시네이션을 줄이고 분석의 정확도를 높입니다.
"""

from typing import Dict, Any

class ReportBuilder:
    @staticmethod
    def analyze_indicators(data: Dict[str, float]) -> Dict[str, str]:
        """
        지표별 상태 해석 로직 (실제 데이터의 ratio 및 position 값 기반으로 해석)
        """
        analysis = {}
        
        # 1. 이동평균선 (추세 판단)
        # generator.py에서 사용한 역산 방식(Inverse)을 그대로 적용하여 가격을 추정
        price = data.get("close", 0)
        ma5 = data.get("ma5_ratio", 1.0) * price if price > 0 else 0
        ma60 = data.get("ma60_ratio", 1.0) * price if price > 0 else 0
        
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
        # signal_line이 없으므로 macd_ratio(MACD 오실레이터 또는 히스토그램 비율)의 양/음수로 모멘텀 판단
        macd_ratio = data.get("macd_ratio", 0)
        if macd_ratio > 0:
            analysis["macd_status"] = "긍정적 모멘텀 (MACD 양수/상승 우위)"
        elif macd_ratio < 0:
            analysis["macd_status"] = "부정적 모멘텀 (MACD 음수/하락 우위)"
        else:
            analysis["macd_status"] = "모멘텀 중립"

        # 4. 볼린저 밴드 (변동성)
        # bb_position: 일반적으로 1.0 이상이면 상단 돌파, 0.0 이하면 하단 이탈, 0~1 사이면 밴드 내
        bb_pos = data.get("bb_position", 0.5)
        
        if bb_pos >= 1.0:
            analysis["bb_status"] = "상단 밴드 돌파 (과열 또는 강한 돌파)"
        elif bb_pos <= 0.0:
            analysis["bb_status"] = "하단 밴드 이탈 (과매도 또는 강한 하락)"
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