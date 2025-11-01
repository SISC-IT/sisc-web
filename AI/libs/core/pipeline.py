import os
import sys
from typing import List, Dict
import json
from datetime import datetime, timedelta
import pandas as pd

# --- 프로젝트 루트 경로 설정 ---
project_root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.append(project_root)
# ------------------------------

# --- 모듈 import ---
from finder.main import run_finder
from transformer.main import run_transformer
from libs.utils.fetch_ohlcv import fetch_ohlcv
from xai.run_xai import run_xai
from libs.utils.get_db_conn import get_db_conn
# ---------------------------------

def run_weekly_finder() -> List[str]:
    """
    주간 종목 발굴(Finder)을 실행하고 결과(종목 리스트)를 반환합니다.
    """
    print("--- [PIPELINE-STEP 1] Finder 모듈 실행 시작 ---")
    #top_tickers = run_finder()
    top_tickers = ['AAPL', 'MSFT', 'GOOGL'] # 임시 데이터
    print(f"--- [PIPELINE-STEP 1] Finder 모듈 실행 완료 ---")
    return top_tickers

def run_signal_transformer(tickers: List[str], config: Dict) -> pd.DataFrame:
    """
    종목 리스트를 받아 Transformer 모듈을 실행하고, 신호(결정 로그)를 반환합니다.
    """
    try:
        with open(os.path.join(project_root, 'configs', 'config.json'), 'r') as f:
            config = json.load(f)
    except FileNotFoundError:
        print("Config file not found")
    except json.JSONDecodeError:
        print("Invalid JSON format in config file")
    db_config = (config or {}).get("db", {})   # ★ db 섹션만 추출
    print("--- [PIPELINE-STEP 2] Transformer 모듈 실행 시작 ---")
    
    # --- 실제 Transformer 모듈 호출 ---
    end_date = datetime.now()
    start_date = end_date - timedelta(days=600)
    all_ohlcv_df = []
    for ticker in tickers:
        ohlcv_df = fetch_ohlcv(
            ticker=ticker,
            start=start_date.strftime('%Y-%m-%d'),
            end=end_date.strftime('%Y-%m-%d'),
            config=db_config
        )
        ohlcv_df['ticker'] = ticker
        all_ohlcv_df.append(ohlcv_df)
    if not all_ohlcv_df:
        print("OHLCV 데이터를 가져오지 못했습니다.")
        return pd.DataFrame()
    raw_data = pd.concat(all_ohlcv_df, ignore_index=True)
    finder_df = pd.DataFrame(tickers, columns=['ticker'])
    transformer_result = run_transformer(
        finder_df=finder_df,
        seq_len=60,
        pred_h=1,
        raw_data=raw_data,
        config=config
    )
    logs_df = transformer_result.get("logs", pd.DataFrame())

    # --- 임시 결정 로그 데이터 (주석 처리) ---
    # data = {
    #     'ticker': ['AAPL', 'GOOGL', 'MSFT'],
    #     'date': ['2025-09-17', '2025-09-17', '2025-09-17'],
    #     'action': ['SELL', 'BUY', 'SELL'],
    #     'price': [238.99, 249.52, 510.01],
    #     'weight': [0.16, 0.14, 0.15],
    #     'feature1': ['RSI', 'Stochastic', 'MACD'],
    #     'feature2': ['MACD', 'MA_5', 'ATR'],
    #     'feature3': ['Bollinger_Bands_lower', 'RSI', 'MA_200'],
    #     'prob1': [0.5, 0.4, 0.6],
    #     'prob2': [0.3, 0.25, 0.2],
    #     'prob3': [0.1, 0.15, 0.1]
    # }
    # logs_df = pd.DataFrame(data)
    
    print(f"--- [PIPELINE-STEP 2] Transformer 모듈 실행 완료 ---")
    return logs_df

def run_xai_report(decision_log: pd.DataFrame) -> List[str]:
    """
    결정 로그를 바탕으로 실제 XAI 리포트를 생성합니다.
    """
    print("--- [PIPELINE-STEP 3] XAI 리포트 생성 시작 ---")
    api_key = os.environ.get("GROQ_API_KEY")
    if not api_key:
        raise ValueError("XAI 리포트 생성을 위해 GROQ_API_KEY 환경 변수를 설정해주세요.")
    reports = []
    if decision_log.empty:
        return reports
    for _, row in decision_log.iterrows():
        decision = {
            "ticker": row['ticker'],
            "date": row['date'],
            "signal": row['action'],
            "price": row['price'],
            "evidence": [
                {"feature_name": row['feature1'], "contribution": row['prob1']},
                {"feature_name": row['feature2'], "contribution": row['prob2']},
                {"feature_name": row['feature3'], "contribution": row['prob3']},
            ]
        }
        try:
            report = run_xai(decision, api_key)
            reports.append(report)
            print(f"--- {row['ticker']} XAI 리포트 생성 완료 ---")
        except Exception as e:
            error_message = f"--- {row['ticker']} XAI 리포트 생성 중 오류 발생: {e} ---"
            print(error_message)
            reports.append(error_message)
    print(f"--- [PIPELINE-STEP 3] XAI 리포트 생성 완료 ---")
    return reports

def save_reports_to_db(reports: List[str], config: Dict):
    """
    생성된 XAI 리포트를 데이터베이스에 저장합니다.
    """
    db_config = config.get("report_DB", {})
    conn = get_db_conn(db_config)
    cursor = conn.cursor()
    insert_query = """
        INSERT INTO xai_reports (report_text, created_at)
        VALUES (%s, %s);
    """
    for report in reports:
        cursor.execute(insert_query, (report, datetime.now()))
    conn.commit()
    cursor.close()
    conn.close()
    print(f"--- {len(reports)}개의 XAI 리포트가 데이터베이스에 저장되었습니다. ---")

# --- 전체 파이프라인 실행 ---
def run_pipeline():
    """
    전체 파이프라인(Finder -> Transformer -> XAI)을 실행합니다.
    """
    #--- 설정 파일 로드 ---
    config : Dict = {}
    try:
        with open(os.path.join(project_root, 'configs', 'config.json'), 'r') as f:
            config = json.load(f)
    except FileNotFoundError:
        print("[WARN] configs/config.json 파일을 찾을 수 없어 DB 연결이 필요 없는 기능만 작동합니다.")

    #--- 파이프라인 단계별 실행 ---
    top_tickers = run_weekly_finder()
    if not top_tickers:
        print("Finder에서 종목을 찾지 못해 파이프라인을 중단합니다.")
        return None
    decision_log = run_signal_transformer(top_tickers, config)
    if decision_log.empty:
        print("Transformer에서 신호를 생성하지 못해 파이프라인을 중단합니다.")
        return None
    xai_reports = run_xai_report(decision_log)
    
    save_reports_to_db(xai_reports, config)

    return xai_reports


# --- 테스트를 위한 실행 코드 ---
if __name__ == "__main__":
    print(">>> 파이프라인 (Finder -> Transformer -> XAI) 테스트를 시작합니다.")
    final_reports = run_pipeline()
    print("\n>>> 최종 반환 결과 (XAI Reports):")
    if final_reports:
        for report in final_reports:
            print(report)
    else:
        print("생성된 리포트가 없습니다.")
    print("\n---")
    print("테스트가 정상적으로 완료되었다면, 위 '최종 반환 결과'에 각 종목에 대한 XAI 리포트가 출력되어야 합니다.")
    print("---")
