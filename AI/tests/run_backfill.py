#AI/tests/run_backfill.py
"""
[과거 매매 기록 소급 생성기 (Backfill)]
- 지정한 기간(예: 2025년 10월 ~ 현재) 동안 하루씩 루프를 돌며 daily_routine 을 실행합니다.
- 주말(토,일)은 자동으로 제외하며, 휴장일은 daily_routine 내부 로직에 의해 스킵됩니다.
- XAI 리포트 생성은 API 비용과 시간 단축을 위해 기본적으로 꺼두는 것을 권장합니다.
"""
import os
import warnings

os.environ['TF_CPP_MIN_LOG_LEVEL'] = '3' 
os.environ['TF_ENABLE_ONEDNN_OPTS'] = '0'   

# 2. Pandas, Scikit-learn 등 파이썬 레벨의 모든 성가신 경고 차단
warnings.filterwarnings('ignore', category=UserWarning)
warnings.filterwarnings('ignore', category=FutureWarning)
warnings.filterwarnings('ignore', message='.*InconsistentVersionWarning.*')
warnings.filterwarnings('ignore', message='.*SQLAlchemy.*')

import sys
import argparse
import pandas as pd
import time
import json
from datetime import datetime

# 프로젝트 루트 경로 추가
current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../.."))
if project_root not in sys.path:
    sys.path.append(project_root)

# daily_routine의 파이프라인 함수를 가져옵니다.
from AI.pipelines.daily_routine import run_daily_pipeline

def run_backfill(start_date: str, end_date: str, tickers: list, enable_xai: bool):
    print(f"🚀 백필(Backfill) 시뮬레이션 시작!")
    print(f" - 기간: {start_date} ~ {end_date}")
    print(f" - 종목: {tickers}")
    print(f" - XAI 리포트 생성: {'ON' if enable_xai else 'OFF (권장)'}\n")

    # freq='B' 를 사용하면 Business Day(평일)만 리스트로 만들어줍니다.
    dates = pd.date_range(start=start_date, end=end_date, freq='B')
    
    total_days = len(dates)
    
    for i, d in enumerate(dates, 1):
        target_date_str = d.strftime("%Y-%m-%d")
        print(f"\n==================================================")
        print(f"▶ [Progress: {i}/{total_days}] 타겟 날짜: {target_date_str}")
        print(f"==================================================")
        
        try:
            # daily_routine 파이프라인 호출 (해당 날짜 주입)
            run_daily_pipeline(
                target_tickers=tickers, 
                mode="simulation", 
                enable_xai=enable_xai, 
                target_date=target_date_str
            )
            
            # DB 부하 및 API Rate Limit 방지를 위해 짧게 휴식
            time.sleep(1)
            
        except Exception as e:
            print(f"❌ {target_date_str} 시뮬레이션 중 치명적 오류 발생: {e}")
            # 한 날짜가 실패해도 다음 날짜로 계속 진행
            continue

    print("\n 모든 백필 작업이 완료")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="과거 매매 기록 소급 생성기")
    parser.add_argument("--start_date", type=str, default="2025-03-01", help="시작일 (YYYY-MM-DD)")
    parser.add_argument("--end_date", type=str, default="2026-02-24", help="종료일 (YYYY-MM-DD)")
    parser.add_argument("--enable_xai", action="store_true", help="XAI 리포트 생성 켜기 (비용/시간 주의)")
    
    args = parser.parse_args()
    
    run_backfill(
        start_date=args.start_date,
        end_date=args.end_date,
        tickers=[], 
        enable_xai=args.enable_xai
    )