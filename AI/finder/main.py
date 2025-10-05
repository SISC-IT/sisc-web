import sys
import os

from libs.utils import news_processing
from finder import ticker_selector
import pandas as pd
from langchain_community.llms import Ollama


project_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.append(project_root)


def run_finder():
    '''
    전체 프로세스를 조율하여 최종 Top 3 투자 종목 반환
    '''
    # --- 1단계: 의존성 객체 및 데이터 준비 ---
    llm = Ollama(model="llama3.2")
    
    try:
        stability_df = pd.read_csv('data/stability_score_2025.csv')
    except FileNotFoundError:
        print("오류: 'data/stability_score_2025.csv' 파일을 찾을 수 없습니다.")
        return []

    # --- 2단계: 주간 뉴스 데이터 수집 및 요약 ---
    weekly_news_df = news_processing.get_weekly_news_summary(days=5, llm_client=llm)

    if weekly_news_df.empty:
        print("분석할 뉴스 데이터가 없어 프로세스를 종료합니다.")
        return []

    # --- 3단계: 뉴스 데이터와 재무 데이터를 기반으로 Top 3 종목 선정 ---
    top_3_tickers = ticker_selector.select_top_stocks(
        news_summary_df=weekly_news_df,
        stability_df=stability_df,
        llm_client=llm
    )

    print("\n🎉 [Finder 모듈 최종 결과] 투자 추천 Top 3 종목 🎉")
    print(top_3_tickers)
    
    return top_3_tickers

if __name__ == '__main__':
    run_finder()