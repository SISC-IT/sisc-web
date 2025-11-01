# quick_db_check.py
import os
import sys
import json
from typing import Dict, Union

import psycopg2


# --- 프로젝트 루트 경로 설정 ---------------------------------------------------
project_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.append(project_root)

# --- 설정 파일 로드 ------------------------------------------------------------
cfg_path = os.path.join(project_root, "configs", "config.json")

config: Dict = {}
if os.path.isfile(cfg_path):
    with open(cfg_path, "r", encoding="utf-8") as f:
        config = json.load(f)
    print("[INFO] configs/config.json 로드 완료")
else:
    print(f"[WARN] 설정 파일이 없습니다: {cfg_path}")

db_cfg: Union[str, Dict] = (config or {}).get("db", {})

# --- DB 연결 테스트 ------------------------------------------------------------
try:
    # db 설정이 dict면 키워드 인자로, 문자열(DSN)이면 그대로 사용
    if isinstance(db_cfg, dict):
        conn = psycopg2.connect(**db_cfg)  # 예: {"host": "...", "port": 5432, "dbname": "...", "user": "...", "password": "..."}
    else:
        conn = psycopg2.connect(dsn=str(db_cfg))

    with conn:
        with conn.cursor() as cur:
            cur.execute("SELECT version();")
            print("✅ 연결 성공:", cur.fetchone()[0])

            cur.execute("SELECT current_database(), current_user;")
            db, user = cur.fetchone()
            print(f"ℹ️ DB/USER: {db} / {user}")

except Exception as e:
    print("❌ 연결 실패:", repr(e))
