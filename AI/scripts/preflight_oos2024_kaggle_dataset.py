"""OOS2024 Kaggle 입력 데이터셋 사전 검증.

Transformer/iTransformer OOS2024 개발 학습은 2024-12-31까지의 데이터가
반드시 필요하고, 2025-01-01 이후 데이터는 포함하면 안 된다. 이 스크립트는
Kaggle dataset version 업로드 전과 Kaggle notebook 학습 전 둘 다에서 같은
검증을 수행한다.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any, Iterable, Mapping

import pandas as pd


REQUIRED_PARQUETS = [
    "price_data.parquet",
    "macroeconomic_indicators.parquet",
    "market_breadth.parquet",
    "sector_returns.parquet",
    "company_fundamentals.parquet",
    "stock_info.parquet",
]

DATE_PARQUETS = [
    "price_data.parquet",
    "macroeconomic_indicators.parquet",
    "market_breadth.parquet",
    "sector_returns.parquet",
    "company_fundamentals.parquet",
]

DEFAULT_HORIZONS = [1, 3, 5, 7]


def _timestamp(value: str, *, name: str) -> pd.Timestamp:
    try:
        return pd.Timestamp(value).normalize()
    except Exception as exc:
        raise ValueError(f"{name} 날짜를 해석할 수 없습니다: {value}") from exc


def _date_text(value: Any) -> str | None:
    if value is None or pd.isna(value):
        return None
    return str(pd.Timestamp(value).normalize().date())


def _jsonable(value: Any) -> Any:
    if isinstance(value, Mapping):
        return {str(key): _jsonable(item) for key, item in value.items()}
    if isinstance(value, (list, tuple)):
        return [_jsonable(item) for item in value]
    if isinstance(value, pd.Timestamp):
        return _date_text(value)
    if hasattr(value, "item"):
        try:
            return value.item()
        except Exception:
            return value
    return value


def _read_date_column(path: Path) -> pd.Series:
    frame = pd.read_parquet(path, columns=["date"])
    return pd.to_datetime(frame["date"], errors="coerce").dropna().dt.normalize()


def _date_file_summary(path: Path, *, eval_end: pd.Timestamp, holdout_start: pd.Timestamp) -> dict[str, Any]:
    date_series = _read_date_column(path)
    if date_series.empty:
        return {
            "row_count": 0,
            "data_min": None,
            "data_max": None,
            "rows_2025_plus": 0,
            "has_eval_end": False,
        }
    return {
        "row_count": int(len(date_series)),
        "data_min": _date_text(date_series.min()),
        "data_max": _date_text(date_series.max()),
        "rows_2025_plus": int((date_series >= holdout_start).sum()),
        "has_eval_end": bool(date_series.max() >= eval_end),
    }


def _ticker_row_counts(price_frame: pd.DataFrame, *, eval_start: pd.Timestamp, eval_end: pd.Timestamp) -> pd.DataFrame:
    grouped = price_frame.groupby("ticker", sort=True)
    rows = grouped.agg(
        row_count=("date", "size"),
        data_min=("date", "min"),
        data_max=("date", "max"),
    ).reset_index()
    eval_counts = (
        price_frame[(price_frame["date"] >= eval_start) & (price_frame["date"] <= eval_end)]
        .groupby("ticker", sort=True)
        .size()
        .rename("eval_row_count")
        .reset_index()
    )
    rows = rows.merge(eval_counts, on="ticker", how="left")
    rows["eval_row_count"] = rows["eval_row_count"].fillna(0).astype(int)
    rows["data_min"] = rows["data_min"].map(_date_text)
    rows["data_max"] = rows["data_max"].map(_date_text)
    return rows.sort_values("ticker").reset_index(drop=True)


def _forward_return_summary(
    price_frame: pd.DataFrame,
    *,
    train_start: pd.Timestamp,
    train_cutoff: pd.Timestamp,
    eval_start: pd.Timestamp,
    eval_end: pd.Timestamp,
    holdout_start: pd.Timestamp,
    horizons: Iterable[int],
) -> dict[str, Any]:
    train_counts = {int(horizon): 0 for horizon in horizons}
    eval_counts = {int(horizon): 0 for horizon in horizons}
    train_label_end_max: pd.Timestamp | None = None
    eval_label_end_max: pd.Timestamp | None = None

    clean_price = price_frame.dropna(subset=["ticker", "date", "close"]).copy()
    clean_price["ticker"] = clean_price["ticker"].astype(str)
    clean_price = clean_price.sort_values(["ticker", "date"])

    for _, group in clean_price.groupby("ticker", sort=True):
        group = group.sort_values("date").reset_index(drop=True)
        close_values = pd.to_numeric(group["close"], errors="coerce").to_numpy()
        date_values = pd.to_datetime(group["date"], errors="coerce").dt.normalize().tolist()

        for index, asof_date in enumerate(date_values):
            if pd.isna(asof_date):
                continue
            current_close = close_values[index]
            if pd.isna(current_close) or float(current_close) == 0.0:
                continue

            for horizon in horizons:
                horizon = int(horizon)
                label_index = index + horizon
                if label_index >= len(group):
                    continue

                label_end = pd.Timestamp(date_values[label_index]).normalize()
                future_close = close_values[label_index]
                if pd.isna(label_end) or pd.isna(future_close):
                    continue

                if train_start <= asof_date <= train_cutoff and label_end <= train_cutoff:
                    train_counts[horizon] += 1
                    train_label_end_max = (
                        label_end if train_label_end_max is None else max(train_label_end_max, label_end)
                    )

                if eval_start <= asof_date <= eval_end and label_end <= eval_end and label_end < holdout_start:
                    eval_counts[horizon] += 1
                    eval_label_end_max = label_end if eval_label_end_max is None else max(eval_label_end_max, label_end)

    return {
        "train_forward_return_rows_by_horizon": {str(key): int(value) for key, value in train_counts.items()},
        "eval_forward_return_rows_by_horizon": {str(key): int(value) for key, value in eval_counts.items()},
        "train_label_end_max": _date_text(train_label_end_max),
        "eval_label_end_max": _date_text(eval_label_end_max),
    }


def run_preflight(
    dataset_dir: str | Path,
    *,
    train_start: str = "2021-01-01",
    train_cutoff: str = "2024-06-30",
    eval_start: str = "2024-09-03",
    eval_end: str = "2024-12-31",
    holdout_start: str = "2025-01-01",
    horizons: Iterable[int] = DEFAULT_HORIZONS,
    summary_name: str = "oos2024_dataset_preflight_summary.json",
    ticker_counts_name: str = "oos2024_ticker_row_counts.csv",
    write_outputs: bool = True,
    strict: bool = True,
) -> dict[str, Any]:
    dataset_path = Path(dataset_dir)
    train_start_ts = _timestamp(train_start, name="train_start")
    train_cutoff_ts = _timestamp(train_cutoff, name="train_cutoff")
    eval_start_ts = _timestamp(eval_start, name="eval_start")
    eval_end_ts = _timestamp(eval_end, name="eval_end")
    holdout_start_ts = _timestamp(holdout_start, name="holdout_start")
    horizon_values = [int(horizon) for horizon in horizons]

    failures: list[str] = []
    missing_files = [name for name in REQUIRED_PARQUETS if not (dataset_path / name).exists()]
    if missing_files:
        failures.append(f"필수 parquet 누락: {missing_files}")

    date_summaries: dict[str, Any] = {}
    for name in DATE_PARQUETS:
        path = dataset_path / name
        if not path.exists():
            continue
        file_summary = _date_file_summary(path, eval_end=eval_end_ts, holdout_start=holdout_start_ts)
        date_summaries[name] = file_summary
        if not file_summary["has_eval_end"]:
            failures.append(f"{name} data_max가 {eval_end}보다 작습니다: {file_summary['data_max']}")
        if int(file_summary["rows_2025_plus"]) != 0:
            failures.append(f"{name}에 2025 이후 row가 있습니다: {file_summary['rows_2025_plus']}")

    price_summary: dict[str, Any] = {}
    ticker_count_summary: dict[str, Any] = {}
    forward_summary: dict[str, Any] = {}
    ticker_counts = pd.DataFrame()

    price_path = dataset_path / "price_data.parquet"
    if price_path.exists():
        price_frame = pd.read_parquet(price_path, columns=["ticker", "date", "close"])
        price_frame["date"] = pd.to_datetime(price_frame["date"], errors="coerce").dt.normalize()
        price_frame["ticker"] = price_frame["ticker"].astype(str)
        duplicate_count = int(price_frame.duplicated(["ticker", "date"]).sum())
        rows_2025_plus = int((price_frame["date"] >= holdout_start_ts).sum())
        rows_in_eval = int(((price_frame["date"] >= eval_start_ts) & (price_frame["date"] <= eval_end_ts)).sum())
        rows_after_train_cutoff = int((price_frame["date"] > train_cutoff_ts).sum())
        data_min = price_frame["date"].min()
        data_max = price_frame["date"].max()

        price_summary = {
            "data_min": _date_text(data_min),
            "data_max": _date_text(data_max),
            "row_count": int(len(price_frame)),
            "ticker_count": int(price_frame["ticker"].nunique()),
            "ticker_date_duplicate_count": duplicate_count,
            "rows_after_train_cutoff": rows_after_train_cutoff,
            "rows_in_eval_window": rows_in_eval,
            "rows_2025_plus": rows_2025_plus,
        }

        if pd.isna(data_max) or data_max < eval_end_ts:
            failures.append(f"DATA_DATE_MAX가 부족합니다: {price_summary['data_max']} < {eval_end}")
        if pd.isna(data_min) or data_min > train_start_ts:
            failures.append(f"DATA_DATE_MIN이 학습 시작일보다 늦습니다: {price_summary['data_min']} > {train_start}")
        if duplicate_count != 0:
            failures.append(f"price_data ticker/date 중복 row가 있습니다: {duplicate_count}")
        if rows_2025_plus != 0:
            failures.append(f"price_data에 2025 이후 row가 있습니다: {rows_2025_plus}")
        if rows_in_eval == 0:
            failures.append(f"eval window row가 없습니다: {eval_start}..{eval_end}")

        ticker_counts = _ticker_row_counts(price_frame, eval_start=eval_start_ts, eval_end=eval_end_ts)
        ticker_count_summary = {
            "ticker_count": int(len(ticker_counts)),
            "row_count_min": int(ticker_counts["row_count"].min()) if not ticker_counts.empty else 0,
            "row_count_median": float(ticker_counts["row_count"].median()) if not ticker_counts.empty else 0.0,
            "row_count_max": int(ticker_counts["row_count"].max()) if not ticker_counts.empty else 0,
            "eval_row_count_min": int(ticker_counts["eval_row_count"].min()) if not ticker_counts.empty else 0,
            "eval_row_count_median": float(ticker_counts["eval_row_count"].median()) if not ticker_counts.empty else 0.0,
            "eval_row_count_max": int(ticker_counts["eval_row_count"].max()) if not ticker_counts.empty else 0,
        }

        forward_summary = _forward_return_summary(
            price_frame,
            train_start=train_start_ts,
            train_cutoff=train_cutoff_ts,
            eval_start=eval_start_ts,
            eval_end=eval_end_ts,
            holdout_start=holdout_start_ts,
            horizons=horizon_values,
        )

        for horizon, count in forward_summary["eval_forward_return_rows_by_horizon"].items():
            if int(count) <= 0:
                failures.append(f"eval forward return 생성 불가: horizon={horizon}")

        train_label_end_max = forward_summary.get("train_label_end_max")
        eval_label_end_max = forward_summary.get("eval_label_end_max")
        if train_label_end_max and pd.Timestamp(train_label_end_max) > train_cutoff_ts:
            failures.append(f"train_label_end_max가 train_cutoff를 초과했습니다: {train_label_end_max}")
        if eval_label_end_max and pd.Timestamp(eval_label_end_max) > eval_end_ts:
            failures.append(f"eval_label_end_max가 eval_end를 초과했습니다: {eval_label_end_max}")
        if eval_label_end_max and pd.Timestamp(eval_label_end_max) >= holdout_start_ts:
            failures.append(f"eval_label_end_max가 holdout_start 이후입니다: {eval_label_end_max}")

    summary = {
        "dataset_dir": str(dataset_path),
        "required_files": list(REQUIRED_PARQUETS),
        "missing_files": missing_files,
        "train_start": train_start,
        "train_cutoff": train_cutoff,
        "eval_start": eval_start,
        "eval_end": eval_end,
        "holdout_start": holdout_start,
        "horizons": horizon_values,
        "date_file_summary": date_summaries,
        "price_data_summary": price_summary,
        "ticker_row_count_summary": ticker_count_summary,
        "forward_return_summary": forward_summary,
        "failures": failures,
        "passed": not failures,
    }

    if write_outputs:
        dataset_path.mkdir(parents=True, exist_ok=True)
        summary_path = dataset_path / summary_name
        with open(summary_path, "w", encoding="utf-8") as file:
            json.dump(_jsonable(summary), file, ensure_ascii=False, indent=2)
            file.write("\n")
        if not ticker_counts.empty:
            ticker_counts.to_csv(dataset_path / ticker_counts_name, index=False)

    if strict and failures:
        preview = "; ".join(failures[:5])
        if len(failures) > 5:
            preview += f"; 외 {len(failures) - 5}건"
        raise ValueError(f"OOS2024 Kaggle dataset preflight 실패: {preview}")

    return summary


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="OOS2024 Kaggle dataset preflight")
    parser.add_argument("--dataset-dir", default="AI/data/kaggle_data")
    parser.add_argument("--train-start", default="2021-01-01")
    parser.add_argument("--train-cutoff", default="2024-06-30")
    parser.add_argument("--eval-start", default="2024-09-03")
    parser.add_argument("--eval-end", default="2024-12-31")
    parser.add_argument("--holdout-start", default="2025-01-01")
    parser.add_argument("--summary-name", default="oos2024_dataset_preflight_summary.json")
    parser.add_argument("--ticker-counts-name", default="oos2024_ticker_row_counts.csv")
    parser.add_argument("--non-strict", action="store_true")
    parser.add_argument("--no-write", action="store_true")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    summary = run_preflight(
        args.dataset_dir,
        train_start=args.train_start,
        train_cutoff=args.train_cutoff,
        eval_start=args.eval_start,
        eval_end=args.eval_end,
        holdout_start=args.holdout_start,
        summary_name=args.summary_name,
        ticker_counts_name=args.ticker_counts_name,
        write_outputs=not args.no_write,
        strict=not args.non_strict,
    )
    print(json.dumps(_jsonable(summary), ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
