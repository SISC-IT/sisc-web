# quick_db_check.py

"""
DB 연결을 빠르게 테스트하는 스크립트.
- 프로젝트 루트(sisc-web) 자동 계산
- .env 자동 로드
- 환경변수 기반 get_db_conn 사용
"""

import os
import sys
from dotenv import load_dotenv

# -----------------------------
# 1) 프로젝트 루트 계산 (중요!)
# -----------------------------
# 현재 위치: sisc-web/AI/tests/quick_db_check.py
project_root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.append(project_root)

# -----------------------------
# 2) .env 파일 로드
# -----------------------------
load_dotenv(os.path.join(project_root, ".env"))

# -----------------------------
# 3) DB 유틸
# -----------------------------
from AI.libs.utils.get_db_conn import get_db_conn


def quick_db_check(db_name: str = "db"):
    print(f"[INFO] DB 연결 테스트 시작 (db_name='{db_name}')")

    try:
        conn = get_db_conn(db_name)
    except Exception as e:
        print("❌ DB 연결 실패 (커넥션 생성 오류):", repr(e))
        return

    try:
        with conn.cursor() as cur:
            cur.execute("SELECT version();")
            version = cur.fetchone()[0]
            print("✅ 연결 성공:", version)

            cur.execute("SELECT current_database(), current_user;")
            db, user = cur.fetchone()
            print(f"ℹ️ DB = {db}, USER = {user}")

    except Exception as e:
        print("❌ 쿼리 실행 실패:", repr(e))
    finally:
        conn.close()
        print("🔌 DB 연결 종료")


if __name__ == "__main__":
    quick_db_check("db")
