"""TCN 전용 전처리 방어 유틸리티.

공통 `get_standard_training_data()`는 단일 ticker frame에서는 정상 동작하지만,
여러 ticker가 같은 날짜 인덱스를 공유한 상태로 들어오면 주봉/월봉 join에서 row가
증폭될 수 있다. TCN 학습/추론은 ticker별 전처리 후 합치는 경로만 사용한다.
"""

from __future__ import annotations

import numpy as np
import pandas as pd

from AI.modules.signal.core.dataset_builder import get_standard_training_data


TICKER_DATE_KEYS = ["ticker", "date"]
TECHNICAL_DAILY_V1 = "technical_daily_v1"
TCN_SHORT_HORIZON_V1 = "tcn_short_horizon_v1"

TECHNICAL_DAILY_V1_COLUMNS = [
    "log_return",
    "open_ratio",
    "high_ratio",
    "low_ratio",
    "vol_change",
    "ma5_ratio",
    "ma20_ratio",
    "ma60_ratio",
    "rsi",
    "macd_ratio",
    "bb_position",
]

TCN_SHORT_HORIZON_V1_COLUMNS = [
    "ret_1d",
    "ret_2d",
    "ret_3d",
    "ret_5d",
    "log_return",
    "vol_change",
    "volume_z_5",
    "volume_z_10",
    "intraday_volatility",
    "candle_body",
    "upper_wick",
    "lower_wick",
    "ma5_ratio",
    "ma10_ratio",
    "rolling_vol_5",
    "rsi_7",
    "rsi_14",
]

TCN_FEATURE_SET_COLUMNS = {
    TECHNICAL_DAILY_V1: TECHNICAL_DAILY_V1_COLUMNS,
    TCN_SHORT_HORIZON_V1: TCN_SHORT_HORIZON_V1_COLUMNS,
}

SUPPORTED_TCN_FEATURE_SET_VERS = tuple(TCN_FEATURE_SET_COLUMNS.keys())


def log_ticker_date_counts(frame: pd.DataFrame, *, stage_name: str) -> dict[str, int]:
    """전처리 단계별 row 수와 ticker/date 중복 수를 출력한다."""
    stats = _ticker_date_stats(frame)
    print(
        f"{stage_name} row count - "
        f"rows={stats['rows']}, "
        f"tickers={stats['tickers']}, "
        f"unique_ticker_date={stats['unique_ticker_date']}, "
        f"duplicate_rows={stats['duplicate_rows']}"
    )
    return stats


def validate_unique_ticker_date(
    frame: pd.DataFrame,
    *,
    stage_name: str,
    allow_exact_duplicate_drop: bool = False,
) -> pd.DataFrame:
    """ticker/date 단위가 유일한지 검증한다.

    기본 정책은 중복 발견 시 즉시 ValueError다. 완전히 동일한 중복 제거는
    함수 옵션으로만 열어두고, TCN 학습/추론 경로에서는 기본값 False를 사용한다.
    """
    _require_ticker_date_columns(frame, stage_name)
    checked = _normalize_date_column(frame)
    duplicated = checked.duplicated(TICKER_DATE_KEYS, keep=False)
    if not duplicated.any():
        return checked

    duplicate_rows = checked.loc[duplicated].copy()
    duplicate_keys = (
        duplicate_rows[TICKER_DATE_KEYS]
        .drop_duplicates()
        .sort_values(TICKER_DATE_KEYS)
        .head(10)
        .to_dict("records")
    )
    if allow_exact_duplicate_drop:
        deduped = checked.drop_duplicates().copy()
        remaining = deduped.duplicated(TICKER_DATE_KEYS, keep=False)
        if not remaining.any():
            print(
                f"{stage_name} - 완전히 동일한 ticker/date 중복 row "
                f"{int(duplicated.sum())}개를 제거했습니다."
            )
            return deduped.sort_values(TICKER_DATE_KEYS).reset_index(drop=True)

    raise ValueError(
        f"{stage_name}에서 ticker/date 중복이 발견되었습니다. "
        f"duplicate_rows={int(duplicated.sum())}, "
        f"duplicate_keys_sample={duplicate_keys}"
    )


def prepare_tcn_standard_data(
    raw_frame: pd.DataFrame,
    *,
    stage_name: str,
    feature_set_ver: str = TECHNICAL_DAILY_V1,
    allow_exact_duplicate_drop: bool = False,
    fill_missing_features: bool = True,
) -> pd.DataFrame:
    """TCN 학습/추론용 표준 피처를 ticker별로 생성한다."""
    feature_set_ver = normalize_tcn_feature_set_ver(feature_set_ver)
    raw_checked = validate_unique_ticker_date(
        raw_frame,
        stage_name=f"{stage_name} raw",
        allow_exact_duplicate_drop=allow_exact_duplicate_drop,
    )
    log_ticker_date_counts(raw_checked, stage_name=f"{stage_name} raw")

    processed_frames: list[pd.DataFrame] = []
    for ticker, ticker_frame in raw_checked.groupby("ticker", sort=True):
        ticker_processed = get_standard_training_data(ticker_frame.sort_values("date").copy())
        ticker_processed = validate_unique_ticker_date(
            ticker_processed,
            stage_name=f"{stage_name} processed ticker={ticker}",
            allow_exact_duplicate_drop=allow_exact_duplicate_drop,
        )
        validate_processed_row_count(
            ticker_frame,
            ticker_processed,
            stage_name=f"{stage_name} processed ticker={ticker}",
        )
        ticker_processed = prepare_tcn_feature_set(
            ticker_processed,
            feature_set_ver=feature_set_ver,
            stage_name=f"{stage_name} feature_set={feature_set_ver} ticker={ticker}",
            fill_missing_features=fill_missing_features,
        )
        processed_frames.append(ticker_processed)

    if not processed_frames:
        raise ValueError(f"{stage_name}에서 전처리된 ticker 데이터가 없습니다.")

    processed = pd.concat(processed_frames, ignore_index=True)
    processed = validate_unique_ticker_date(
        processed,
        stage_name=f"{stage_name} processed",
        allow_exact_duplicate_drop=allow_exact_duplicate_drop,
    )
    validate_processed_row_count(raw_checked, processed, stage_name=f"{stage_name} processed")
    processed = processed.sort_values(TICKER_DATE_KEYS).reset_index(drop=True)
    log_ticker_date_counts(processed, stage_name=f"{stage_name} processed")
    return processed


def normalize_tcn_feature_set_ver(feature_set_ver: str | None) -> str:
    """metadata가 없는 과거 artifact는 technical_daily_v1로 해석한다."""
    if feature_set_ver in (None, ""):
        return TECHNICAL_DAILY_V1
    if feature_set_ver not in TCN_FEATURE_SET_COLUMNS:
        raise ValueError(
            f"지원하지 않는 TCN feature_set_ver입니다: {feature_set_ver}. "
            f"지원 목록={list(SUPPORTED_TCN_FEATURE_SET_VERS)}"
        )
    return str(feature_set_ver)


def get_tcn_feature_columns(feature_set_ver: str | None = TECHNICAL_DAILY_V1) -> list[str]:
    """feature_set_ver에 대응하는 TCN 입력 컬럼 순서를 반환한다."""
    normalized = normalize_tcn_feature_set_ver(feature_set_ver)
    return list(TCN_FEATURE_SET_COLUMNS[normalized])


def validate_tcn_feature_set_contract(
    feature_set_ver: str | None,
    feature_columns: list[str] | tuple[str, ...] | None = None,
    feature_count: int | None = None,
    *,
    stage_name: str,
) -> tuple[str, list[str]]:
    """feature_set_ver, feature_columns, feature_count의 계약이 같은지 검증한다."""
    normalized = normalize_tcn_feature_set_ver(feature_set_ver)
    expected_columns = get_tcn_feature_columns(normalized)
    resolved_columns = expected_columns if feature_columns is None else list(feature_columns)

    if resolved_columns != expected_columns:
        raise ValueError(
            f"{stage_name} feature_columns가 feature_set_ver={normalized} 계약과 다릅니다. "
            f"expected={expected_columns}, actual={resolved_columns}"
        )
    if feature_count is not None and int(feature_count) != len(resolved_columns):
        raise ValueError(
            f"{stage_name} feature_count가 feature_columns 길이와 다릅니다. "
            f"feature_count={feature_count}, columns={len(resolved_columns)}"
        )
    return normalized, resolved_columns


def prepare_tcn_feature_set(
    frame: pd.DataFrame,
    *,
    feature_set_ver: str = TECHNICAL_DAILY_V1,
    stage_name: str,
    fill_missing_features: bool = True,
) -> pd.DataFrame:
    """선택한 TCN feature set을 생성하고 입력 컬럼 품질을 검증한다."""
    normalized = normalize_tcn_feature_set_ver(feature_set_ver)
    prepared = frame.copy()

    if normalized == TCN_SHORT_HORIZON_V1:
        prepared = add_tcn_short_horizon_features(prepared)

    feature_columns = get_tcn_feature_columns(normalized)
    validate_tcn_feature_columns(prepared, feature_columns, stage_name=stage_name)
    prepared[feature_columns] = prepared[feature_columns].replace([np.inf, -np.inf], np.nan)
    if fill_missing_features:
        prepared[feature_columns] = prepared[feature_columns].fillna(0.0)
        validate_tcn_feature_values(prepared, feature_columns, stage_name=stage_name)
    return prepared


def add_tcn_short_horizon_features(frame: pd.DataFrame) -> pd.DataFrame:
    """1일/3일 예측 목적의 빠른 가격/거래량 피처를 ticker별로 계산한다."""
    required = ["ticker", "date", "open", "high", "low", "close", "volume"]
    missing = [column for column in required if column not in frame.columns]
    if missing:
        raise ValueError(f"tcn_short_horizon_v1 생성에 필요한 원천 컬럼이 없습니다: {missing}")

    checked = _normalize_date_column(frame)
    processed_parts: list[pd.DataFrame] = []
    for _, ticker_frame in checked.groupby("ticker", sort=True):
        sub = ticker_frame.sort_values("date").copy()
        close = sub["close"].astype(float)
        open_ = sub["open"].astype(float)
        high = sub["high"].astype(float)
        low = sub["low"].astype(float)
        volume = sub["volume"].astype(float)
        epsilon = 1e-9

        sub["ret_1d"] = close.pct_change()
        sub["ret_2d"] = close.pct_change(periods=2)
        sub["ret_3d"] = close.pct_change(periods=3)
        sub["ret_5d"] = close.pct_change(periods=5)
        sub["log_return"] = np.log(close / close.shift(1))
        sub["vol_change"] = volume.pct_change()

        for window in (5, 10):
            volume_mean = volume.rolling(window=window).mean()
            volume_std = volume.rolling(window=window).std()
            sub[f"volume_z_{window}"] = (volume - volume_mean) / (volume_std.replace(0, np.nan) + epsilon)

        price_range = (high - low).replace(0, np.nan)
        candle_high_body = pd.concat([open_, close], axis=1).max(axis=1)
        candle_low_body = pd.concat([open_, close], axis=1).min(axis=1)
        sub["intraday_volatility"] = (high - low) / (close + epsilon)
        sub["candle_body"] = (close - open_) / (open_ + epsilon)
        sub["upper_wick"] = (high - candle_high_body).clip(lower=0) / (price_range + epsilon)
        sub["lower_wick"] = (candle_low_body - low).clip(lower=0) / (price_range + epsilon)

        ma5 = close.rolling(window=5).mean()
        ma10 = close.rolling(window=10).mean()
        sub["ma5_ratio"] = (close - ma5) / (ma5 + epsilon)
        sub["ma10_ratio"] = (close - ma10) / (ma10 + epsilon)
        sub["rolling_vol_5"] = sub["ret_1d"].rolling(window=5).std()
        sub["rsi_7"] = _compute_rsi(close, period=7) / 100.0
        sub["rsi_14"] = _compute_rsi(close, period=14) / 100.0

        processed_parts.append(sub)

    if not processed_parts:
        raise ValueError("tcn_short_horizon_v1 피처를 생성할 데이터가 없습니다.")
    return pd.concat(processed_parts, ignore_index=True)


def validate_tcn_feature_columns(
    frame: pd.DataFrame,
    feature_columns: list[str],
    *,
    stage_name: str,
) -> None:
    """필수 피처 컬럼이 없으면 0으로 대체하지 않고 즉시 실패한다."""
    missing = [column for column in feature_columns if column not in frame.columns]
    if missing:
        raise ValueError(f"{stage_name}에 필요한 TCN 피처 컬럼이 없습니다: {missing}")


def validate_tcn_feature_values(
    frame: pd.DataFrame,
    feature_columns: list[str],
    *,
    stage_name: str,
) -> None:
    """학습/추론 입력 피처에 NaN 또는 무한대가 남아 있는지 검증한다."""
    feature_frame = frame[feature_columns]
    if feature_frame.isna().any().any():
        nan_columns = feature_frame.columns[feature_frame.isna().any()].tolist()
        raise ValueError(f"{stage_name} 피처에 NaN이 남아 있습니다: {nan_columns}")
    values = feature_frame.to_numpy(dtype=float)
    if not np.isfinite(values).all():
        raise ValueError(f"{stage_name} 피처에 무한대 값이 남아 있습니다.")


def validate_processed_row_count(
    raw_frame: pd.DataFrame,
    processed_frame: pd.DataFrame,
    *,
    stage_name: str,
) -> None:
    """전처리 결과 row 수가 원본보다 늘어나면 중복 폭증으로 보고 실패한다."""
    raw_count = len(raw_frame)
    processed_count = len(processed_frame)
    if processed_count > raw_count:
        raise ValueError(
            f"{stage_name} row 수가 원본보다 증가했습니다. "
            f"raw_rows={raw_count}, processed_rows={processed_count}"
        )


def _ticker_date_stats(frame: pd.DataFrame) -> dict[str, int]:
    _require_ticker_date_columns(frame, "ticker/date 통계")
    checked = _normalize_date_column(frame)
    return {
        "rows": int(len(checked)),
        "tickers": int(checked["ticker"].nunique()),
        "unique_ticker_date": int(checked[TICKER_DATE_KEYS].drop_duplicates().shape[0]),
        "duplicate_rows": int(checked.duplicated(TICKER_DATE_KEYS).sum()),
    }


def _require_ticker_date_columns(frame: pd.DataFrame, stage_name: str) -> None:
    if not isinstance(frame, pd.DataFrame):
        raise TypeError(f"{stage_name} frame은 pandas.DataFrame이어야 합니다.")
    missing = [column for column in TICKER_DATE_KEYS if column not in frame.columns]
    if missing:
        raise ValueError(f"{stage_name}에 필요한 컬럼이 없습니다: {missing}")


def _normalize_date_column(frame: pd.DataFrame) -> pd.DataFrame:
    checked = frame.copy()
    checked["ticker"] = checked["ticker"].astype(str)
    checked["date"] = pd.to_datetime(checked["date"], errors="raise").dt.normalize()
    return checked


def _compute_rsi(series: pd.Series, period: int) -> pd.Series:
    delta = series.diff()
    gain = delta.where(delta > 0, 0.0).rolling(window=period).mean()
    loss = (-delta.where(delta < 0, 0.0)).rolling(window=period).mean()
    rs = gain / (loss + 1e-9)
    return 100 - (100 / (1 + rs))
