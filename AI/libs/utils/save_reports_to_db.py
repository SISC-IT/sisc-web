# libs/core/save_reports_to_db.py
from __future__ import annotations
from typing import Iterable, Tuple, List
from datetime import datetime, timezone
import sys
from sqlalchemy import create_engine, text
import os

# --- 프로젝트 루트 경로 설정 ---
project_root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.append(project_root)
# ------------------------------

from libs.utils.get_db_conn import get_engine

ReportRow = Tuple[str, str, float, str, str]

def utcnow() -> datetime:
    return datetime.now(timezone.utc)


def ensure_table_schema(engine) -> None:
    """
    한국어 주석:
    - 정보스키마 조회 후 필요한 컬럼만 추가.
    """
    with engine.begin() as conn:
        cols = conn.execute(text("""
            SELECT column_name FROM information_schema.columns
            WHERE table_schema='public' AND table_name='xai_reports';
        """)).fetchall()
        existing = {r[0] for r in cols}
        need = {"ticker", "signal", "price", "date", "report", "created_at"}
        missing = need - existing
        if missing:
            parts = []
            if "ticker" in missing: parts.append("ADD COLUMN IF NOT EXISTS ticker varchar(20) NOT NULL")
            if "signal" in missing: parts.append("ADD COLUMN IF NOT EXISTS signal varchar(10) NOT NULL")
            if "price" in missing:  parts.append("ADD COLUMN IF NOT EXISTS price numeric(10,2) NOT NULL")
            if "date" in missing:   parts.append("ADD COLUMN IF NOT EXISTS date date NOT NULL")
            if "report" in missing: parts.append("ADD COLUMN IF NOT EXISTS report text")
            if "created_at" in missing:
                parts.append("ADD COLUMN IF NOT EXISTS created_at timestamptz NOT NULL DEFAULT now()")
            conn.execute(text(f"ALTER TABLE public.xai_reports {', '.join(parts)};"))

def build_insert_params(rows: Iterable[ReportRow], created_at: datetime) -> List[dict]:
    """
    한국어 주석:
    - SQLAlchemy의 named parameter 형태(dict)로 변환.
    """
    out: List[dict] = []
    for (ticker, signal, price, date_s, report_text) in rows:
        if not ticker or not signal or not date_s:
            continue
        out.append({
            "ticker": ticker,
            "signal": signal,
            "price": float(price),
            "date": date_s,       # 'YYYY-MM-DD'
            "report": str(report_text),
            "created_at": created_at,
        })
    return out

def save_reports_to_db(rows: List[ReportRow], db_name: str) -> int:
    """
    한국어 주석:
    - SQLAlchemy로 안전하게 INSERT.
    - pandas 경고 제거, 커넥션 관리 자동화, 프리핑으로 죽은 커넥션 방지.
    """
    if not rows:
        print("[INFO] 저장할 리포트가 없습니다.")
        return 0

    engine = get_engine(db_name)
    ensure_table_schema(engine)

    created_at = utcnow()
    params = build_insert_params(rows, created_at)
    if not params:
        print("[WARN] 유효한 저장 파라미터가 없어 INSERT를 건너뜁니다.")
        return 0

    insert_sql = text("""
        INSERT INTO public.xai_reports (ticker, signal, price, date, report, created_at)
        VALUES (:ticker, :signal, :price, :date, :report, :created_at)
    """)

    inserted = 0
    # 대량이면 청크 분할 권장
    CHUNK = 1000
    with engine.begin() as conn:
        for i in range(0, len(params), CHUNK):
            batch = params[i:i+CHUNK]
            conn.execute(insert_sql, batch)
            inserted += len(batch)

    print(f"--- {inserted}개의 XAI 리포트가 데이터베이스에 저장되었습니다. ---")
    return inserted
