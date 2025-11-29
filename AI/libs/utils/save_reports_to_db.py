# libs/utils/save_reports_to_db.py
# -*- coding: utf-8 -*-
"""
한국어 주석:
- XAI 리포트 저장
- 체결/자산(cash) 업데이트 로직 없이, xai_reports 테이블에 INSERT만 수행한다.
- DB 스키마는 절대 변경하지 않는다(테이블/컬럼 생성/수정 X).
- 입력 rows 형식: List[Tuple[ticker, signal, price, date_str, report_text]]

변경점:
- 기존에는 "실제 INSERT 된 행 수(int)"를 반환했으나,
  이제는 "각 INSERT 행의 xai_reports.id 리스트(List[int])"를 반환한다.
  (rows의 순서와 id 리스트의 순서는 동일하다.)
"""

from __future__ import annotations
from typing import Iterable, Tuple, List
from datetime import datetime, timezone

from sqlalchemy import text
from libs.utils.get_db_conn import get_engine  # 프로젝트 표준 엔진 헬퍼

# (ticker, signal, price, date_str, report_text)
ReportRow = Tuple[str, str, float, str, str]


# ----- 유틸 -----
def utcnow() -> datetime:
    """한국어 주석: 현재 UTC 시간을 반환(생성 시각 created_at 기록용)."""
    return datetime.now(timezone.utc)


def _build_insert_params(rows: Iterable[ReportRow], created_at: datetime) -> List[dict]:
    """
    한국어 주석:
    - 파이프라인에서 넘어온 리포트 튜플들을 DB INSERT용 딕셔너리 리스트로 변환한다.
    - 방어적 필터링: ticker/signal/date 가 비어 있으면 해당 행은 건너뜀.
    """
    out: List[dict] = []
    for (ticker, signal, price, date_s, report_text) in rows:
        if not ticker or not signal or not date_s:
            continue
        out.append({
            "ticker": ticker,
            "signal": signal,
            "price": float(price),
            "date": date_s,                  # 'YYYY-MM-DD'
            "report": str(report_text),
            "created_at": created_at,        # TIMESTAMPTZ
        })
    return out


# ----- 메인: 리포트 저장(INSERT only) -----
def save_reports_to_db(rows: List[ReportRow], db_name: str) -> List[int]:
    """
    한국어 주석:
    - 입력된 XAI 리포트(rows)를 public.xai_reports 테이블에 INSERT 한다.
    - 테이블 스키마는 건드리지 않는다(생성/ALTER 하지 않음).
    - 반환값: 실제 INSERT 된 각 행의 xai_reports.id 리스트.
      (rows의 순서에서 유효한 행만 추려낸 순서와 동일)
    """
    if not rows:
        print("[INFO] 저장할 XAI 리포트가 없습니다.")
        return []

    engine = get_engine(db_name)
    created_at = utcnow()

    # INSERT 템플릿 (스키마는 기존 그대로 사용) + RETURNING id
    insert_sql = text("""
        INSERT INTO public.xai_reports (ticker, signal, price, date, report, created_at)
        VALUES (:ticker, :signal, :price, :date, :report, :created_at)
        RETURNING id
    """)

    inserted_ids: List[int] = []

    with engine.begin() as conn:
        params = _build_insert_params(rows, created_at)
        if not params:
            print("[WARN] 유효한 XAI 리포트 파라미터가 없어 저장을 생략합니다.")
            return []

        # 리포트 개수가 엄청 많지 않을 것으로 가정하고, id를 받기 위해 한 행씩 INSERT
        for p in params:
            result = conn.execute(insert_sql, p)
            new_id = result.scalar()
            if new_id is not None:
                inserted_ids.append(int(new_id))

    print(f"--- {len(inserted_ids)}개의 XAI 리포트가 저장되었습니다. ---")
    return inserted_ids
