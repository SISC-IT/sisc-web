import pandas as pd
from typing import List, Dict
import json
import os
from collections import defaultdict
from datetime import datetime

import warnings
warnings.filterwarnings("ignore", category=FutureWarning)

from langchain_community.llms import Ollama


# --- private 함수 ---

def _parse_summary(x):
    '''
    JSON 문자열을 dict로 변환해주는 함수
    '''
    if isinstance(x, str):
        try:
            return json.loads(x)
        except:
            return None
    elif isinstance(x, dict):
        return x
    return None

def _process_sentiment_and_confidence(row):
    '''
    Sentiment와 Confidence 열을 집계/처리하는 함수
    '''
    # Sentiment -> dict 변환
    sentiment_count = {'Positive': 0, 'Neutral': 0, 'Negative': 0}
    for s in row['Sentiment']:
        if s in sentiment_count:
            sentiment_count[s] += 1
    row['Sentiment'] = sentiment_count
    
    # Confidence -> 평균값
    conf_list = [c for c in row['Confidence'] if isinstance(c, (int, float))]
    if conf_list:
        row['Confidence'] = sum(conf_list) / len(conf_list)
    else:
        row['Confidence'] = None
    
    return row

def _get_top3_tickers_from_response(response: str) -> List[str]:
    '''
    LLM의 JSON 응답에서 ticker 리스트를 추출하는 함수
    '''
    try:
        data = json.loads(response)
        return [item['ticker'] for item in data.get('Top3', []) if 'ticker' in item]
    except (json.JSONDecodeError, TypeError, KeyError):
        return []


# --- public 함수 ---
def select_top_stocks(
    news_summary_df: pd.DataFrame, 
    stability_df: pd.DataFrame, 
    llm_client
) -> List[str]:
    '''
    뉴스 요약 데이터와 재무 안정성 데이터 기반 Top 3 투자 종목 선정

    Args:
        news_summary_df (pd.DataFrame): news_processing에서 반환된 주간 뉴스 요약 데이터
        stability_df (pd.DataFrame): 재무 안정성 점수 데이터
        llm_client: Langchain LLM 클라이언트 객체

    Returns:
        List[str]: 최종 선정된 Top 3 종목의 ticker 리스트
    '''
    print("--- Top 3 종목 선정 시작 ---")

    # 1. 뉴스 요약 데이터 파싱 및 종목별 집계
    df = news_summary_df.copy()
    df['parsed'] = df['summary'].apply(_parse_summary)

    result = defaultdict(lambda: defaultdict(list))
    for item in df['parsed'].dropna():
        try:
            stock = item.get("Stock").upper()
        except:
            continue
        
        if not stock:
            continue
        if not item.get("Event"):
            continue
        if not item.get("Confidence"):
            continue
        if not item.get("Factor"):
            continue
        if not item.get("Reason"):
            continue
        if not item.get("Sentiment"):
            continue

        confidence = item.get("Confidence")
        try:
            if float(confidence) >= 0.5:
                for key in ["Event", "Factor", "Reason", "Sentiment", "Confidence"]:
                    if item.get(key):
                        result[stock][key].append(item.get(key))
        except (ValueError, TypeError):
            continue
            
    result_df = pd.DataFrame.from_dict(result, orient='index').reset_index().rename(columns={'index': 'ticker'})

    if result_df.empty:
        print("분석할 유효한 뉴스 데이터가 없습니다.")
        return []

    # 2. 재무제표 기반 안정성 필터링
    good_tickers = stability_df[stability_df['stability_score'] > stability_df['stability_score'].mean()]['ticker']
    filter_df = pd.merge(result_df, pd.DataFrame(good_tickers), on='ticker')

    if filter_df.empty:
        print("재무 안정성 기준을 통과한 종목이 없습니다.")
        return []
    
    # 3. 뉴스 요약 최종 전처리
    df_processed = filter_df.apply(_process_sentiment_and_confidence, axis=1)
    df_processed.dropna(subset=['Confidence'], inplace=True)

    # 4. LLM을 이용한 최종 Top 3 선정
    prompt = f"""You are a professional investment analyst. Analyze the stock data below and recommend the top three stocks for investment.

Data:
{df_processed.to_dict(orient='records')}

Requests:
- Higher scores are awarded for higher positive sentiment and higher confidence.
- Bonus points are given for short-term positive factors (events, factors, reasons).
- Select only your top three stocks and provide a final score and brief explanation for each.

Please respond in English and No output other than the output format is required. Summarize output format(JSON):
{{
 "Top3": [
   {{"ticker": "", "score": "", "reason": ""}},
   {{"ticker": "", "score": "", "reason": ""}},
   {{"ticker": "", "score": "", "reason": ""}}
 ]
}}
"""
    response = llm_client.invoke(prompt)
    
    # 5. LLM 응답에서 Ticker만 추출하여 반환
    top_3_tickers = _get_top3_tickers_from_response(response)
    print(f"--- 최종 Top 3 종목 선정 완료: {top_3_tickers} ---")

    return top_3_tickers


# --- 테스트 코드 ---
if __name__ == '__main__':
    print("--- ticker_selector.py 테스트 모드 ---")
    
    folder_path = 'data/input_data'
    file_list = os.listdir(folder_path)

    all_dataframes = []
    for file in file_list:
        if file.endswith('.csv'):
            df = pd.read_csv('data/input_data' + '/' + file)
            all_dataframes.append(df)

    news_summary = pd.concat(all_dataframes, ignore_index=True)

    stability_score = pd.read_csv('data/stability_score_2025.csv')

    llm = Ollama(model="llama3.2")
    
    top_stocks = select_top_stocks(
        news_summary_df=news_summary,
        stability_df=stability_score,
        llm_client=llm
    )
    
    print("\n[최종 테스트 결과]")
    print(top_stocks)