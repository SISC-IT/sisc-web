from __future__ import annotations

import csv
import importlib
import json
import logging
import time
from dataclasses import dataclass
from datetime import date, datetime
from pathlib import Path
from typing import Iterable, Literal, Sequence

import pandas as pd
from psycopg2.extras import execute_values
from tqdm import tqdm

from AI.libs.database.connection import get_db_conn


# 지원하는 시장/수집 소스/저장 방식을 타입으로 제한해 CLI 오입력을 빨리 잡습니다.
Market = Literal["KOSPI", "KOSDAQ", "KONEX", "ALL"]
Source = Literal["fdr", "pykrx"]
Storage = Literal["file", "db", "both"]
FileFormat = Literal["csv", "parquet"]


# 이 파일 위치: AI/modules/data_collector/components/korea_stock_data.py
# parents[4]는 저장소 루트(sisc-web)를 가리킵니다.
PROJECT_ROOT = Path(__file__).resolve().parents[4]
DEFAULT_CONFIG_PATH = PROJECT_ROOT / "AI/modules/data_collector/config/korea_stocks.json"


@dataclass(frozen=True)
class KoreaStockCollectorConfig:
    """국내 주식 OHLCV 수집 실행에 필요한 설정값 묶음."""

    # 기본 소스는 FinanceDataReader입니다. pykrx 최신 버전은 numpy>=2를 요구할 수 있어
    # 현재 AI 학습 환경(numpy<2.0)과 충돌 가능성이 있기 때문입니다.
    source: Source = "fdr"

    # 기본 대상은 국장 자동매매의 핵심 대상인 KOSPI/KOSDAQ입니다.
    markets: tuple[Market, ...] = ("KOSPI", "KOSDAQ")

    # 기존 해외 주식 수집기와 맞춰 2015년부터 충분한 학습 구간을 확보합니다.
    start_date: str = "2015-01-01"
    end_date: str | None = None

    # both: 로컬 파일 검증과 운영 DB 적재를 동시에 수행합니다.
    storage: Storage = "both"
    file_format: FileFormat = "csv"

    # 수집 결과와 로그는 data_collector 내부에 모아두되, git에는 올리지 않습니다.
    data_dir: Path = PROJECT_ROOT / "AI/modules/data_collector/storage/korea_ohlcv"
    log_dir: Path = PROJECT_ROOT / "AI/modules/data_collector/logs"

    # 기존 DB 연결 유틸의 기본 prefix인 "db"를 그대로 사용합니다.
    db_name: str = "db"

    # 공개 데이터 소스에 과도한 요청을 보내지 않도록 종목 사이에 짧은 휴식 시간을 둡니다.
    sleep_seconds: float = 0.2

    # 샘플 테스트용 제한값입니다. None이면 전체 종목을 대상으로 실행합니다.
    batch_limit: int | None = None

    @classmethod
    def from_file(cls, path: str | Path = DEFAULT_CONFIG_PATH) -> "KoreaStockCollectorConfig":
        """JSON 설정 파일을 읽어 Config 객체로 변환합니다."""

        config_path = Path(path)
        if not config_path.is_absolute():
            config_path = PROJECT_ROOT / config_path

        with config_path.open("r", encoding="utf-8") as f:
            raw = json.load(f)

        return cls(
            source=raw.get("source", cls.source),
            markets=tuple(raw.get("markets", cls.markets)),
            start_date=raw.get("start_date", cls.start_date),
            end_date=raw.get("end_date"),
            storage=raw.get("storage", cls.storage),
            file_format=raw.get("file_format", cls.file_format),
            data_dir=_resolve_project_path(raw.get("data_dir", cls.data_dir)),
            log_dir=_resolve_project_path(raw.get("log_dir", cls.log_dir)),
            db_name=raw.get("db_name", cls.db_name),
            sleep_seconds=float(raw.get("sleep_seconds", cls.sleep_seconds)),
            batch_limit=raw.get("batch_limit", cls.batch_limit),
        )

    def with_overrides(self, **kwargs) -> "KoreaStockCollectorConfig":
        """CLI 인자로 받은 값만 기존 설정 위에 덮어씁니다."""

        values = self.__dict__.copy()
        values.update({k: v for k, v in kwargs.items() if v is not None})

        # JSON/CLI에서는 list나 str로 들어올 수 있어 dataclass 타입에 맞게 정규화합니다.
        if isinstance(values.get("markets"), list):
            values["markets"] = tuple(values["markets"])
        if isinstance(values.get("data_dir"), str):
            values["data_dir"] = _resolve_project_path(values["data_dir"])
        if isinstance(values.get("log_dir"), str):
            values["log_dir"] = _resolve_project_path(values["log_dir"])

        return KoreaStockCollectorConfig(**values)


def _resolve_project_path(path: str | Path) -> Path:
    """상대 경로는 저장소 루트 기준 절대 경로로 바꿉니다."""

    resolved = Path(path)
    return resolved if resolved.is_absolute() else PROJECT_ROOT / resolved


def _as_yyyymmdd(value: str | date | datetime | None) -> str:
    """pykrx가 요구하는 YYYYMMDD 문자열로 날짜를 변환합니다."""

    if value is None:
        return datetime.now().strftime("%Y%m%d")
    if isinstance(value, datetime):
        return value.strftime("%Y%m%d")
    if isinstance(value, date):
        return value.strftime("%Y%m%d")
    return value.replace("-", "")


def _as_iso_date(value: str | date | datetime | None) -> str:
    """FinanceDataReader와 내부 표준에 맞는 YYYY-MM-DD 문자열로 날짜를 변환합니다."""

    if value is None:
        return datetime.now().strftime("%Y-%m-%d")
    if isinstance(value, datetime):
        return value.date().isoformat()
    if isinstance(value, date):
        return value.isoformat()
    if len(value) == 8 and value.isdigit():
        return f"{value[:4]}-{value[4:6]}-{value[6:]}"
    return value


def _require_module(module_name: str, package_name: str):
    """선택 의존성을 실행 시점에 import하고, 없으면 설치 방법을 알려줍니다."""

    try:
        return importlib.import_module(module_name)
    except ImportError as exc:
        raise RuntimeError(
            f"이 수집기를 실행하려면 {package_name} 패키지가 필요합니다. "
            f"`pip install {package_name}` 명령으로 설치해 주세요."
        ) from exc


def _build_logger(log_dir: Path) -> logging.Logger:
    """배치 실행 결과를 콘솔과 파일에 동시에 남기는 logger를 구성합니다."""

    log_dir.mkdir(parents=True, exist_ok=True)
    logger = logging.getLogger("korea_stock_data")
    logger.setLevel(logging.INFO)

    # 테스트나 반복 실행 중 handler가 중복 등록되면 로그가 여러 번 찍히므로 초기화합니다.
    logger.handlers.clear()

    formatter = logging.Formatter("%(asctime)s %(levelname)s %(message)s")
    file_handler = logging.FileHandler(
        log_dir / f"korea_stock_data_{datetime.now().strftime('%Y%m%d')}.log",
        encoding="utf-8",
    )
    file_handler.setFormatter(formatter)
    logger.addHandler(file_handler)

    stream_handler = logging.StreamHandler()
    stream_handler.setFormatter(formatter)
    logger.addHandler(stream_handler)
    return logger


class KoreaStockDataCollector:
    """KOSPI/KOSDAQ 일봉 OHLCV를 수집하고 파일/DB에 저장하는 수집기."""

    # 기존 AI/트레이딩 파이프라인이 기대하는 컬럼을 기준으로 표준 출력 순서를 고정합니다.
    # trading_value는 국장 용어이고, amount는 기존 price_data DB 컬럼과의 호환용입니다.
    STANDARD_COLUMNS = [
        "date",
        "ticker",
        "open",
        "high",
        "low",
        "close",
        "volume",
        "trading_value",
        "amount",
        "adjusted_close",
        "market",
        "name",
    ]

    def __init__(self, config: KoreaStockCollectorConfig | None = None):
        self.config = config or KoreaStockCollectorConfig.from_file()

        # 저장/로그 경로는 실행 전에 보장합니다. gitignore 대상이라 비어 있으면 repo에 보이지 않습니다.
        self.config.data_dir.mkdir(parents=True, exist_ok=True)
        self.config.log_dir.mkdir(parents=True, exist_ok=True)
        self.logger = _build_logger(self.config.log_dir)

    def get_ticker_list(self, markets: Sequence[Market] | None = None) -> pd.DataFrame:
        """시장별 상장 종목 코드를 수집해 ticker/name/market DataFrame으로 반환합니다."""

        markets = tuple(markets or self.config.markets)
        frames: list[pd.DataFrame] = []

        if self.config.source == "pykrx":
            # pykrx는 종목명 조회 API가 별도로 있어 ticker마다 이름을 한 번씩 조회합니다.
            stock = _require_module("pykrx.stock", "pykrx")
            for market in markets:
                tickers = stock.get_market_ticker_list(market="ALL" if market == "ALL" else market)
                frames.append(
                    pd.DataFrame(
                        {
                            "ticker": tickers,
                            "name": [stock.get_market_ticker_name(t) for t in tickers],
                            "market": market,
                        }
                    )
                )
        else:
            # FinanceDataReader는 시장별 listing 테이블에 종목 코드와 이름이 같이 들어옵니다.
            fdr = _require_module("FinanceDataReader", "finance-datareader")
            market_map = {"KOSPI": "KOSPI", "KOSDAQ": "KOSDAQ", "KONEX": "KONEX", "ALL": "KRX"}
            for market in markets:
                listing = fdr.StockListing(market_map[market])

                # 버전에 따라 종목 코드 컬럼명이 Code 또는 Symbol일 수 있어 둘 다 대응합니다.
                symbol_col = "Code" if "Code" in listing.columns else "Symbol"
                name_col = "Name" if "Name" in listing.columns else symbol_col
                frames.append(
                    pd.DataFrame(
                        {
                            "ticker": listing[symbol_col].astype(str).str.zfill(6),
                            "name": listing[name_col].astype(str),
                            "market": market,
                        }
                    )
                )

        if not frames:
            return pd.DataFrame(columns=["ticker", "name", "market"])

        # KRX 전체 목록을 섞으면 중복 종목이 생길 수 있어 ticker 기준으로 제거합니다.
        return (
            pd.concat(frames, ignore_index=True)
            .drop_duplicates(subset=["ticker"])
            .sort_values(["market", "ticker"])
            .reset_index(drop=True)
        )

    def fetch_daily_ohlcv(
        self,
        ticker: str,
        start_date: str | date | datetime | None = None,
        end_date: str | date | datetime | None = None,
        market: str | None = None,
        name: str | None = None,
    ) -> pd.DataFrame:
        """단일 종목의 일봉 OHLCV를 수집하고 내부 표준 컬럼으로 정규화합니다."""

        # 국내 종목 코드는 6자리 문자열이 표준입니다. 숫자로 들어와도 앞쪽 0을 복구합니다.
        ticker = str(ticker).zfill(6)
        start = _as_iso_date(start_date or self.config.start_date)
        end = _as_iso_date(end_date or self.config.end_date)

        if self.config.source == "pykrx":
            raw = self._fetch_with_pykrx(ticker, start, end)
        else:
            raw = self._fetch_with_fdr(ticker, start, end)

        return self._normalize_ohlcv(raw, ticker=ticker, market=market, name=name)

    def collect_batch(
        self,
        tickers: Iterable[str] | None = None,
        markets: Sequence[Market] | None = None,
        start_date: str | date | datetime | None = None,
        end_date: str | date | datetime | None = None,
    ) -> dict[str, int]:
        """여러 종목을 순회 수집합니다. 실패한 종목은 기록만 하고 다음 종목으로 넘어갑니다."""

        if tickers:
            # 사용자가 직접 종목을 지정한 경우에도 가능하면 listing에서 종목명/시장 정보를 보강합니다.
            target = {str(t).zfill(6) for t in tickers}
            try:
                listing = self.get_ticker_list(markets)
                listing = listing[listing["ticker"].isin(target)].copy()
            except Exception as exc:
                # listing 수집이 실패해도 명시 ticker 수집 자체는 계속할 수 있습니다.
                self.logger.warning("종목 목록 조회에 실패해 입력받은 종목 코드만 사용합니다: %s", exc)
                listing = pd.DataFrame(columns=["ticker", "name", "market"])

            missing = sorted(target - set(listing["ticker"]))
            if missing:
                fallback_market = markets[0] if markets else "UNKNOWN"
                listing = pd.concat(
                    [
                        listing,
                        pd.DataFrame(
                            {"ticker": missing, "name": [""] * len(missing), "market": [fallback_market] * len(missing)}
                        ),
                    ],
                    ignore_index=True,
                )
        else:
            # ticker를 직접 주지 않으면 설정된 시장 전체 종목을 대상으로 합니다.
            listing = self.get_ticker_list(markets)

        # 샘플 실행이나 CI 검증에서는 batch_limit으로 대상 종목 수를 줄일 수 있습니다.
        if self.config.batch_limit:
            listing = listing.head(int(self.config.batch_limit))

        stats = {"success": 0, "empty": 0, "failed": 0, "rows": 0}
        failures: list[dict[str, str]] = []

        for row in tqdm(listing.itertuples(index=False), total=len(listing), desc="Korea OHLCV"):
            try:
                df = self.fetch_daily_ohlcv(
                    ticker=row.ticker,
                    start_date=start_date,
                    end_date=end_date,
                    market=row.market,
                    name=row.name,
                )
                if df.empty:
                    # 신규상장 전 구간, 상장폐지/데이터 미제공 종목 등은 빈 결과가 정상일 수 있습니다.
                    stats["empty"] += 1
                    failures.append({"ticker": row.ticker, "market": row.market, "reason": "empty"})
                    continue

                if self.config.storage in {"file", "both"}:
                    self.save_to_file(df, market=row.market)
                if self.config.storage in {"db", "both"}:
                    self.save_to_db(df)

                stats["success"] += 1
                stats["rows"] += len(df)
            except Exception as exc:
                # 한 종목 실패가 전체 배치를 중단시키지 않도록 실패 목록에만 남깁니다.
                stats["failed"] += 1
                failures.append({"ticker": row.ticker, "market": row.market, "reason": str(exc)})
                self.logger.exception("%s 종목 수집에 실패했습니다", row.ticker)
            finally:
                if self.config.sleep_seconds > 0:
                    time.sleep(self.config.sleep_seconds)

        self._write_failure_log(failures)
        self.logger.info("배치 수집을 완료했습니다: %s", stats)
        return stats

    def save_to_file(self, df: pd.DataFrame, market: str | None = None) -> Path:
        """종목별 수집 결과를 시장 폴더 아래 CSV 또는 Parquet 파일로 저장합니다."""

        ticker = str(df["ticker"].iloc[0])
        market_dir = str(market or df.get("market", pd.Series(["UNKNOWN"])).iloc[0] or "UNKNOWN")
        out_dir = self.config.data_dir / market_dir
        out_dir.mkdir(parents=True, exist_ok=True)

        path = out_dir / f"{ticker}.{self.config.file_format}"
        export_df = df[self.STANDARD_COLUMNS].copy()
        if self.config.file_format == "parquet":
            try:
                export_df.to_parquet(path, index=False)
            except ImportError as exc:
                raise RuntimeError("Parquet 저장에는 `pyarrow` 또는 `fastparquet`가 필요합니다.") from exc
        else:
            export_df.to_csv(path, index=False, encoding="utf-8")
        return path

    def save_to_db(self, df: pd.DataFrame) -> int:
        """기존 public.price_data 테이블에 일봉 데이터를 upsert합니다."""

        # trading_value는 DB의 기존 컬럼명 amount로 저장합니다.
        # adjusted_close는 국내 데이터에서 별도 조정종가가 없으면 close와 동일하게 들어갑니다.
        insert_query = """
            INSERT INTO public.price_data (
                date, ticker, open, high, low, close, volume, adjusted_close, amount
            )
            VALUES %s
            ON CONFLICT (date, ticker) DO UPDATE SET
                open = EXCLUDED.open,
                high = EXCLUDED.high,
                low = EXCLUDED.low,
                close = EXCLUDED.close,
                volume = EXCLUDED.volume,
                adjusted_close = EXCLUDED.adjusted_close,
                amount = EXCLUDED.amount;
        """
        records = [
            (
                row.date.date() if hasattr(row.date, "date") else row.date,
                row.ticker,
                _nullable_float(row.open),
                _nullable_float(row.high),
                _nullable_float(row.low),
                _nullable_float(row.close),
                int(row.volume) if pd.notna(row.volume) else 0,
                _nullable_float(row.adjusted_close),
                _nullable_float(row.amount),
            )
            for row in df.itertuples(index=False)
        ]
        if not records:
            return 0

        conn = get_db_conn(self.config.db_name)
        try:
            with conn.cursor() as cursor:
                execute_values(cursor, insert_query, records)
            conn.commit()
            return len(records)
        except Exception:
            conn.rollback()
            raise
        finally:
            conn.close()

    def _fetch_with_pykrx(self, ticker: str, start: str, end: str) -> pd.DataFrame:
        """pykrx에서 단일 종목 일봉 OHLCV를 조회합니다."""

        stock = _require_module("pykrx.stock", "pykrx")
        return stock.get_market_ohlcv_by_date(_as_yyyymmdd(start), _as_yyyymmdd(end), ticker)

    def _fetch_with_fdr(self, ticker: str, start: str, end: str) -> pd.DataFrame:
        """FinanceDataReader에서 단일 종목 일봉 OHLCV를 조회합니다."""

        fdr = _require_module("FinanceDataReader", "finance-datareader")
        return fdr.DataReader(ticker, start, end)

    def _normalize_ohlcv(self, raw: pd.DataFrame, ticker: str, market: str | None, name: str | None) -> pd.DataFrame:
        """소스별 컬럼명을 AI 파이프라인 표준 OHLCV 스키마로 통일합니다."""

        if raw is None or raw.empty:
            return pd.DataFrame(columns=self.STANDARD_COLUMNS)

        df = raw.copy()
        df.index = pd.to_datetime(df.index)

        # pykrx는 한글 컬럼, FinanceDataReader는 영문 컬럼을 사용하므로 한 번에 표준명으로 바꿉니다.
        df = df.rename(
            columns={
                "시가": "open",
                "고가": "high",
                "저가": "low",
                "종가": "close",
                "거래량": "volume",
                "거래대금": "trading_value",
                "Open": "open",
                "High": "high",
                "Low": "low",
                "Close": "close",
                "Volume": "volume",
                "Amount": "trading_value",
                "Adj Close": "adjusted_close",
            }
        )
        df = df.reset_index(names="date")

        # 소스마다 빠지는 컬럼이 있을 수 있어 필수 수치 컬럼을 항상 만들고 numeric으로 강제 변환합니다.
        for col in ["open", "high", "low", "close", "volume", "trading_value", "adjusted_close"]:
            if col not in df.columns:
                df[col] = None
            df[col] = pd.to_numeric(df[col], errors="coerce")

        # 거래대금이 없는 소스는 close * volume으로 근사합니다.
        missing_value = df["trading_value"].isna()
        df.loc[missing_value, "trading_value"] = df.loc[missing_value, "close"] * df.loc[missing_value, "volume"]

        # 기존 DB/모델 코드 호환을 위해 amount와 adjusted_close를 항상 채웁니다.
        df["amount"] = df["trading_value"]
        df["adjusted_close"] = df["adjusted_close"].fillna(df["close"])
        df["ticker"] = ticker
        df["market"] = market or "UNKNOWN"
        df["name"] = name or ""

        # 결측/비정상 가격 제거: 날짜나 종가가 없거나 종가가 0 이하인 row는 feature 입력으로 부적합합니다.
        df = df.dropna(subset=["date", "close"])
        df = df[df["close"] > 0]

        # 동일 날짜 중복이 있으면 마지막 값을 사용해 DB primary key(date, ticker) 충돌을 예방합니다.
        df = df.sort_values("date")
        df = df.drop_duplicates(subset=["date", "ticker"], keep="last")
        return df[self.STANDARD_COLUMNS].reset_index(drop=True)

    def _write_failure_log(self, failures: list[dict[str, str]]) -> Path | None:
        """배치 실패/빈 결과 종목을 CSV로 남겨 추후 재수집할 수 있게 합니다."""

        if not failures:
            return None

        path = self.config.log_dir / f"failed_tickers_{datetime.now().strftime('%Y%m%d_%H%M%S')}.csv"
        with path.open("w", newline="", encoding="utf-8") as f:
            writer = csv.DictWriter(f, fieldnames=["ticker", "market", "reason"])
            writer.writeheader()
            writer.writerows(failures)
        self.logger.warning("수집 실패 목록을 저장했습니다: %s", path)
        return path


def _nullable_float(value) -> float | None:
    """pandas NaN은 DB NULL로 들어가도록 None으로 바꿉니다."""

    if pd.isna(value):
        return None
    return float(value)
