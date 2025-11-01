import os
import sys
import json
from typing import Dict, Any
import psycopg2

# --- 프로젝트 루트 경로 설정 ---
project_root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.append(project_root)
# ------------------------------

REQUIRED_KEYS = {"host", "user", "password", "dbname"}

def get_db_conn(db_name: str):
    """db_name으로 configs/config.json에서 DB 접속정보를 찾아 psycopg2 Connection 생성"""
    if not db_name or not isinstance(db_name, str):
        raise ValueError("db_name must be a non-empty string")

    config_path = os.path.join(project_root, "configs", "config.json")

    try:
        with open(config_path, "r", encoding="utf-8") as f:
            configs: Dict[str, Dict[str, Any]] = json.load(f)
    except FileNotFoundError:
        raise FileNotFoundError(f"Config file not found: {config_path}")
    except json.JSONDecodeError as e:
        raise ValueError(f"Invalid JSON format in config file: {e}") from e

    config = configs.get(db_name)
    if not config:
        raise KeyError(f"DB config for '{db_name}' not found")

    missing = REQUIRED_KEYS - set(config.keys())
    if missing:
        raise KeyError(f"DB config '{db_name}' missing keys: {sorted(missing)}")

    return psycopg2.connect(
        host=config["host"],
        user=config["user"],
        password=config["password"],
        dbname=config["dbname"],
        port=config.get("port", 5432),
    )
