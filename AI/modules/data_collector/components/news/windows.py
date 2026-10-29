from __future__ import annotations

from datetime import datetime, timedelta, timezone


def event_news_window(
    accepted_at: datetime,
    *,
    hours_before: int = 24,
    hours_after: int = 48,
) -> tuple[datetime, datetime]:
    """향후 SEC 이벤트 연결기가 사용할 UTC 뉴스 구간을 계산합니다.

    이 순수 함수는 SEC 저장소에 의존하지 않습니다. SEC 담당자가 제공할
    ``accepted_at``을 나중에 넘기면 동일 계약을 그대로 사용할 수 있습니다.
    """

    reference = require_aware_utc(accepted_at, "accepted_at")
    if hours_before < 0 or hours_after < 0:
        raise ValueError("이벤트 전후 시간은 음수일 수 없습니다.")
    return (
        reference - timedelta(hours=hours_before),
        reference + timedelta(hours=hours_after),
    )


def forward_collection_window(
    end_at: datetime,
    *,
    lookback_hours: int,
    max_recovery_hours: int = 72,
) -> tuple[datetime, datetime]:
    """hourly/수동 실행에서 사용할 최근 뉴스 조회 구간을 계산합니다."""

    end_utc = require_aware_utc(end_at, "end_at")
    if lookback_hours <= 0:
        raise ValueError("lookback_hours는 0보다 커야 합니다.")
    if lookback_hours > max_recovery_hours:
        raise ValueError(
            f"Google RSS 복구 구간은 최대 {max_recovery_hours}시간입니다. "
            "5년 백필에는 역사 조회 provider가 필요합니다."
        )
    return end_utc - timedelta(hours=lookback_hours), end_utc


def require_aware_utc(value: datetime, field_name: str) -> datetime:
    if value.tzinfo is None or value.utcoffset() is None:
        raise ValueError(f"{field_name}은 timezone-aware datetime이어야 합니다.")
    return value.astimezone(timezone.utc)
