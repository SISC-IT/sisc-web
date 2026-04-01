import json
import os
import sys
from io import StringIO
from typing import Dict, List

import pandas as pd
import requests

# 프로젝트 루트 경로 설정
current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../../../.."))
if project_root not in sys.path:
    sys.path.append(project_root)

from AI.libs.database.connection import get_db_conn
from AI.modules.data_collector.components.company_name_korean_updater import CompanyNameKoreanUpdater


class TickerUpdater:
    """
    [Ticker Updater]
    - 외부 소스(Wikipedia/File)에서 티커를 수집합니다.
    - stock_info 테이블을 갱신합니다.
    - 수집된 티커에 대해 company_names 한글명 모듈을 함께 실행합니다.
    """

    USER_AGENT = (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
        "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
    )

    def __init__(self, db_name: str = "db"):
        self.db_name = db_name
        self.name_updater = CompanyNameKoreanUpdater(db_name=db_name)

    def fetch_sp500_tickers(self) -> List[Dict]:
        print("[TickerUpdater] S&P 500 리스트 다운로드 중...")
        try:
            url = "https://en.wikipedia.org/wiki/List_of_S%26P_500_companies"
            response = requests.get(url, headers={"User-Agent": self.USER_AGENT}, timeout=20)
            response.raise_for_status()

            tables = pd.read_html(StringIO(response.text))
            if not tables:
                return []

            df = tables[0].copy()
            df["Symbol"] = df["Symbol"].astype(str).str.replace(".", "-", regex=False)

            tickers = []
            for _, row in df.iterrows():
                tickers.append(
                    {
                        "ticker": str(row.get("Symbol", "")).strip().upper(),
                        "name": str(row.get("Security", "")).strip(),
                        "sector": str(row.get("GICS Sector", "")).strip() or None,
                    }
                )
            return [item for item in tickers if item["ticker"]]
        except Exception as e:
            print(f"[TickerUpdater][Error] S&P 500 로드 실패: {e}")
            return []

    def fetch_nasdaq100_tickers(self) -> List[Dict]:
        print("[TickerUpdater] NASDAQ 100 리스트 다운로드 중...")
        try:
            url = "https://en.wikipedia.org/wiki/Nasdaq-100"
            response = requests.get(url, headers={"User-Agent": self.USER_AGENT}, timeout=20)
            response.raise_for_status()

            tables = pd.read_html(StringIO(response.text))
            df = None
            for table in tables:
                if "Ticker" in table.columns and "Company" in table.columns:
                    df = table.copy()
                    break

            if df is None:
                return []

            df["Ticker"] = df["Ticker"].astype(str).str.replace(".", "-", regex=False)

            tickers = []
            for _, row in df.iterrows():
                tickers.append(
                    {
                        "ticker": str(row.get("Ticker", "")).strip().upper(),
                        "name": str(row.get("Company", "")).strip(),
                        "sector": str(row.get("GICS Sector", "")).strip() or None,
                    }
                )
            return [item for item in tickers if item["ticker"]]
        except Exception as e:
            print(f"[TickerUpdater][Error] NASDAQ 100 로드 실패: {e}")
            return []

    def fetch_file_tickers(self, file_path: str) -> List[Dict]:
        print(f"[TickerUpdater] 파일 티커 로드 중: {file_path}")
        if not os.path.exists(file_path):
            print(f"[TickerUpdater][Warn] 파일이 존재하지 않습니다: {file_path}")
            return []

        try:
            with open(file_path, "r", encoding="utf-8") as f:
                payload = json.load(f)
        except Exception as e:
            print(f"[TickerUpdater][Error] 파일 로드 실패: {e}")
            return []

        if isinstance(payload, dict):
            if "tickers" in payload and isinstance(payload["tickers"], list):
                payload = payload["tickers"]
            else:
                payload = [payload]

        if not isinstance(payload, list):
            return []

        tickers: List[Dict] = []
        for item in payload:
            if isinstance(item, str):
                ticker = item.strip().upper().replace(".", "-")
                if ticker:
                    tickers.append({"ticker": ticker, "name": "", "sector": None})
                continue

            if not isinstance(item, dict):
                continue

            ticker = str(item.get("ticker") or item.get("symbol") or "").strip().upper().replace(".", "-")
            if not ticker:
                continue

            tickers.append(
                {
                    "ticker": ticker,
                    "name": str(item.get("name") or item.get("company_name") or "").strip(),
                    "sector": str(item.get("sector") or "").strip() or None,
                }
            )

        return tickers

    def collect_tickers(self, source: str = "all", file_path: str = "AI/weekly_tickers.json") -> List[Dict]:
        final_list: List[Dict] = []

        if source in ["sp500", "all"]:
            final_list.extend(self.fetch_sp500_tickers())

        if source in ["nasdaq", "all"]:
            final_list.extend(self.fetch_nasdaq100_tickers())

        if source in ["file", "all"]:
            final_list.extend(self.fetch_file_tickers(file_path))

        # 티커 기준 중복 제거 (먼저 들어온 값 우선)
        unique = {}
        for item in final_list:
            ticker = str(item.get("ticker", "")).strip().upper()
            if not ticker:
                continue
            if ticker not in unique:
                unique[ticker] = item

        deduped = list(unique.values())
        print(f"[TickerUpdater] 수집 완료: {len(deduped)}개 티커")
        return deduped

    def count_tickers_in_db(self) -> int:
        conn = get_db_conn(self.db_name)
        cursor = conn.cursor()
        try:
            cursor.execute("SELECT COUNT(*) FROM public.stock_info;")
            row = cursor.fetchone()
            return int(row[0] if row else 0)
        finally:
            cursor.close()
            conn.close()

    def save_to_db(self, ticker_list: List[Dict], sync_korean_names: bool = True) -> None:
        if not ticker_list:
            print("[TickerUpdater] 저장할 티커가 없습니다.")
            return

        conn = get_db_conn(self.db_name)
        cursor = conn.cursor()
        try:
            insert_stock_query = """
                INSERT INTO public.stock_info (ticker)
                VALUES (%s)
                ON CONFLICT (ticker) DO NOTHING;
            """
            stock_rows = [(item["ticker"],) for item in ticker_list if item.get("ticker")]
            cursor.executemany(insert_stock_query, stock_rows)
            print(f"[TickerUpdater] stock_info 저장 완료: {len(stock_rows)}개 처리")

            if sync_korean_names:
                name_success = 0
                for item in ticker_list:
                    ticker = str(item.get("ticker", "")).strip().upper()
                    if not ticker:
                        continue
                    english_name = str(item.get("name", "")).strip() or None
                    if self.name_updater.upsert_company_name(
                        ticker=ticker,
                        english_name=english_name,
                        cursor=cursor,
                    ):
                        name_success += 1
                print(f"[TickerUpdater] company_names 동기화 완료: {name_success}개 처리")

            conn.commit()
        except Exception as e:
            conn.rollback()
            print(f"[TickerUpdater][Error] DB 저장 실패: {e}")
        finally:
            cursor.close()
            conn.close()

    def run(
        self,
        source: str = "all",
        file_path: str = "AI/weekly_tickers.json",
        sync_korean_names: bool = True,
    ) -> int:
        ticker_list = self.collect_tickers(source=source, file_path=file_path)
        self.save_to_db(ticker_list=ticker_list, sync_korean_names=sync_korean_names)
        return len(ticker_list)

    def ensure_minimum_tickers(
        self,
        min_count: int = 100,
        source: str = "all",
        file_path: str = "AI/weekly_tickers.json",
    ) -> bool:
        current_count = self.count_tickers_in_db()
        print(f"[TickerUpdater] 현재 DB 티커 수: {current_count}개")

        if current_count > min_count:
            print(f"[TickerUpdater] 기준({min_count}개 초과) 만족. 업데이트 생략.")
            return False

        print(f"[TickerUpdater] 기준({min_count}개 이하) 미달. 티커 수집을 실행합니다.")
        collected_count = self.run(source=source, file_path=file_path, sync_korean_names=True)
        print(f"[TickerUpdater] 실행 완료: {collected_count}개 수집 시도")
        return True


def main() -> None:
    import argparse

    parser = argparse.ArgumentParser(description="Ticker Updater (stock_info/company_names 동기화)")
    parser.add_argument(
        "--source",
        type=str,
        default="all",
        choices=["sp500", "nasdaq", "file", "all"],
        help="티커 수집 소스",
    )
    parser.add_argument("--file", type=str, default="AI/weekly_tickers.json", help="file 소스 경로")
    parser.add_argument("--db", default="db", help="DB 이름")
    parser.add_argument("--min-count", type=int, default=None, help="설정 시 DB 티커 수 기준으로 조건 실행")
    parser.add_argument("--skip-korean", action="store_true", help="company_names 한글명 동기화 생략")

    args = parser.parse_args()

    updater = TickerUpdater(db_name=args.db)
    if args.min_count is not None:
        updater.ensure_minimum_tickers(min_count=args.min_count, source=args.source, file_path=args.file)
        return

    count = updater.run(source=args.source, file_path=args.file, sync_korean_names=not args.skip_korean)
    print(f"[TickerUpdater] 완료: 총 {count}개 티커 수집/저장 시도")


if __name__ == "__main__":
    main()
