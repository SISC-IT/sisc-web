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
from datetime import datetime, timedelta

# 프로젝트 루트 경로 추가
current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../../.."))
if project_root not in sys.path:
    sys.path.append(project_root)

from AI.libs.database.fetcher import fetch_price_data
from AI.modules.signal.core.features import add_technical_indicators

def evaluate_ticker(ticker: str) -> Dict[str, float]:
    """
    특정 종목의 기술적 점수를 계산합니다.
    """
    # [수정] 5년치 데이터 로드 (장기 추세 파악용)
    end_dt = datetime.now()
    start_dt = end_dt - timedelta(days=365 * 5) # 5년 (약 1825일)
    
    start_str = start_dt.strftime("%Y-%m-%d")
    end_str = end_dt.strftime("%Y-%m-%d")

    df = fetch_price_data(ticker, start_date=start_str, end_date=end_str)
    
    if df.empty or len(df) < 60:
        return {'total_score': 0.0, 'reason': '데이터 부족'}
        
    # 기술적 지표 추가
    df = add_technical_indicators(df)
    last_row = df.iloc[-1]
    
    score = 0.0
    
    # 1. 정배열 점수
    if last_row['close'] > last_row['ma20'] > last_row['ma60']:
        score += 30
    elif last_row['close'] > last_row['ma20']:
        score += 10
        
    # 2. RSI 점수
    rsi = last_row['rsi']
    if rsi <= 30:
        score += 20
    elif 30 < rsi < 70:
        score += 10
    
    # 3. MACD 모멘텀
    if last_row['macd'] > last_row['signal_line']:
        score += 20
        
    # 4. 거래량 분석 (스마트하게 수정됨)
    # 5년치 평균을 쓰면 옛날 데이터 때문에 왜곡될 수 있으므로, 
    # '최근 120일(약 6개월)' 평균 거래량과 비교합니다.
    recent_vol_mean = df['volume'].tail(120).mean()
    
    if recent_vol_mean > 0 and last_row['volume'] > recent_vol_mean * 1.5:
        score += 30
        
    return {
        'total_score': score,
        'rsi': rsi,
        'trend': 'Bull' if score > 50 else 'Bear'
    }