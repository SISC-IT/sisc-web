# AI/tests/quick_db_check.py
"""
[DB 연결 테스트]
- AI/libs/database/connection.py 모듈이 정상 작동하는지 확인합니다.
- 간단한 쿼리(SELECT 1)를 실행하여 연결 상태를 점검합니다.
"""

import sys
import os

# 프로젝트 루트 경로 추가 (절대 경로 import 위함)
current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../.."))
if project_root not in sys.path:
    sys.path.append(project_root)

try:
    from AI.libs.database.connection import get_db_conn
except ImportError as e:
    print(f"[Error] 모듈 임포트 실패: {e}")
    print(f"PYTHONPATH: {sys.path}")
    sys.exit(1)

def check_connection():
    print("=== DB 연결 테스트 시작 ===")
    
    conn = None
    try:
        # DB 연결 시도
        conn = get_db_conn()
        cursor = conn.cursor()
        
        # 간단한 쿼리 실행
        cursor.execute("SELECT 1")
        result = cursor.fetchone()
        
        if result and result[0] == 1:
            print("[Success] DB 연결 성공! (Query Result: 1)")
        else:
            print("[Fail] 쿼리 실행 결과가 예상과 다릅니다.")
            
    except Exception as e:
        print(f"[Error] DB 연결 중 오류 발생: {e}")
        
    finally:
        if conn:
            conn.close()
            print("=== DB 연결 종료 ===")

if __name__ == "__main__":
    check_connection()