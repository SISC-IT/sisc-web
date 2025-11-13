# finder/run_finder.py
import csv
import sys
import os
import time
import requests
import pandas as pd

# ---- 경로 세팅 ----
project_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.append(project_root)

from libs.utils import news_processing
from finder import ticker_selector
from libs.llm_clients.ollama_client import get_ollama_client  # ← 분리된 유틸 임포트

def run_finder():
    """
    전체 프로세스를 조율하여 최종 Top 3 투자 종목 반환
    """
    # --- 1단계: 의존성 객체 및 데이터 준비 ---
    try:
        llm = get_ollama_client()  # ✅ 헬스체크 및 모델 확인 포함
    except Exception as e:
        print(str(e))
        return []

    csv_path = os.path.join(project_root, "data", "stability_score_2025.csv")

    try:
        stability_df = pd.read_csv(csv_path)
    except FileNotFoundError:
        print(f"오류: {csv_path} 파일을 찾을 수 없습니다.")
        return []

    # --- 2단계: 주간 뉴스 데이터 수집 및 요약 ---
    try:
        weekly_news_df = news_processing.get_weekly_news_summary(days=5, llm_client=llm)
    except requests.exceptions.ConnectionError as e:
        print(f"[LLM 연결 오류] 뉴스 요약 단계에서 LLM 서버 연결 실패: {e}")
        return []
    except requests.exceptions.Timeout as e:
        print(f"[LLM 타임아웃] 뉴스 요약 단계에서 응답 지연: {e}")
        return []
    except Exception as e:
        print(f"[예기치 못한 오류] 뉴스 요약 단계: {e}")
        return []

    if weekly_news_df is None or getattr(weekly_news_df, "empty", False):
        print("분석할 뉴스 데이터가 없어 프로세스를 종료합니다.")
        return []

    # --- 3단계: 뉴스 데이터와 재무 데이터를 기반으로 Top 3 종목 선정 ---
    try:
        top_3_tickers = ticker_selector.select_top_stocks(
            news_summary_df=weekly_news_df,
            stability_df=stability_df,
            llm_client=llm
        )
    except requests.exceptions.ConnectionError as e:
        print(f"[LLM 연결 오류] 종목 선정 단계에서 LLM 서버 연결 실패: {e}")
        return []
    except requests.exceptions.Timeout as e:
        print(f"[LLM 타임아웃] 종목 선정 단계에서 응답 지연: {e}")
        return []
    except Exception as e:
        print(f"[예기치 못한 오류] 종목 선정 단계: {e}")
        return []

    print("\n🎉 [Finder 모듈 최종 결과] 투자 추천 Top 3 종목 🎉")
    print(top_3_tickers)
    return top_3_tickers

if __name__ == '__main__':
    run_finder()
