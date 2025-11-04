# AI/libs/utils/get_db_conn.py
# 한국어 주석: JSON 설정에서 DB 접속정보를 읽어
# 1) psycopg2 Connection (로우 커넥션)
# 2) SQLAlchemy Engine (권장, 커넥션 풀/프리핑)
# 을 생성하는 유틸. 중복 로딩 방지를 위해 캐시 사용.

from __future__ import annotations
import os
import sys
import json
from typing import Dict, Any, Optional
from pathlib import Path
from urllib.parse import quote_plus

import psycopg2
from sqlalchemy import create_engine

# --- 프로젝트 루트 경로 설정 ---
project_root = Path(__file__).resolve().parents[3]  # .../AI/libs/utils/get_db_conn.py 기준 상위 3단계
sys.path.append(str(project_root))
# --------------------------------

# 필수 키(포트는 선택)
REQUIRED_KEYS = {"host", "user", "password", "dbname"}

# JSON 설정 캐시 (프로세스 내 1회 로드)
_CONFIG_CACHE: Optional[Dict[str, Dict[str, Any]]] = None


def _config_path() -> Path:
    """configs/config.json 경로를 안전하게 계산"""
    return project_root/"AI"/"configs"/"config.json"


def _load_configs() -> Dict[str, Dict[str, Any]]:
    """
    - configs/config.json을 읽어서 {db_name: {host, user, password, dbname, port?, sslmode?}} 형태로 반환
    - 파일은 깃에 올리지 않는 것을 권장(민감정보)
    """
    global _CONFIG_CACHE
    if _CONFIG_CACHE is not None:
        return _CONFIG_CACHE

    path = _config_path()
    if not path.exists():
        raise FileNotFoundError(f"[DB CONFIG] 설정 파일이 없습니다: {path}")

    try:
        with path.open("r", encoding="utf-8") as f:
            data: Dict[str, Dict[str, Any]] = json.load(f)
    except json.JSONDecodeError as e:
        raise ValueError(f"[DB CONFIG] JSON 파싱 오류: {e}") from e

    # 간단한 구조 검증(필수키 확인은 get_* 함수에서 db별로 수행)
    if not isinstance(data, dict) or not data:
        raise ValueError("[DB CONFIG] 최상위 JSON은 비어있지 않은 객체여야 합니다.")

    _CONFIG_CACHE = data
    return _CONFIG_CACHE


def _get_db_config(db_name: str) -> Dict[str, Any]:
    """
    - 특정 db_name에 해당하는 설정 블록을 반환
    - 필수 키(host, user, password, dbname) 존재 검증
    """
    if not db_name or not isinstance(db_name, str):
        raise ValueError("db_name must be a non-empty string")

    configs = _load_configs()
    cfg = configs.get(db_name)
    if not cfg:
        raise KeyError(f"[DB CONFIG] '{db_name}' 설정 블록을 찾을 수 없습니다. (configs/config.json)")

    missing = REQUIRED_KEYS - set(cfg.keys())
    if missing:
        raise KeyError(f"[DB CONFIG] '{db_name}'에 필수 키 누락: {sorted(missing)}")

    return cfg


def _build_sqlalchemy_url(cfg: Dict[str, Any]) -> str:
    """
    - SQLAlchemy용 PostgreSQL URI를 안전하게 구성
    - 비밀번호/유저의 특수문자를 URL 인코딩(quote_plus)로 보호
    - 예: postgresql+psycopg2://user:pass@host:port/dbname?sslmode=require
    """
    user = quote_plus(str(cfg["user"]))
    password = quote_plus(str(cfg["password"]))
    host = str(cfg["host"])
    port = int(cfg.get("port", 5432))
    dbname = str(cfg["dbname"])

    base = f"postgresql+psycopg2://{user}:{password}@{host}:{port}/{dbname}"

    # 선택 옵션: sslmode
    # (Neon/클라우드 Postgres의 경우 require가 흔함)
    sslmode = cfg.get("sslmode")
    if sslmode:
        return f"{base}?sslmode={sslmode}"

    return base


def get_db_conn(db_name: str):
    """
    - psycopg2 로우 커넥션 생성(직접 커서 열어 사용할 때)
    - pandas 경고가 싫다면 read_sql에는 get_engine() 사용을 권장
    """
    cfg = _get_db_config(db_name)
    return psycopg2.connect(
        host=cfg["host"],
        user=cfg["user"],
        password=cfg["password"],
        dbname=cfg["dbname"],
        port=int(cfg.get("port", 5432)),
        sslmode=cfg.get("sslmode", None),  # 필요 시 자동 적용
    )


def get_engine(db_name: str):
    """
    - SQLAlchemy Engine 생성(권장)
    - 커넥션 풀 + pre_ping으로 죽은 연결 사전 감지 → 운영 안정성↑
    - pandas.read_sql, 대량입출력 등에서 사용
    """
    cfg = _get_db_config(db_name)
    url = _build_sqlalchemy_url(cfg)
    return create_engine(url, pool_pre_ping=True)
