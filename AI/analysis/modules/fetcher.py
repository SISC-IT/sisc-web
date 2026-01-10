from typing import List, Dict, Any
import pandas as pd
import yfinance as yf
import numpy as np

INDICATORS = [
    "RSI", "MACD", "Bollinger_Bands_upper", "Bollinger_Bands_lower",
    "ATR", "OBV", "Stochastic", "MFI", "MA_5", "MA_20", "MA_50", "MA_200"
]

def _ema(series: pd.Series, span: int) -> pd.Series:
    return series.ewm(span=span, adjust=False).mean()

def _wilder_ema(series: pd.Series, length: int) -> pd.Series:
    alpha = 1.0 / float(length)
    return series.ewm(alpha=alpha, adjust=False).mean()

def _as_1d_series(x, index=None) -> pd.Series:
    """
    입력이 Series/ndarray/DataFrame 모두여도 1D Series로 변환.
    """
    if isinstance(x, pd.DataFrame):
        if x.shape[1] == 1:
            x = x.iloc[:, 0]
        else:
            raise ValueError(f"Expected 1 column DataFrame, got shape {x.shape}")
    if isinstance(x, pd.Series):
        arr = x.to_numpy().reshape(-1,)  # 1D 보장
        idx = x.index if index is None else index
        return pd.Series(arr, index=idx)
    # numpy or list-like
    arr = np.asarray(x).reshape(-1,)
    if index is None:
        raise ValueError("index required when converting array to Series")
    return pd.Series(arr, index=index)

def _flatten_yf_df(df: pd.DataFrame, ticker: str) -> pd.DataFrame:
    """
    yfinance가 MultiIndex 컬럼을 줄 때 단일 티커 프레임으로 평탄화.
    결과 컬럼: ['Open','High','Low','Close','Adj Close','Volume']
    """
    if isinstance(df.columns, pd.MultiIndex):
        # case 1: level 1 (티커명)이 있음
        lv0 = df.columns.get_level_values(0)
        lv1 = df.columns.get_level_values(1)
        if ticker in lv1:
            flat = df.xs(ticker, axis=1, level=1, drop_level=True)
            return flat
        # case 2: level 0이 티커명인 경우
        if ticker in lv0:
            flat = df.xs(ticker, axis=1, level=0, drop_level=True)
            return flat
        # fallback: 첫 번째 티커 세트 사용
        first_ticker = list(set(lv1))[0] if len(set(lv1)) > 0 else None
        if first_ticker is not None:
            flat = df.xs(first_ticker, axis=1, level=1, drop_level=True)
            return flat
        # 최후: 모든 레벨을 문자열로 합쳐 단일 레벨로
        df.columns = ["_".join([str(p) for p in c if p is not None]) for c in df.columns]
        return df
    return df

def fetch_context_data_from_yf(
    ticker: str,
    days: int = 400,
    window: int = 60,
) -> List[Dict[str, Any]]:
    # 1) 시세
    df = yf.download(ticker, period=f"{days}d", progress=False, auto_adjust=False)
    if df.empty:
        raise ValueError(f"No data for ticker: {ticker}")

    # 🌟 멀티인덱스 대응: 단일 티커 프레임으로 평탄화
    df = _flatten_yf_df(df, ticker)

    for col in ["High", "Low", "Close", "Volume"]:
        if col not in df.columns:
            raise ValueError(f"Missing column: {col}")

    # 정밀 캐스팅
    high = _as_1d_series(pd.to_numeric(df["High"], errors="coerce"))
    low  = _as_1d_series(pd.to_numeric(df["Low"], errors="coerce"))
    close= _as_1d_series(pd.to_numeric(df["Close"], errors="coerce"))
    vol  = _as_1d_series(pd.to_numeric(df["Volume"], errors="coerce"))

    # 2) RSI(14) - Wilder
    delta = close.diff()
    gain = delta.clip(lower=0.0)
    loss = (-delta).clip(lower=0.0)
    avg_gain = _wilder_ema(gain, 14)
    avg_loss = _wilder_ema(loss, 14)
    rs = avg_gain / avg_loss.replace(0, np.nan)
    rsi = 100.0 - (100.0 / (1.0 + rs))

    # 3) MACD(12,26,9) - 라인만
    ema12 = _ema(close, 12)
    ema26 = _ema(close, 26)
    macd_line = ema12 - ema26
    _ = _ema(macd_line, 9)  # signal 미사용

    # 4) Bollinger Bands(20,2)
    mid = close.rolling(20).mean()
    sd = close.rolling(20).std(ddof=0)
    bbu = mid + 2.0 * sd
    bbl = mid - 2.0 * sd

    # 5) ATR(14) - Wilder
    prev_close = close.shift(1)
    tr1 = (high - low).abs()
    tr2 = (high - prev_close).abs()
    tr3 = (low - prev_close).abs()
    tr = pd.concat([tr1, tr2, tr3], axis=1).max(axis=1)
    atr = _wilder_ema(tr, 14)

    # 6) OBV (1D 보장)
    direction = _as_1d_series(np.sign(close.diff().fillna(0.0).to_numpy()), index=close.index)
    obv = (direction * vol).cumsum()

    # 7) Stochastic %K(14,3,3)
    ll14 = low.rolling(14).min()
    hh14 = high.rolling(14).max()
    raw_k = ((close - ll14) / (hh14 - ll14).replace(0, np.nan) * 100.0)
    stoch_k = raw_k.rolling(3).mean().rolling(3).mean()

    # 8) MFI(14)
    tp = (high + low + close) / 3.0
    mf = tp * vol
    pos_mf = mf.where(tp > tp.shift(1), 0.0)
    neg_mf = mf.where(tp < tp.shift(1), 0.0)
    pos_sum = pos_mf.rolling(14).sum()
    neg_sum = neg_mf.rolling(14).sum()
    mfr = pos_sum / neg_sum.replace(0, np.nan)
    mfi = 100.0 - (100.0 / (1.0 + mfr))

    # 9) SMA
    ma5   = close.rolling(5).mean()
    ma20  = close.rolling(20).mean()
    ma50  = close.rolling(50).mean()
    ma200 = close.rolling(200).mean()

    # 🌟 최종 1D 보장 (혹시 모를 2D를 싹 Series로)
    def S(x): return _as_1d_series(x, index=df.index)

    rsi      = S(rsi)
    macd_line= S(macd_line)
    bbu      = S(bbu)
    bbl      = S(bbl)
    atr      = S(atr)
    obv      = S(obv)
    stoch_k  = S(stoch_k)
    mfi      = S(mfi)
    ma5      = S(ma5)
    ma20     = S(ma20)
    ma50     = S(ma50)
    ma200    = S(ma200)

    out_df = pd.DataFrame({
        "RSI": rsi,
        "MACD": macd_line,
        "Bollinger_Bands_upper": bbu,
        "Bollinger_Bands_lower": bbl,
        "ATR": atr,
        "OBV": obv,
        "Stochastic": stoch_k,
        "MFI": mfi,
        "MA_5": ma5,
        "MA_20": ma20,
        "MA_50": ma50,
        "MA_200": ma200,
    }, index=df.index)

    out_df = out_df.dropna(how="any")
    if len(out_df) < window:
        raise ValueError(f"Not enough valid rows after indicator calc ({len(out_df)}). Increase 'days' (e.g., 500).")

    out_df = out_df.tail(window).copy()

    records: List[Dict[str, Any]] = []
    for idx, row in out_df.iterrows():
        rec = {"Date": idx.strftime("%Y-%m-%d")}
        for col in INDICATORS:
            rec[col] = float(row[col])
        records.append(rec)

    return records
