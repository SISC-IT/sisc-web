import pandas as pd
import yfinance as yf
from datetime import datetime, date, timedelta
from calendar import monthrange

# ============================================================
# ① 안전한 Series 추출 유틸 (중복 컬럼 / MultiIndex 방어)
# ============================================================

def get_series(df: pd.DataFrame, col_name: str) -> pd.Series:
    """
    df[col_name]이 Series가 아니라 DataFrame으로 나오는 경우
    (동일 이름 컬럼 여러 개 등)를 방어해서
    항상 1차원 Series만 반환하도록 정규화하는 함수.
    """
    col = df[col_name]
    if isinstance(col, pd.DataFrame):
        # 같은 이름의 컬럼이 여러 개 있으면 첫 번째 컬럼만 사용
        return col.iloc[:, 0]
    return col


# ============================================================
# ② 기술적 지표 계산 함수 (네 코드 + 컬럼 정규화 보강)
# ============================================================

def compute_technical_indicators(df: pd.DataFrame) -> pd.DataFrame:
    """
    입력 df: ticker, date, open, high, low, close, volume
    출력 df: ticker, date, RSI, MACD, Bollinger_Bands_upper, ... MA_200
    """

    # MultiIndex 컬럼 방어: 상위 레벨만 사용
    if isinstance(df.columns, pd.MultiIndex):
        df.columns = df.columns.get_level_values(0)

    df = df.sort_values("date").reset_index(drop=True)

    # 여기서 무조건 Series로 강제
    close = get_series(df, "close")
    high = get_series(df, "high")
    low = get_series(df, "low")
    volume = get_series(df, "volume").fillna(0)

    # ------------------------- RSI -------------------------
    delta = close.diff()
    gain = delta.clip(lower=0)
    loss = -delta.clip(upper=0)

    window_rsi = 14
    avg_gain = gain.rolling(window_rsi).mean()
    avg_loss = loss.rolling(window_rsi).mean()
    rs = avg_gain / (avg_loss + 1e-9)
    rsi = 100 - (100 / (1 + rs))

    # ------------------------- MACD -------------------------
    ema12 = close.ewm(span=12, adjust=False).mean()
    ema26 = close.ewm(span=26, adjust=False).mean()
    macd = ema12 - ema26

    # ------------------------- Bollinger Bands -------------------------
    ma20 = close.rolling(20).mean()
    std20 = close.rolling(20).std()
    bb_upper = ma20 + 2 * std20
    bb_lower = ma20 - 2 * std20

    # ------------------------- ATR -------------------------
    prev_close = close.shift(1)
    tr1 = high - low
    tr2 = (high - prev_close).abs()
    tr3 = (low - prev_close).abs()
    tr = pd.concat([tr1, tr2, tr3], axis=1).max(axis=1)
    atr = tr.rolling(14).mean()

    # ------------------------- OBV -------------------------
    obv = [0]
    for i in range(1, len(close)):
        if close.iloc[i] > close.iloc[i - 1]:
            obv.append(obv[-1] + volume.iloc[i])
        elif close.iloc[i] < close.iloc[i - 1]:
            obv.append(obv[-1] - volume.iloc[i])
        else:
            obv.append(obv[-1])
    obv = pd.Series(obv, index=df.index)

    # ------------------------- Stochastic -------------------------
    lowest_low = low.rolling(14).min()
    highest_high = high.rolling(14).max()
    stochastic = (close - lowest_low) / (highest_high - lowest_low + 1e-9) * 100

    # ------------------------- MFI -------------------------
    typical_price = (high + low + close) / 3
    raw_mf = typical_price * volume
    tp_diff = typical_price.diff()
    pos_mf = raw_mf.where(tp_diff > 0, 0)
    neg_mf = raw_mf.where(tp_diff < 0, 0)
    pos_14 = pos_mf.rolling(14).sum()
    neg_14 = neg_mf.abs().rolling(14).sum() + 1e-9
    mfr = pos_14 / neg_14
    mfi = 100 - (100 / (1 + mfr))

    # ------------------------- MA -------------------------
    ma_5 = close.rolling(5).mean()
    ma_20_roll = close.rolling(20).mean()
    ma_50 = close.rolling(50).mean()
    ma_200 = close.rolling(200).mean()

    out = pd.DataFrame({
        "ticker": df["ticker"],
        "date": df["date"],
        "RSI": rsi,
        "MACD": macd,
        "Bollinger_Bands_upper": bb_upper,
        "Bollinger_Bands_lower": bb_lower,
        "ATR": atr,
        "OBV": obv,
        "Stochastic": stochastic,
        "MFI": mfi,
        "MA_5": ma_5,
        "MA_20": ma_20_roll,
        "MA_50": ma_50,
        "MA_200": ma_200,
    })

    return out


# ============================================================
# ③ yfinance 로 "작년 같은 달" 1개월치 MSFT OHLCV 다운로드
# ============================================================

def fetch_msft_last_year_one_month() -> pd.DataFrame:
    today = date.today()
    last_year = today.year - 1
    month = today.month

    # 작년 같은 달의 1일 ~ 말일
    start = date(last_year, month, 1)
    last_day = monthrange(last_year, month)[1]
    end = date(last_year, month, last_day) + timedelta(days=1)  # yfinance end는 exclusive

    print(f"[TEST] Fetching MSFT data: {start} ~ {end} (작년 같은 달 1개월)")

    df = yf.download("MSFT", start=start.strftime("%Y-%m-%d"), end=end.strftime("%Y-%m-%d"))

    if df.empty:
        print("[TEST] No data returned from yfinance.")
        return pd.DataFrame()

    # 인덱스 → 컬럼
    df = df.reset_index()

    # yfinance 포맷 → 표준 컬럼명으로 정리
    # (단일 티커이므로 MultiIndex 방어는 compute 함수에서 추가로 한 번 더 함)
    df["date"] = pd.to_datetime(df["Date"]).dt.date
    df["ticker"] = "MSFT"

    df = df.rename(columns={
        "Open": "open",
        "High": "high",
        "Low": "low",
        "Close": "close",
        "Volume": "volume",
    })

    return df[["ticker", "date", "open", "high", "low", "close", "volume"]]


# ============================================================
# ④ 실제 계산 & 출력 (DB 업서트 없음)
# ============================================================

if __name__ == "__main__":
    df_price = fetch_msft_last_year_one_month()

    if df_price.empty:
        print("[TEST] 가격 데이터가 없어 기술지표를 계산할 수 없습니다.")
    else:
        df_tech = compute_technical_indicators(df_price)

        # 앞/뒤 일부를 확인해보고 싶으면 둘 다 찍어보자
        print("\n===== MSFT 기술적 지표 (앞 5행) =====\n")
        print(df_tech.head(5).to_string(index=False))

        print("\n===== MSFT 기술적 지표 (뒤 10행) =====\n")
        print(df_tech.tail(10).to_string(index=False))

