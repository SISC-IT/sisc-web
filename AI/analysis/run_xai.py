from typing import Dict, Any
from .modules.generate import generate_report_from_yf
'''
https://console.groq.com/settings/limits
제한 문제 없음
Chat Completions
Model	                Requests per Minute	Requests per Day	Tokens per Minute	Tokens per Day
llama-3.3-70b-versatile	    30	                1K	                12K	                    100K

https://console.groq.com/keys
세투연 구글계정으로 로그인 후 api 키 생성. billing 설정 불필요
.env에 저장 후 사용

'''
"""
    decision 인자 예시
    decision = {
        "ticker": "AAPL",
        "date": "2024-12-16",
        "signal": "BUY",
        "price": 453.72,
        "evidence": [
            {"feature_name": "MA_5", "contribution": 0.0186},
            {"feature_name": "Bollinger_Bands_lower", "contribution": 0.4182},
            {"feature_name": "Stochastic", "contribution": 0.0507},
        ]
    }
"""
def run_xai(decision: Dict[str, Any], api_key: str) -> str:

    evidence = decision.pop("evidence", [])
    report = generate_report_from_yf(decision, evidence, api_key)
    return report
