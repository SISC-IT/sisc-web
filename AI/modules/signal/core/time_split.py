"""공통 날짜 경계로 분할하고 검증 기간에 걸친 학습 정답을 제거한다."""

import numpy as np
import pandas as pd


def resolve_validation_start(dates, validation_start=None, validation_fraction=0.2):
    """Use one calendar boundary for all tickers, inferred from unique dates if omitted."""
    dates = pd.DatetimeIndex(pd.to_datetime(dates)).normalize()
    if dates.hasnans or not 0 < validation_fraction < 1:
        raise ValueError("유효한 날짜와 0~1 사이 validation_fraction이 필요합니다.")
    unique_dates = dates.unique().sort_values()
    if len(unique_dates) < 2:
        raise ValueError("날짜 분할에는 서로 다른 날짜가 두 개 이상 필요합니다.")
    boundary = (
        pd.Timestamp(validation_start)
        if validation_start is not None
        else unique_dates[min(int(len(unique_dates) * (1 - validation_fraction)), len(unique_dates) - 1)]
    )
    if pd.isna(boundary) or not unique_dates[0] < boundary <= unique_dates[-1]:
        raise ValueError("validation_start는 데이터 시작일 이후, 마지막 날짜 이하여야 합니다.")
    return boundary.normalize()


def chronological_split(asof_dates, label_end_dates, validation_start):
    """Return train/validation masks, excluding train labels that reach validation.

    Both partitions must be nonempty. Callers must separately cap label dates
    before any reserved final evaluation period; this helper has no test boundary.
    """
    asof = pd.DatetimeIndex(pd.to_datetime(asof_dates)).normalize()
    label_end = pd.DatetimeIndex(pd.to_datetime(label_end_dates)).normalize()
    boundary = pd.Timestamp(validation_start)
    if len(asof) != len(label_end) or asof.hasnans or label_end.hasnans or pd.isna(boundary):
        raise ValueError("표본별 판단 날짜와 정답 종료 날짜가 필요합니다.")
    boundary = boundary.normalize()
    if np.any(label_end <= asof):
        raise ValueError("정답 종료 날짜는 판단 날짜보다 뒤여야 합니다.")
    train = (asof < boundary) & (label_end < boundary)
    validation = asof >= boundary
    if not train.any() or not validation.any():
        raise ValueError("날짜 분할 후 train 또는 validation 표본이 비었습니다. 날짜 경계를 확인하세요.")
    return np.asarray(train), np.asarray(validation)
