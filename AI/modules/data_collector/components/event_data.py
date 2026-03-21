import io
import json
import os
import sys
import time
from contextlib import redirect_stderr, redirect_stdout
from datetime import date, datetime, timedelta
from typing import List, Optional

import pandas as pd
import requests
import yfinance as yf
from psycopg2.extras import execute_values

# 프로젝트 루트 경로 설정
current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../../../.."))
if project_root not in sys.path:
    sys.path.append(project_root)

from AI.libs.database.connection import get_db_conn


FMP_API_KEY = os.getenv("FMP_API_KEY", "your_fmp_api_key_here")


class EventDataCollector:
    """
    이벤트 일정과 서프라이즈 데이터를 수집합니다.
    - Macro: FMP API에서 forecast/actual 수집
    - Earnings: yfinance에서 EPS estimate/reported 수집
    """

    def __init__(self, db_name: str = "db"):
        self.db_name = db_name
        self.macro_urls = [
            "https://financialmodelingprep.com/stable/economic-calendar",
            "https://financialmodelingprep.com/api/v3/economic_calendar",
        ]

    def _get_fmp_api_key(self) -> Optional[str]:
        """실행 시점 기준으로 FMP API 키를 재확인합니다."""
        api_key = os.getenv("FMP_API_KEY", FMP_API_KEY).strip()
        if not api_key or api_key == "your_fmp_api_key_here":
            return None
        return api_key

    def _parse_fmp_error_message(self, response: requests.Response) -> str:
        """FMP 응답 본문에서 사람이 읽기 쉬운 오류 메시지를 추출합니다."""
        try:
            payload = response.json()
        except ValueError:
            return response.text[:300].strip()

        if isinstance(payload, dict):
            for key in ("Error Message", "error", "message"):
                value = payload.get(key)
                if value:
                    return str(value)

        if isinstance(payload, list) and payload:
            return json.dumps(payload[:2], ensure_ascii=False)

        return response.text[:300].strip()

    def _has_sufficient_macro_data(self) -> bool:
        """DB에 미래 macro 이벤트가 충분하면 불필요한 호출을 줄입니다."""
        conn = get_db_conn(self.db_name)
        cursor = conn.cursor()
        try:
            query = """
                SELECT COUNT(*)
                FROM public.event_calendar
                WHERE ticker = 'MACRO' AND event_date > CURRENT_DATE
            """
            cursor.execute(query)
            count = cursor.fetchone()[0]
            return count >= 5
        except Exception:
            return False
        finally:
            cursor.close()
            conn.close()

    def fetch_macro_from_api(self):
        """FMP economic calendar API를 호출합니다."""
        print("[Event] FMP API로 경제 지표(Surprise) 수집 중...")
        start_date = (datetime.now() - timedelta(days=30)).strftime("%Y-%m-%d")
        end_date = (datetime.now() + timedelta(days=365)).strftime("%Y-%m-%d")

        api_key = self._get_fmp_api_key()
        if not api_key:
            print("   [Error] FMP_API_KEY가 설정되지 않았습니다.")
            return []

        last_status = None
        for base_url in self.macro_urls:
            try:
                response = requests.get(
                    base_url,
                    params={"from": start_date, "to": end_date, "apikey": api_key},
                    timeout=10,
                )
            except Exception as e:
                print(f"   [Error] API 연동 오류 [{base_url}]: {e}")
                continue

            if response.status_code == 200:
                return response.json()

            last_status = response.status_code
            error_message = self._parse_fmp_error_message(response)
            print(
                f"   [Error] FMP 호출 실패 ({response.status_code}) "
                f"[{base_url}]: {error_message}"
            )

        if last_status in (402, 403):
            print(
                "   [Hint] 현재 FMP 구독 플랜 또는 엔드포인트 권한으로 "
                "economic calendar 접근이 불가합니다."
            )
        return []

    def update_macro_events(self, force_update: bool = False):
        """거시경제 이벤트 일정을 저장합니다."""
        if not force_update and self._has_sufficient_macro_data():
            return

        events = self.fetch_macro_from_api()
        if not events:
            return

        conn = get_db_conn(self.db_name)
        cursor = conn.cursor()

        insert_query = """
            INSERT INTO public.event_calendar
            (event_date, event_type, ticker, description, forecast, actual)
            VALUES %s
            ON CONFLICT (event_date, event_type, ticker)
            DO UPDATE SET
                description = EXCLUDED.description,
                forecast = EXCLUDED.forecast,
                actual = EXCLUDED.actual;
        """

        target_keywords = {
            "FOMC": ["FOMC", "Fed Interest Rate Decision"],
            "CPI": ["CPI", "Consumer Price Index"],
            "GDP": ["GDP Growth Rate", "Gross Domestic Product"],
            "PCE": ["PCE", "Personal Consumption Expenditures"],
        }

        data_to_insert = []
        seen = set()

        for item in events:
            if item.get("country") != "US":
                continue

            evt_date = item.get("date", "")[:10]
            evt_name = item.get("event", "")
            estimate_val = item.get("estimate")
            actual_val = item.get("actual")

            detected_type = None
            for key, keywords in target_keywords.items():
                if any(keyword in evt_name for keyword in keywords):
                    detected_type = key
                    break

            if detected_type and (evt_date, detected_type) not in seen:
                data_to_insert.append(
                    (
                        evt_date,
                        detected_type,
                        "MACRO",
                        evt_name,
                        estimate_val,
                        actual_val,
                    )
                )
                seen.add((evt_date, detected_type))

        try:
            if data_to_insert:
                execute_values(cursor, insert_query, data_to_insert)
                conn.commit()
                print(f"   >> Macro 일정/서프라이즈 {len(data_to_insert)}건 저장 완료.")
            else:
                print("   >> 저장할 주요 매크로 이벤트가 없습니다.")
        except Exception as e:
            conn.rollback()
            print(f"   [Error] DB 저장 실패: {e}")
        finally:
            cursor.close()
            conn.close()

    def update_earnings_dates(self, tickers: List[str]):
        """기업 실적 발표 일정과 EPS 추정/실적 값을 저장합니다."""
        if not tickers:
            return
        print(f"[Event] 기업 {len(tickers)}개 실적 서프라이즈 데이터 수집 중...")

        conn = get_db_conn(self.db_name)
        cursor = conn.cursor()

        insert_query = """
            INSERT INTO public.event_calendar
            (event_date, event_type, ticker, description, forecast, actual)
            VALUES %s
            ON CONFLICT (event_date, event_type, ticker)
            DO UPDATE SET
                forecast = EXCLUDED.forecast,
                actual = EXCLUDED.actual;
        """

        data_buffer = []

        for ticker in tickers:
            try:
                yf_ticker = yf.Ticker(ticker)
                # 일부 티커는 yfinance가 경고/잡음을 stderr로 출력하므로 해당 호출만 조용히 감쌉니다.
                with io.StringIO() as buffer, redirect_stdout(buffer), redirect_stderr(buffer):
                    df_earnings = yf_ticker.get_earnings_dates(limit=8)

                if df_earnings is None or df_earnings.empty:
                    continue

                for dt_idx, row in df_earnings.iterrows():
                    evt_date = dt_idx.date()
                    if evt_date < (date.today() - timedelta(days=180)):
                        continue
                    if evt_date > (date.today() + timedelta(days=365)):
                        continue

                    estimate = row.get("EPS Estimate")
                    reported = row.get("Reported EPS")

                    if pd.isna(estimate):
                        estimate = None
                    else:
                        estimate = float(estimate)

                    if pd.isna(reported):
                        reported = None
                    else:
                        reported = float(reported)

                    data_buffer.append(
                        (
                            evt_date,
                            "EARNINGS",
                            ticker,
                            f"{ticker} Earnings Release",
                            estimate,
                            reported,
                        )
                    )

                time.sleep(0.1)

                if len(data_buffer) >= 50:
                    execute_values(cursor, insert_query, data_buffer)
                    conn.commit()
                    data_buffer = []

            except Exception:
                continue

        if data_buffer:
            execute_values(cursor, insert_query, data_buffer)
            conn.commit()

        cursor.close()
        conn.close()
        print("   >> 실적 데이터 업데이트 완료.")

    def run(self, tickers: List[str] = None):
        self.update_macro_events(force_update=False)
        if tickers:
            self.update_earnings_dates(tickers)


if __name__ == "__main__":
    import argparse
    from AI.libs.database.ticker_loader import load_all_tickers_from_db

    parser = argparse.ArgumentParser()
    parser.add_argument("--all", action="store_true")
    parser.add_argument("--force", action="store_true")
    parser.add_argument("--db", default="db")
    args = parser.parse_args()

    collector = EventDataCollector(db_name=args.db)

    if args.force:
        collector.update_macro_events(force_update=True)

    targets = load_all_tickers_from_db() if args.all else None
    collector.run(tickers=targets)
