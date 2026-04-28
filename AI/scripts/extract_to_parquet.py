# AI/scripts/extract_to_parquet.py
"""
[목적]
  운영 서버 DB에서 학습 데이터를 추출하여 parquet 파일로 저장

[실행 환경]
  1. 서버 크론잡 기본값
     → DB_CONNECT_MODE=direct
     → DB_HOST, DB_PORT로 DB에 직접 접속

  2. SSH 터널 모드
     → DB_CONNECT_MODE=ssh_tunnel
     → SSH_HOST, SSH_USER, SSH_PRIVATE_KEY, SSH_PORT를 사용해 터널 생성

  3. 로컬 Termius 터널 모드
     → DB_CONNECT_MODE=termius를 명시한 경우에만 127.0.0.1:15432 사용

[실행 방법]
  # 서버 크론잡
  python AI/scripts/extract_to_parquet.py

  # 로컬 Termius 터널을 명시적으로 사용할 때
  DB_CONNECT_MODE=termius python AI/scripts/extract_to_parquet.py

  # SSH 터널을 스크립트에서 열어야 할 때
  DB_CONNECT_MODE=ssh_tunnel python AI/scripts/extract_to_parquet.py
"""
import os
import io
import time
import sys
import pandas as pd
import psycopg2
from dotenv import load_dotenv

# ─────────────────────────────────────────────────────────────────────────────
# 경로 설정
# ─────────────────────────────────────────────────────────────────────────────
current_dir  = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../.."))

# .env 로드
load_dotenv(os.path.join(project_root, ".env"))

OUTPUT_DIR = os.path.join(project_root, "AI/data/kaggle_data")
os.makedirs(OUTPUT_DIR, exist_ok=True)

# ─────────────────────────────────────────────────────────────────────────────
# 접속 설정
# 서버 크론잡에서는 기본적으로 DB_HOST, DB_PORT에 직접 접속한다.
# 로컬 Termius나 SSH 터널은 DB_CONNECT_MODE로 명시해야 한다.
# ─────────────────────────────────────────────────────────────────────────────
SSH_HOST    = os.environ.get("SSH_HOST")
SSH_USER    = os.environ.get("SSH_USER")
SSH_KEY_STR = os.environ.get("SSH_PRIVATE_KEY")
SSH_PORT    = int(os.environ.get("SSH_PORT", 22))

DB_HOST     = os.environ.get("DB_HOST",     "127.0.0.1")
DB_PORT     = int(os.environ.get("DB_PORT", 5432))
DB_USER     = os.environ.get("DB_USER",     "postgres")
DB_PASSWORD = os.environ.get("DB_PASSWORD", "")
DB_NAME     = os.environ.get("DB_NAME",     "sisc_db")
DB_SSLMODE  = os.environ.get("DB_SSLMODE", "").strip()

DB_CONNECT_MODE = os.environ.get("DB_CONNECT_MODE", "direct").strip().lower()
VALID_CONNECT_MODES = {"direct", "ssh_tunnel", "ssh-tunnel", "termius", "local_termius", "local-termius"}
if DB_CONNECT_MODE not in VALID_CONNECT_MODES:
    raise ValueError(f"지원하지 않는 DB_CONNECT_MODE입니다: {DB_CONNECT_MODE}")

USE_SSH_TUNNEL = DB_CONNECT_MODE in {"ssh_tunnel", "ssh-tunnel"}
USE_TERMIUS_TUNNEL = DB_CONNECT_MODE in {"termius", "local_termius", "local-termius"}

DB_CONNECT_HOST = DB_HOST
DB_CONNECT_PORT = DB_PORT


# ─────────────────────────────────────────────────────────────────────────────
# DB 접속 준비
# direct: DB_HOST, DB_PORT 직접 접속
# termius: 명시적으로 요청된 로컬 터널만 사용
# ssh_tunnel: paramiko + sshtunnel로 터널 생성
# ─────────────────────────────────────────────────────────────────────────────
tunnel     = None


def prepare_connection() -> None:
    global tunnel, DB_CONNECT_HOST, DB_CONNECT_PORT

    if USE_TERMIUS_TUNNEL:
        DB_CONNECT_HOST = os.environ.get("TERMIUS_DB_HOST", "127.0.0.1")
        DB_CONNECT_PORT = int(os.environ.get("TERMIUS_DB_PORT", 15432))
        print(f">> [Termius 모드] 로컬 터널 사용 ({DB_CONNECT_HOST}:{DB_CONNECT_PORT})")
        return

    if not USE_SSH_TUNNEL:
        print(f">> [직접 DB 접속] {DB_CONNECT_HOST}:{DB_CONNECT_PORT}")
        return

    if not all([SSH_HOST, SSH_USER, SSH_KEY_STR]):
        print("[오류] SSH 터널 모드에는 SSH_HOST, SSH_USER, SSH_PRIVATE_KEY가 필요합니다.")
        sys.exit(1)

    print(">> [SSH 터널 모드] paramiko SSH 터널 오픈 중...")
    try:
        from sshtunnel import SSHTunnelForwarder
        import paramiko

        for key_class in [paramiko.Ed25519Key, paramiko.RSAKey, paramiko.ECDSAKey]:
            try:
                private_key = key_class.from_private_key(io.StringIO(SSH_KEY_STR))
                break
            except Exception:
                continue
        else:
            raise ValueError("SSH 키 타입을 인식할 수 없습니다.")

        tunnel = SSHTunnelForwarder(
            (SSH_HOST, SSH_PORT),
            ssh_username        = SSH_USER,
            ssh_pkey            = private_key,
            remote_bind_address = (DB_HOST, DB_PORT),
            local_bind_address  = ('127.0.0.1', 0),  # 0 = 빈 포트 자동 배정
        )
        tunnel.start()
        DB_CONNECT_HOST = "127.0.0.1"
        DB_CONNECT_PORT = tunnel.local_bind_port
        print(f">> SSH 터널 오픈 완료! ({DB_CONNECT_HOST}:{DB_CONNECT_PORT} → {DB_HOST}:{DB_PORT})")

    except Exception as e:
        print(f"[오류] SSH 터널 오픈 실패: {e}")
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
    conn_kwargs = dict(
        host            = DB_CONNECT_HOST,
        port            = DB_CONNECT_PORT,
        user            = DB_USER,
        password        = DB_PASSWORD,
        dbname          = DB_NAME,
        connect_timeout = 30,
    )
    if DB_SSLMODE:
        conn_kwargs["sslmode"] = DB_SSLMODE
    return psycopg2.connect(**conn_kwargs)


def read_sql_safe(query: str, desc: str = "") -> pd.DataFrame:
    """연결 끊김 시 최대 3회 재시도"""
    for attempt in range(1, 4):
        conn = None
        try:
            conn = get_conn()
            df   = pd.read_sql(query, conn)
            return df
        except Exception as e:
            print(f"   [시도 {attempt}/3] 실패: {e}")
            time.sleep(3)
        finally:
            if conn is not None:
                try:
                    conn.close()
                except Exception:
                    pass
    raise RuntimeError(f"'{desc}' 쿼리 3회 모두 실패")


# ─────────────────────────────────────────────────────────────────────────────
# 메인
# ─────────────────────────────────────────────────────────────────────────────
def main():
    print("=" * 50)
    print(">> extract_to_parquet.py 시작")
    print(f">> DB 접속 모드: {DB_CONNECT_MODE}")
    print("=" * 50)

    prepare_connection()

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
                   wti_price, gold_price, credit_spread_hy,
                   core_cpi, pce, core_pce
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

