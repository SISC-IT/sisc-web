# AI/scripts/extract_to_parquet.py
"""
[목적]
  운영 서버 DB에서 학습 데이터를 추출하여 parquet 파일로 저장

[두 가지 실행 환경]
  1. 로컬 (Termius 터널 켜둔 상태)
     → SSH_PRIVATE_KEY 환경변수 없으면 자동으로 로컬 터널 모드
     → localhost:15432 직접 접속

  2. GitHub Actions (자동화)
     → SSH_PRIVATE_KEY 환경변수 있으면 paramiko로 터널 자동 오픈
     → Termius 불필요

[실행 방법]
  # 로컬 (Termius 켜둔 상태)
  python AI/scripts/extract_to_parquet.py

  # GitHub Actions (환경변수 자동 주입)
  python AI/scripts/extract_to_parquet.py
"""
import os
import io
import time
import sys
import pandas as pd
import psycopg2

# ─────────────────────────────────────────────────────────────────────────────
# 경로 설정
# ─────────────────────────────────────────────────────────────────────────────
current_dir  = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../.."))

OUTPUT_DIR = os.path.join(project_root, "AI/data/kaggle_data")
os.makedirs(OUTPUT_DIR, exist_ok=True)

# ─────────────────────────────────────────────────────────────────────────────
# 접속 설정
# 로컬: 환경변수 없으면 localhost:15432 (Termius 터널)
# Actions: 환경변수로 SSH 정보 주입 → paramiko 터널 자동 오픈
# ─────────────────────────────────────────────────────────────────────────────
SSH_HOST    = os.environ.get("SSH_HOST")
SSH_USER    = os.environ.get("SSH_USER")
SSH_KEY_STR = os.environ.get("SSH_PRIVATE_KEY")
SSH_PORT    = int(os.environ.get("SSH_PORT", 22))

DB_HOST     = os.environ.get("DB_HOST",     "localhost")
DB_PORT     = int(os.environ.get("DB_PORT", 5432))
DB_USER     = os.environ.get("DB_USER",     "유저명")
DB_PASSWORD = os.environ.get("DB_PASSWORD", "비밀번호")
DB_NAME     = os.environ.get("DB_NAME",     "DB이름")

# GitHub Actions 여부: SSH 환경변수 3개 모두 있으면 Actions 모드
IS_ACTIONS = all([SSH_HOST, SSH_USER, SSH_KEY_STR])


# ─────────────────────────────────────────────────────────────────────────────
# SSH 터널 오픈
# 로컬: Termius가 이미 15432 열어두니까 그냥 패스
# Actions: paramiko + sshtunnel 로 코드에서 직접 터널 생성
# ─────────────────────────────────────────────────────────────────────────────
tunnel     = None
LOCAL_PORT = None


def open_tunnel() -> int:
    global tunnel, LOCAL_PORT

    if not IS_ACTIONS:
        # 로컬 모드: Termius 터널이 이미 열려있다고 가정
        print(">> [로컬 모드] Termius 터널 사용 (127.0.0.1:15432)")
        LOCAL_PORT = 15432
        return LOCAL_PORT

    print(">> [Actions 모드] paramiko SSH 터널 오픈 중...")
    try:
        from sshtunnel import SSHTunnelForwarder
        import paramiko

        private_key = paramiko.RSAKey.from_private_key(io.StringIO(SSH_KEY_STR))

        tunnel = SSHTunnelForwarder(
            (SSH_HOST, SSH_PORT),
            ssh_username        = SSH_USER,
            ssh_pkey            = private_key,
            remote_bind_address = (DB_HOST, DB_PORT),
            local_bind_address  = ('127.0.0.1', 0),  # 0 = 빈 포트 자동 배정
        )
        tunnel.start()
        LOCAL_PORT = tunnel.local_bind_port
        print(f">> SSH 터널 오픈 완료! (127.0.0.1:{LOCAL_PORT} → {DB_HOST}:{DB_PORT})")
        return LOCAL_PORT

    except Exception as e:
        print(f"❌ SSH 터널 오픈 실패: {e}")
        sys.exit(1)


def close_tunnel():
    global tunnel
    if tunnel:
        tunnel.stop()
        print(">> SSH 터널 닫힘")


# ─────────────────────────────────────────────────────────────────────────────
# DB 연결 (매번 새 연결 생성 - Neon 연결 끊김 방지)
# ─────────────────────────────────────────────────────────────────────────────
def get_conn():
    return psycopg2.connect(
        host            = "127.0.0.1",
        port            = LOCAL_PORT,
        user            = DB_USER,
        password        = DB_PASSWORD,
        dbname          = DB_NAME,
        connect_timeout = 30,
    )


def read_sql_safe(query: str, desc: str = "") -> pd.DataFrame:
    """연결 끊김 시 최대 3회 재시도"""
    for attempt in range(1, 4):
        try:
            conn = get_conn()
            df   = pd.read_sql(query, conn)
            conn.close()
            return df
        except Exception as e:
            print(f"   [시도 {attempt}/3] 실패: {e}")
            time.sleep(3)
    raise RuntimeError(f"'{desc}' 쿼리 3회 모두 실패")


# ─────────────────────────────────────────────────────────────────────────────
# 메인
# ─────────────────────────────────────────────────────────────────────────────
def main():
    print("=" * 50)
    print(">> extract_to_parquet.py 시작")
    print(f">> 실행 환경: {'GitHub Actions' if IS_ACTIONS else '로컬 (Termius)'}")
    print("=" * 50)

    open_tunnel()

    try:
        # 1. price_data (연도별 청크)
        print("\n>> [1/6] price_data 추출 중 (연도별 분할)...")
        chunks = []
        for year in range(2015, 2024):
            print(f"   {year}년 읽는 중...")
            query = f"""
                SELECT ticker, date, open, high, low, close, volume, per, pbr
                FROM price_data
                WHERE date BETWEEN '{year}-01-01' AND '{year}-12-31'
                ORDER BY ticker, date
            """
            df_chunk = read_sql_safe(query, f"price_data {year}")
            print(f"   {year}년 완료: {len(df_chunk):,}행")
            chunks.append(df_chunk)
            time.sleep(1)

        df_price = pd.concat(chunks, ignore_index=True)
        df_price.to_parquet(os.path.join(OUTPUT_DIR, "price_data.parquet"), index=False)
        print(f"   >> 전체 완료: {len(df_price):,}행")

        # 2. stock_info
        print("\n>> [2/6] stock_info 추출 중...")
        df = read_sql_safe(
            "SELECT ticker, sector, industry FROM stock_info",
            "stock_info"
        )
        df.to_parquet(os.path.join(OUTPUT_DIR, "stock_info.parquet"), index=False)
        print(f"   완료: {len(df):,}행")

        # 3. macroeconomic_indicators
        print("\n>> [3/6] macroeconomic_indicators 추출 중...")
        df = read_sql_safe("""
            SELECT date, cpi, gdp, interest_rate, unemployment_rate,
                   us10y, us2y, yield_spread, vix_close, dxy_close,
                   wti_price, gold_price, credit_spread_hy
            FROM macroeconomic_indicators
            ORDER BY date
        """, "macroeconomic_indicators")
        df.to_parquet(os.path.join(OUTPUT_DIR, "macroeconomic_indicators.parquet"), index=False)
        print(f"   완료: {len(df):,}행")

        # 4. company_fundamentals
        print("\n>> [4/6] company_fundamentals 추출 중...")
        df = read_sql_safe("""
            SELECT ticker, date, revenue, net_income, total_assets,
                   total_liabilities, equity, eps, roe, debt_ratio,
                   operating_cash_flow
            FROM company_fundamentals
            ORDER BY ticker, date
        """, "company_fundamentals")
        df.to_parquet(os.path.join(OUTPUT_DIR, "company_fundamentals.parquet"), index=False)
        print(f"   완료: {len(df):,}행")

        # 5. market_breadth
        print("\n>> [5/6] market_breadth 추출 중...")
        df = read_sql_safe("""
            SELECT date, nh_nl_index, ma200_pct
            FROM market_breadth
            ORDER BY date
        """, "market_breadth")
        df.to_parquet(os.path.join(OUTPUT_DIR, "market_breadth.parquet"), index=False)
        print(f"   완료: {len(df):,}행")

        # 6. sector_returns
        print("\n>> [6/6] sector_returns 추출 중...")
        df = read_sql_safe("""
            SELECT date, sector, etf_ticker, return, close
            FROM sector_returns
            ORDER BY date, sector
        """, "sector_returns")
        df.to_parquet(os.path.join(OUTPUT_DIR, "sector_returns.parquet"), index=False)
        print(f"   완료: {len(df):,}행")

        print("\n" + "=" * 50)
        print(">> 전체 추출 완료!")
        print(f">> 저장 위치: {OUTPUT_DIR}")
        print("=" * 50)

    finally:
        close_tunnel()


if __name__ == "__main__":
    main()

