"""Signal Schema v0 기반 최소 평가 지표."""

from __future__ import annotations

import math
from typing import Any

import numpy as np
import pandas as pd


def _as_float_array(values: Any, name: str) -> np.ndarray:
    """입력값을 1차원 float 배열로 변환한다."""
    array = np.asarray(values, dtype=float)
    if array.ndim != 1:
        array = array.reshape(-1)
    if array.size == 0:
        raise ValueError(f"{name}은 비어 있을 수 없습니다.")
    if not np.isfinite(array).all():
        raise ValueError(f"{name}에는 NaN 또는 무한대가 포함될 수 없습니다.")
    return array


def _validate_binary_labels(y_true: Any) -> np.ndarray:
    """0/1 라벨만 허용한다."""
    labels = _as_float_array(y_true, "y_true")
    invalid_mask = ~np.isin(labels, [0.0, 1.0])
    if invalid_mask.any():
        raise ValueError("y_true는 0 또는 1만 포함해야 합니다.")
    return labels.astype(int)


def _validate_probabilities(prob_up: Any) -> np.ndarray:
    """확률값이 0~1 범위인지 검증한다."""
    probabilities = _as_float_array(prob_up, "prob_up")
    if ((probabilities < 0.0) | (probabilities > 1.0)).any():
        raise ValueError("prob_up은 0 이상 1 이하여야 합니다.")
    return probabilities


def _validate_same_length(left: np.ndarray, right: np.ndarray, left_name: str, right_name: str) -> None:
    if len(left) != len(right):
        raise ValueError(f"{left_name}와 {right_name}의 길이가 같아야 합니다.")


def _safe_divide(numerator: float, denominator: float) -> float | None:
    if denominator == 0:
        return None
    return float(numerator / denominator)


def classification_metrics(
    y_true,
    prob_up,
    *,
    threshold: float = 0.5,
    eps: float = 1e-12,
) -> dict:
    """상승 확률의 이진 분류 성능을 계산한다."""
    labels = _validate_binary_labels(y_true)
    probabilities = _validate_probabilities(prob_up)
    _validate_same_length(labels, probabilities, "y_true", "prob_up")

    threshold = float(threshold)
    eps = float(eps)
    if not 0.0 <= threshold <= 1.0:
        raise ValueError("threshold는 0 이상 1 이하여야 합니다.")
    if not 0.0 < eps < 0.5:
        raise ValueError("eps는 0보다 크고 0.5보다 작아야 합니다.")

    predictions = (probabilities >= threshold).astype(int)
    tp = int(((predictions == 1) & (labels == 1)).sum())
    fp = int(((predictions == 1) & (labels == 0)).sum())
    tn = int(((predictions == 0) & (labels == 0)).sum())
    fn = int(((predictions == 0) & (labels == 1)).sum())

    precision = _safe_divide(tp, tp + fp)
    recall = _safe_divide(tp, tp + fn)
    if precision is None or recall is None or precision + recall == 0:
        f1 = None
    else:
        f1 = float(2 * precision * recall / (precision + recall))

    clipped = np.clip(probabilities, eps, 1.0 - eps)
    log_loss = -np.mean(labels * np.log(clipped) + (1 - labels) * np.log(1 - clipped))

    return {
        "count": int(len(labels)),
        "positive_rate": float(labels.mean()),
        "brier_score": float(np.mean((probabilities - labels) ** 2)),
        "log_loss": float(log_loss),
        "accuracy": float((tp + tn) / len(labels)),
        "precision": precision,
        "recall": recall,
        "f1": f1,
    }


def high_confidence_metrics(
    y_true,
    prob_up,
    *,
    confidence_threshold: float = 0.2,
    threshold: float = 0.5,
) -> dict:
    """확신도가 높은 구간만 따로 분리해 성능을 계산한다."""
    labels = _validate_binary_labels(y_true)
    probabilities = _validate_probabilities(prob_up)
    _validate_same_length(labels, probabilities, "y_true", "prob_up")

    confidence_threshold = float(confidence_threshold)
    threshold = float(threshold)
    if not 0.0 <= confidence_threshold <= 1.0:
        raise ValueError("confidence_threshold는 0 이상 1 이하여야 합니다.")
    if not 0.0 <= threshold <= 1.0:
        raise ValueError("threshold는 0 이상 1 이하여야 합니다.")

    confidence = np.abs(probabilities - 0.5) * 2.0
    # 0.6, 0.4처럼 사람이 보기에는 정확히 0.2인 값이 부동소수점 오차로
    # threshold 바로 아래로 떨어지는 일을 막는다.
    selected = confidence + 1e-12 >= confidence_threshold
    selected_count = int(selected.sum())
    coverage = float(selected_count / len(labels))
    if selected_count == 0:
        return {
            "coverage": coverage,
            "count": 0,
            "precision": None,
            "accuracy": None,
            "avg_confidence": None,
        }

    selected_labels = labels[selected]
    selected_probabilities = probabilities[selected]
    selected_predictions = (selected_probabilities >= threshold).astype(int)
    tp = int(((selected_predictions == 1) & (selected_labels == 1)).sum())
    fp = int(((selected_predictions == 1) & (selected_labels == 0)).sum())

    return {
        "coverage": coverage,
        "count": selected_count,
        "precision": _safe_divide(tp, tp + fp),
        "accuracy": float((selected_predictions == selected_labels).mean()),
        "avg_confidence": float(confidence[selected].mean()),
    }


def _require_columns(frame: pd.DataFrame, columns: list[str], frame_name: str) -> None:
    missing = [column for column in columns if column not in frame.columns]
    if missing:
        raise ValueError(f"{frame_name}에 필요한 컬럼이 없습니다: {missing}")


def _require_non_null(frame: pd.DataFrame, columns: list[str], frame_name: str) -> None:
    null_columns = [column for column in columns if frame[column].isna().any()]
    if null_columns:
        raise ValueError(f"{frame_name}에 결측값이 있는 컬럼이 있습니다: {null_columns}")


def _require_unique_keys(frame: pd.DataFrame, keys: list[str], frame_name: str) -> None:
    duplicated_rows = frame.duplicated(subset=keys, keep=False)
    if duplicated_rows.any():
        duplicate_keys = (
            frame.loc[duplicated_rows, keys]
            .drop_duplicates()
            .to_dict("records")
        )
        raise ValueError(f"{frame_name}의 조인 키가 중복되었습니다: {duplicate_keys}")


def _spearman_correlation(left: pd.Series, right: pd.Series) -> float | None:
    """pandas rank를 사용해 Spearman correlation을 계산한다."""
    if len(left) < 2:
        return None
    left_rank = left.rank(method="average")
    right_rank = right.rank(method="average")
    if left_rank.nunique(dropna=True) < 2 or right_rank.nunique(dropna=True) < 2:
        return None
    value = left_rank.corr(right_rank)
    if pd.isna(value):
        return None
    return float(value)


def ranking_metrics(
    signal_frame,
    returns_frame,
    *,
    k: int = 5,
) -> dict:
    """날짜별 예측 순위가 실현 수익률 순위와 맞는지 평가한다."""
    if not isinstance(signal_frame, pd.DataFrame):
        raise TypeError("signal_frame은 pandas.DataFrame이어야 합니다.")
    if not isinstance(returns_frame, pd.DataFrame):
        raise TypeError("returns_frame은 pandas.DataFrame이어야 합니다.")

    required_signal_columns = ["asof_date", "ticker", "horizon", "prob_up"]
    required_return_columns = ["asof_date", "ticker", "horizon", "forward_return"]
    _require_columns(signal_frame, required_signal_columns, "signal_frame")
    _require_columns(returns_frame, required_return_columns, "returns_frame")
    _require_non_null(signal_frame, required_signal_columns, "signal_frame")
    _require_non_null(returns_frame, required_return_columns, "returns_frame")
    join_keys = ["asof_date", "ticker", "horizon"]
    _require_unique_keys(signal_frame, join_keys, "signal_frame")
    _require_unique_keys(returns_frame, join_keys, "returns_frame")

    k = int(k)
    if k <= 0:
        raise ValueError("k는 1 이상이어야 합니다.")

    merged = signal_frame[required_signal_columns].merge(
        returns_frame[required_return_columns],
        on=["asof_date", "ticker", "horizon"],
        how="inner",
    )
    if merged.empty:
        raise ValueError("signal_frame과 returns_frame을 조인한 결과가 비어 있습니다.")

    merged["prob_up"] = _validate_probabilities(merged["prob_up"].to_numpy())
    merged["forward_return"] = _as_float_array(
        merged["forward_return"].to_numpy(),
        "forward_return",
    )

    top_returns: list[float] = []
    bottom_returns: list[float] = []
    rank_ics: list[float] = []
    group_count = 0

    # 여러 horizon이 섞일 수 있으므로 날짜와 horizon을 함께 평가 단위로 묶는다.
    # 종목 수가 k보다 작은 묶음은 제외하지 않고 가능한 전체 종목 수를 사용한다.
    for _, group in merged.groupby(["asof_date", "horizon"], sort=True):
        if group.empty:
            continue
        group_count += 1
        take_count = min(k, len(group))
        sorted_group = group.sort_values("prob_up", ascending=False)
        top_returns.append(float(sorted_group.head(take_count)["forward_return"].mean()))
        bottom_returns.append(float(sorted_group.tail(take_count)["forward_return"].mean()))

        rank_ic = _spearman_correlation(group["prob_up"], group["forward_return"])
        if rank_ic is not None:
            rank_ics.append(rank_ic)

    top_k_mean = float(np.mean(top_returns)) if top_returns else None
    bottom_k_mean = float(np.mean(bottom_returns)) if bottom_returns else None
    if top_k_mean is None or bottom_k_mean is None:
        spread = None
    else:
        spread = float(top_k_mean - bottom_k_mean)

    return {
        "count_groups": int(group_count),
        "count_rows": int(len(merged)),
        "top_k_mean_return": top_k_mean,
        "bottom_k_mean_return": bottom_k_mean,
        "top_bottom_spread": spread,
        "rank_ic_mean": float(np.mean(rank_ics)) if rank_ics else None,
        "rank_ic_std": float(np.std(rank_ics, ddof=1)) if len(rank_ics) > 1 else (0.0 if rank_ics else None),
    }


def avoid_filter_metrics(
    signal_frame,
    returns_frame,
    *,
    buy_threshold: float = 0.6,
    sell_threshold: float = 0.4,
    confidence_threshold: float = 0.2,
) -> dict:
    """고확신 buy/sell bucket의 realized return 차이를 계산한다."""
    required_signal_columns = ["asof_date", "ticker", "horizon", "prob_up"]
    required_return_columns = ["asof_date", "ticker", "horizon", "forward_return"]
    signal_frame = pd.DataFrame(signal_frame).copy()
    returns_frame = pd.DataFrame(returns_frame).copy()
    _require_columns(signal_frame, required_signal_columns, "signal_frame")
    _require_columns(returns_frame, required_return_columns, "returns_frame")
    _require_non_null(signal_frame, required_signal_columns, "signal_frame")
    _require_non_null(returns_frame, required_return_columns, "returns_frame")

    buy_threshold = float(buy_threshold)
    sell_threshold = float(sell_threshold)
    confidence_threshold = float(confidence_threshold)
    if not 0.0 <= sell_threshold < buy_threshold <= 1.0:
        raise ValueError("buy_threshold는 sell_threshold보다 크고 둘 다 0~1 범위여야 합니다.")
    if not 0.0 <= confidence_threshold <= 1.0:
        raise ValueError("confidence_threshold는 0 이상 1 이하여야 합니다.")

    if "confidence" not in signal_frame.columns:
        signal_frame["confidence"] = signal_frame["prob_up"].astype(float).sub(0.5).abs() * 2.0

    join_keys = ["asof_date", "ticker", "horizon"]
    signal_frame["asof_date"] = pd.to_datetime(signal_frame["asof_date"], errors="raise").dt.normalize()
    returns_frame["asof_date"] = pd.to_datetime(returns_frame["asof_date"], errors="raise").dt.normalize()
    signal_frame["horizon"] = signal_frame["horizon"].astype(int)
    returns_frame["horizon"] = returns_frame["horizon"].astype(int)
    _require_unique_keys(signal_frame, join_keys, "signal_frame")
    _require_unique_keys(returns_frame, join_keys, "returns_frame")

    merged = signal_frame[required_signal_columns + ["confidence"]].merge(
        returns_frame[required_return_columns],
        on=join_keys,
        how="inner",
    )
    if merged.empty:
        raise ValueError("signal_frame과 returns_frame을 조인한 결과가 비어 있습니다.")

    merged["prob_up"] = _validate_probabilities(merged["prob_up"].to_numpy())
    merged["confidence"] = _validate_probabilities(merged["confidence"].to_numpy())
    merged["forward_return"] = _as_float_array(merged["forward_return"].to_numpy(), "forward_return")

    high_confidence = merged["confidence"] >= confidence_threshold
    buy_mask = (merged["prob_up"] >= buy_threshold) & high_confidence
    sell_mask = (merged["prob_up"] <= sell_threshold) & high_confidence

    buy_count = int(buy_mask.sum())
    sell_count = int(sell_mask.sum())
    row_count = int(len(merged))
    buy_mean = float(merged.loc[buy_mask, "forward_return"].mean()) if buy_count > 0 else None
    sell_mean = float(merged.loc[sell_mask, "forward_return"].mean()) if sell_count > 0 else None
    spread = None
    if buy_mean is not None and sell_mean is not None:
        spread = float(buy_mean - sell_mean)

    return {
        "count_rows": row_count,
        "buy_bucket_count": buy_count,
        "sell_bucket_count": sell_count,
        "buy_bucket_coverage": float(buy_count / row_count),
        "sell_bucket_coverage": float(sell_count / row_count),
        "buy_bucket_mean_return": buy_mean,
        "sell_bucket_mean_return": sell_mean,
        "avoid_filter_spread": spread,
        "avoided_loss_mean": float(-sell_mean) if sell_mean is not None else None,
    }


def calibration_metrics(
    y_true,
    prob_up,
    *,
    n_bins: int = 10,
) -> dict:
    """예측 확률의 calibration 품질을 계산한다."""
    labels = _validate_binary_labels(y_true)
    probabilities = _validate_probabilities(prob_up)
    _validate_same_length(labels, probabilities, "y_true", "prob_up")

    n_bins = int(n_bins)
    if n_bins < 2:
        raise ValueError("n_bins는 2 이상이어야 합니다.")

    edges = np.linspace(0.0, 1.0, n_bins + 1)
    bins: list[dict] = []
    ece = 0.0
    mce = 0.0

    for index in range(n_bins):
        start = float(edges[index])
        end = float(edges[index + 1])
        if index == n_bins - 1:
            mask = (probabilities >= start) & (probabilities <= end)
        else:
            mask = (probabilities >= start) & (probabilities < end)
        count = int(mask.sum())
        if count == 0:
            continue

        avg_prob = float(probabilities[mask].mean())
        actual_rate = float(labels[mask].mean())
        gap = abs(avg_prob - actual_rate)
        ece += (count / len(labels)) * gap
        mce = max(mce, gap)
        bins.append(
            {
                "bin_start": start,
                "bin_end": end,
                "count": count,
                "avg_prob": avg_prob,
                "actual_rate": actual_rate,
            }
        )

    return {
        "ece": float(ece),
        "mce": float(mce),
        "brier_score": float(np.mean((probabilities - labels) ** 2)),
        "bins": bins,
    }


def _max_drawdown(equity: np.ndarray) -> float:
    peaks = np.maximum.accumulate(equity)
    drawdowns = equity / peaks - 1.0
    return float(drawdowns.min())


def portfolio_metrics(
    equity_curve,
    trades=None,
    *,
    periods_per_year: float = 252,
) -> dict:
    """equity curve와 선택적 trade log로 포트폴리오 지표를 계산한다."""
    if not isinstance(equity_curve, pd.DataFrame):
        raise TypeError("equity_curve는 pandas.DataFrame이어야 합니다.")
    _require_columns(equity_curve, ["date", "equity"], "equity_curve")

    periods_per_year = float(periods_per_year)
    if periods_per_year <= 0:
        raise ValueError("periods_per_year는 0보다 커야 합니다.")

    curve = equity_curve[["date", "equity"]].copy().sort_values("date").reset_index(drop=True)
    if curve.empty:
        raise ValueError("equity_curve는 비어 있을 수 없습니다.")

    equity = _as_float_array(curve["equity"].to_numpy(), "equity")
    if (equity <= 0.0).any():
        raise ValueError("equity는 모두 양수여야 합니다.")

    start_equity = float(equity[0])
    end_equity = float(equity[-1])
    cumulative_return = float(end_equity / start_equity - 1.0)

    result = {
        "start_equity": start_equity,
        "end_equity": end_equity,
        "cumulative_return": cumulative_return,
        "annualized_return": None,
        "annualized_volatility": None,
        "sharpe": None,
        "mdd": _max_drawdown(equity),
        "calmar": None,
        "turnover": None,
        "cost_paid": None,
    }

    periods = len(equity) - 1
    if periods > 0:
        result["annualized_return"] = float((end_equity / start_equity) ** (periods_per_year / periods) - 1.0)
        returns = pd.Series(equity).pct_change().dropna().to_numpy(dtype=float)
        if len(returns) > 1:
            annualized_volatility = float(np.std(returns, ddof=1) * math.sqrt(periods_per_year))
            result["annualized_volatility"] = annualized_volatility
            if annualized_volatility > 0.0:
                result["sharpe"] = float(np.mean(returns) / np.std(returns, ddof=1) * math.sqrt(periods_per_year))

    mdd = result["mdd"]
    annualized_return = result["annualized_return"]
    if annualized_return is not None and mdd is not None and mdd < 0.0:
        result["calmar"] = float(annualized_return / abs(mdd))

    if trades is not None:
        if not isinstance(trades, pd.DataFrame):
            raise TypeError("trades는 pandas.DataFrame이어야 합니다.")
        if trades.empty:
            result["turnover"] = 0.0
            result["cost_paid"] = 0.0
        elif "turnover" in trades.columns:
            turnover_values = _as_float_array(trades["turnover"].to_numpy(), "turnover")
            result["turnover"] = float(turnover_values.sum())
        elif "notional" in trades.columns:
            notional_values = np.abs(_as_float_array(trades["notional"].to_numpy(), "notional"))
            result["turnover"] = float(notional_values.sum() / start_equity)

        if not trades.empty and "cost" in trades.columns:
            cost_values = _as_float_array(trades["cost"].to_numpy(), "cost")
            result["cost_paid"] = float(cost_values.sum())

    return result
