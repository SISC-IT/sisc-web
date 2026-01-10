# AI/modules/finder/evaluator.py
"""
[종목 평가 모듈]
- 선정된 종목들의 재무 건전성(Fundamental) 또는 기술적 상태(Technical)를 평가합니다.
- 점수(Score)를 매겨 최종 투자 유망 종목을 선정하는 데 도움을 줍니다.
"""

import sys
import os
import pandas as pd
from typing import Dict

# 프로젝트 루트 경로 추가
current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../../.."))
if project_root not in sys.path:
    sys.path.append(project_root)

from AI.libs.database.fetcher import fetch_ohlcv
from AI.modules.signal.core.features import add_technical_indicators

def evaluate_ticker(ticker: str) -> Dict[str, float]:
    """
    특정 종목의 기술적 점수를 계산합니다. (펀더멘털 데이터 부재 시 대안)
    
    Returns:
        Dict: {'trend_score': 0.8, 'volatility': 0.02, ...}
    """
    # 최근 100일 데이터 조회
    df = fetch_ohlcv(ticker, start="2023-01-01", end="2099-12-31")
    
    if df.empty or len(df) < 60:
        return {'score': 0.0, 'reason': '데이터 부족'}
        
    # 기술적 지표 추가
    df = add_technical_indicators(df)
    last_row = df.iloc[-1]
    
    score = 0.0
    
    # 1. 정배열 점수 (이동평균선)
    if last_row['close'] > last_row['ma20'] > last_row['ma60']:
        score += 30
    elif last_row['close'] > last_row['ma20']:
        score += 10
        
    # 2. RSI 점수 (과매도 구간이면 가점)
    rsi = last_row['rsi']
    if rsi <= 30:
        score += 20
    elif 30 < rsi < 70:
        score += 10
    # 70 이상(과매수)은 0점
    
    # 3. MACD 모멘텀
    if last_row['macd'] > last_row['signal_line']:
        score += 20
        
    # 4. 거래량 분석 (거래량 급증 시 가점)
    if last_row['volume'] > df['volume'].mean() * 1.5:
        score += 30
        
    return {
        'total_score': score,
        'rsi': rsi,
        'trend': 'Bull' if score > 50 else 'Bear'
    }