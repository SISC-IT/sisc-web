# AI/libs/database/connection.py
# 환경변수 기반 DB 연결 유틸

from __future__ import annotations
import os
import sys
from typing import Dict, Any
from pathlib import Path
from urllib.parse import quote_plus

import psycopg2
from sqlalchemy import create_engine
from dotenv import load_dotenv


# ----------------------------------------------------------------------
# 프로젝트 루트 경로 설정
# ----------------------------------------------------------------------
project_root = Path(__file__).resolve().parents[3]
sys.path.append(str(project_root))

load_dotenv(os.path.join(project_root, ".env"))


# ----------------------------------------------------------------------
# 내부 헬퍼: prefix 정규화
# ----------------------------------------------------------------------
def _normalize_prefix(name: str) -> str:
    """
    한국어 주석:
    - db_name="db" → "DB_"
    - db_name="DB" → "DB_"
    - db_name="DB_" → "DB_"
    - 이미 REPORT_DB_처럼 끝이 "_" 로 끝나는 경우 → 그대로 사용
    - REPORT_DB → REPORT_DB_
    - market_db → MARKET_DB_
    """
    if not name:
        return "DB_"

    up = name.upper()

    # 이미 정확한 환경변수 prefix 구조인 경우 (예: REPORT_DB_)
    if up.endswith("_"):
        return up

    # REPORT_DB → REPORT_DB_
    if "_" in up and not up.endswith("_"):
        return up + "_"

    # db → DB_
    return up + "_"


# ----------------------------------------------------------------------
# 환경변수에서 DB 설정 가져오기
# ----------------------------------------------------------------------
REQUIRED_ENV_KEYS = ["HOST", "USER", "PASSWORD", "NAME"]


def _load_db_env(prefix: str) -> Dict[str, Any]:
    """
    prefix(DB_, REPORT_DB_, MARKET_DB_ 등)에 맞는 환경변수 로딩
    예: DB_HOST, REPORT_DB_HOST, MARKET_DB_PASSWORD...
    """
    cfg = {}

    for key in os.environ:
        if key.startswith(prefix):
            # DB_HOST → host
            sub = key.replace(prefix, "").lower()
            cfg[sub] = os.environ[key]

    # 필수값 검사
    missing = []
    for key in REQUIRED_ENV_KEYS:
        env_name = prefix + key
        if env_name not in os.environ:
            missing.append(env_name)

    if missing:
        raise EnvironmentError(
            f"[DB CONFIG ERROR] 아래 환경변수가 필요합니다:\n"
            f"  {missing}\n"
            f"예시:\n"
            f"  export {prefix}HOST=...\n"
            f"  export {prefix}USER=...\n"
            f"  export {prefix}PASSWORD=...\n"
            f"  export {prefix}NAME=..."
        )

    # 기본 포트
    cfg.setdefault("port", "5432")

    return cfg


# ----------------------------------------------------------------------
# SQLAlchemy URL 생성
# ----------------------------------------------------------------------
def _build_sqlalchemy_url(cfg: Dict[str, Any]) -> str:
    user = quote_plus(cfg["user"])
    password = quote_plus(cfg["password"])
    host = cfg["host"]
    port = cfg.get("port", "5432")
    dbname = cfg["name"]

    base = f"postgresql+psycopg2://{user}:{password}@{host}:{port}/{dbname}"

    sslmode = cfg.get("sslmode")
    if sslmode:
        return f"{base}?sslmode={sslmode}"

    return base


# ----------------------------------------------------------------------
# psycopg2 커넥션
# ----------------------------------------------------------------------
def get_db_conn(db_name: str = "DB_"):
    """
    한국어 주석:
    - db_name="db" 또는 "DB" → 자동으로 prefix="DB_"
    - db_name="report_db" → "REPORT_DB_"
    - 이미 "REPORT_DB_" 라면 그대로 사용
    """

    prefix = _normalize_prefix(db_name)
    cfg = _load_db_env(prefix)

    return psycopg2.connect(
        host=cfg["host"],
        user=cfg["user"],
        password=cfg["password"],
        dbname=cfg["name"],
        port=int(cfg.get("port", 5432)),
        sslmode=cfg.get("sslmode"),
    )


# ----------------------------------------------------------------------
# SQLAlchemy 엔진
# ----------------------------------------------------------------------
def get_engine(db_name: str = "DB_"):
    prefix = _normalize_prefix(db_name)
    cfg = _load_db_env(prefix)
    url = _build_sqlalchemy_url(cfg)
    return create_engine(url, pool_pre_ping=True)
