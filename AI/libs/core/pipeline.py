import os
import json
import datetime as dt
import pandas as pd
from typing import Dict

# ==============================================
# 내부 모듈 (이미 구현돼 있다고 가정)
# ==============================================
from finder.modules.finder import run_finder_with_scores  # 종목+점수 매기기 포함
from transform.modules.transform import run_transform
from xai.modules.xai import run_xai
from AI.libs.utils.data import fetch_stock_data, fetch_fundamentals
from AI.libs.utils.io import _log

# ==============================================
# Helper: Finder 결과 → JSON 변환
# ==============================================
def make_reasons_json(finder_df: pd.DataFrame, run_date: str) -> Dict:
    """
    Finder 결과에서 종목 선택 이유를 JSON 구조로 변환
    { "YYYY-MM-DD": { "TICKER1": "이유 요약", "TICKER2": "..." } }
    """
    reasons = {}
    daily_reasons = {}
    for _, row in finder_df.iterrows():
        daily_reasons[row["ticker"]] = row.get("reason", "선정 사유 없음")
    reasons[run_date] = daily_reasons
    return reasons

# ==============================================
# 주간 Finder (월요일 1회)
# ==============================================
def run_weekly_finder(config: dict, run_date: str) -> pd.DataFrame:
    _log(f"[FINDER] 주간 종목 선정 실행 ({run_date})")

    finder_df = run_finder_with_scores(config)  # 종목+점수+이유 포함 DataFrame

    out_dir = os.path.join(config["storage"]["out_dir"], "finder")
    os.makedirs(out_dir, exist_ok=True)

    # parquet 저장
    finder_path = os.path.join(out_dir, f"finder_{run_date}.parquet")
    finder_df.to_parquet(finder_path, index=False)

    # JSON 이유 저장 (append)
    reasons_path = os.path.join(out_dir, "reasons.json")
    reasons = make_reasons_json(finder_df, run_date)
    if os.path.exists(reasons_path):
        with open(reasons_path, "r", encoding="utf-8") as f:
            prev = json.load(f)
    else:
        prev = {}
    prev.update(reasons)
    with open(reasons_path, "w", encoding="utf-8") as f:
        json.dump(prev, f, ensure_ascii=False, indent=2)

    return finder_df

# ==============================================
# 일간 Transform + XAI
# ==============================================
def run_daily_tasks(config: dict, run_date: str, finder_df: pd.DataFrame) -> None:
    _log(f"[DAILY] Transform + XAI 실행 ({run_date})")

    # 데이터 수집
    tickers = finder_df["ticker"].tolist()
    window_days = int(config.get("data", {}).get("window_days", 252 * 5))
    interval = str(config.get("data", {}).get("interval", "1d"))
    cache_dir = str(config.get("storage", {}).get("cache_dir", ""))

    market_data = fetch_stock_data(tickers, period_days=window_days, interval=interval, cache_dir=cache_dir)
    fundamentals = fetch_fundamentals(tickers)

    # Transform (학습 + 로그 생성)
    tr = run_transform(
        finder_df,
        seq_len=config["transform"]["seq_len"],
        pred_h=config["transform"]["pred_h"],
    )
    logs_df: pd.DataFrame = tr["logs"]  # (종목,날짜,매매여부,가격,비중,피쳐...,확률...)

    # Transform 로그 저장 (Parquet)
    out_dir = os.path.join(config["storage"]["out_dir"], "transform")
    os.makedirs(out_dir, exist_ok=True)
    log_path = os.path.join(out_dir, f"logs_{run_date}.parquet")
    logs_df.to_parquet(log_path, index=False)

    # XAI 리포트 생성 + 저장 (JSON per ticker)
    xai_out_dir = os.path.join(config["storage"]["out_dir"], "xai", run_date)
    os.makedirs(xai_out_dir, exist_ok=True)

    xai_reports = run_xai(logs_df)
    for ticker, report in xai_reports.items():
        with open(os.path.join(xai_out_dir, f"{ticker}.json"), "w", encoding="utf-8") as f:
            json.dump(report, f, ensure_ascii=False, indent=2)

    _log(f"[DAILY] Transform 로그 + XAI 저장 완료 ({run_date})")

# ==============================================
# 메인 파이프라인
# ==============================================
def run_pipeline(config: dict) -> bool:
    run_date = dt.datetime.now(dt.timezone(dt.timedelta(hours=9))).strftime("%Y-%m-%d")

    try:
        _log(f"=== 배치 시작: {run_date} ===")

        # 1) 주간 Finder (월요일만 새로 실행)
        finder_out_dir = os.path.join(config["storage"]["out_dir"], "finder")
        if dt.datetime.now().weekday() == 0:  # 월요일
            finder_df = run_weekly_finder(config, run_date)
        else:
            last_file = sorted(
                [f for f in os.listdir(finder_out_dir) if f.startswith("finder_")]
            )[-1]
            finder_df = pd.read_parquet(os.path.join(finder_out_dir, last_file))

        # 2) 일간 Transform + XAI
        run_daily_tasks(config, run_date, finder_df)

        _log("=== 배치 성공 ===")
        return True

    except Exception as e:
        _log(f"[ERROR] 배치 실패: {e}")
        return False
