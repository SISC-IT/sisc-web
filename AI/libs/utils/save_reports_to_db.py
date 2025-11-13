# libs/utils/save_reports_to_db.py
from __future__ import annotations
from typing import Iterable, Tuple, List, Optional
from datetime import datetime, timezone
from decimal import Decimal
import os

from sqlalchemy import text

# 내부 유틸에서 엔진만 사용 (스키마는 절대 변경 X)
from libs.utils.get_db_conn import get_engine

ReportRow = Tuple[str, str, float, str, str]  # (ticker, signal, price, date_str, report_text)

# ----- 환경 변수로 자산 테이블/컬럼 지정 (기본값 제공) -----
ASSETS_TABLE = os.getenv("ASSETS_TABLE", "assets")
ASSETS_ID_COLUMN = os.getenv("ASSETS_ID_COLUMN", "id")
ASSETS_CASH_COLUMN = os.getenv("ASSETS_CASH_COLUMN", "cash")
ASSETS_ROW_ID = os.getenv("ASSETS_ROW_ID", "1")

# ----- 유틸 -----
def utcnow() -> datetime:
    return datetime.now(timezone.utc)

def _to_decimal(x) -> Decimal:
    if isinstance(x, Decimal):
        return x
    try:
        return Decimal(str(x))
    except Exception:
        return Decimal(0)

def _fetch_current_cash(conn) -> Optional[Decimal]:
    sql = text(f"""
        SELECT {ASSETS_CASH_COLUMN}
        FROM public.{ASSETS_TABLE}
        WHERE {ASSETS_ID_COLUMN} = :rid
        FOR UPDATE
    """)
    row = conn.execute(sql, {"rid": ASSETS_ROW_ID}).fetchone()
    if not row:
        return None
    return _to_decimal(row[0])

def _update_cash(conn, new_cash: Decimal) -> None:
    sql = text(f"""
        UPDATE public.{ASSETS_TABLE}
        SET {ASSETS_CASH_COLUMN} = :cash
        WHERE {ASSETS_ID_COLUMN} = :rid
    """)
    conn.execute(sql, {"cash": str(new_cash), "rid": ASSETS_ROW_ID})

def _build_insert_params(rows: Iterable[ReportRow], created_at: datetime) -> List[dict]:
    out: List[dict] = []
    for (ticker, signal, price, date_s, report_text) in rows:
        if not ticker or not signal or not date_s:
            continue
        out.append({
            "ticker": ticker,
            "signal": signal,
            "price": float(price),
            "date": date_s,             # 'YYYY-MM-DD'
            "report": str(report_text),
            "created_at": created_at,
        })
    return out

# ----- 메인: 1주 고정 체결 + 자산 업데이트 + 리포트 저장 -----
def save_reports_to_db(rows: List[ReportRow], db_name: str) -> int:
    """
    요구사항:
    - 저장 '직전'에 티커/시그널/가격을 보고 1주만 체결
    - 매 건 체결 후 잔여 현금(자산) 업데이트
    - DB 스키마 변경 금지 (xai_reports는 기존대로 INSERT만)
    """
    if not rows:
        print("[INFO] 저장할 리포트가 없습니다.")
        return 0

    engine = get_engine(db_name)
    created_at = utcnow()

    # INSERT 템플릿 (스키마는 건드리지 않음)
    insert_sql = text("""
        INSERT INTO public.xai_reports (ticker, signal, price, date, report, created_at)
        VALUES (:ticker, :signal, :price, :date, :report, :created_at)
    """)

    inserted = 0
    with engine.begin() as conn:
        # 현금 락 걸고 읽기
        current_cash = _fetch_current_cash(conn)
        if current_cash is None:
            # 자산 테이블이 없거나 행이 없으면 바로 저장만 수행
            print(f"[WARN] 자산 테이블 public.{ASSETS_TABLE}에서 행을 찾지 못했어요. 체결 없이 리포트만 저장할게요.")
            params = _build_insert_params(rows, created_at)
            if params:
                # 청크 삽입
                CHUNK = 1000
                for i in range(0, len(params), CHUNK):
                    batch = params[i:i+CHUNK]
                    conn.execute(insert_sql, batch)
                    inserted += len(batch)
            print(f"--- {inserted}개의 XAI 리포트가 저장되었습니다 (자산 미적용). ---")
            return inserted