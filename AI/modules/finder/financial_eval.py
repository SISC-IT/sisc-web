# -*- coding: utf-8 -*-
"""
한국어 주석:
- 이 파일은 기존 config.json/psycopg2 기반 코드를
  프로젝트 최신 표준 DB 유틸(get_engine, get_db_conn) 기반으로 완전히 리팩터링한 버전입니다.

- 모든 DB 접속은 .env 기반 환경변수에서 읽고
  get_engine("db") 로 일관성 있게 연결합니다.
"""

import pandas as pd
from datetime import datetime
import warnings
warnings.filterwarnings("ignore", category=FutureWarning)

# 🚀 신규 DB 엔진 유틸 (환경변수 기반)
from AI.libs.utils.get_db_conn import get_engine

# ----------------------------------------------------------------------
# 1️⃣ DB에서 재무제표 불러오기
# ----------------------------------------------------------------------
def load_company_fundamentals(db_name="db"):
    """
    company_fundamentals 테이블 전체를 불러오는 함수.
    JSON 설정 불필요. 환경변수 기반 DB 연결(get_engine) 사용.
    """
    query = "SELECT * FROM company_fundamentals;"
    engine = get_engine(db_name)

    df = pd.read_sql(query, engine)
    print(f"[INFO] Loaded fundamentals: {len(df)} rows")
    return df


# ----------------------------------------------------------------------
# 2️⃣ 재무제표 결측치 보정 함수 (원본과 동일)
# ----------------------------------------------------------------------
def fill_financials(df, industry_medians=None):
    df = df.copy()

    df['date'] = pd.to_datetime(df['date'])
    df = df.sort_values(['ticker', 'date'])

    def fill_group(g):
        g = g.sort_values('date')

        g['revenue'] = g['revenue'].interpolate().ffill().bfill()
        g['net_income'] = g['net_income'].interpolate().ffill().bfill()

        # equity 보정
        mask = g['equity'].isna() & g['total_assets'].notna() & g['total_liabilities'].notna()
        g.loc[mask, 'equity'] = g.loc[mask, 'total_assets'] - g.loc[mask, 'total_liabilities']

        # liabilities 보정
        mask = g['total_liabilities'].isna() & g['total_assets'].notna() & g['equity'].notna()
        g.loc[mask, 'total_liabilities'] = g.loc[mask, 'total_assets'] - g.loc[mask, 'equity']

        # assets 보정
        mask = g['total_assets'].isna() & g['equity'].notna() & g['total_liabilities'].notna()
        g.loc[mask, 'total_assets'] = g.loc[mask, 'equity'] + g.loc[mask, 'total_liabilities']

        # 나머지 보간
        for col in ['total_assets', 'total_liabilities', 'equity']:
            g[col] = g[col].interpolate().ffill().bfill()

        g['eps'] = g['eps'].interpolate().ffill().bfill()

        # PE Ratio 기업중앙값
        g['pe_ratio'] = g['pe_ratio'].fillna(g['pe_ratio'].median())

        return g

    df = df.groupby('ticker').apply(fill_group).reset_index(drop=True)

    if industry_medians is not None:
        df = df.fillna(df['ticker'].map(industry_medians))

    return df


# ----------------------------------------------------------------------
# 3️⃣ 연간 재무제표 집계
# ----------------------------------------------------------------------
def aggregate_yearly_financials(df):
    df["date"] = pd.to_datetime(df["date"])
    df["year"] = df["date"].dt.year

    flow_cols = ["revenue", "net_income", "eps"]
    stock_cols = ["total_assets", "total_liabilities", "equity"]

    yearly_list = []

    for (ticker, year), g in df.groupby(["ticker", "year"]):
        flow_data = g[flow_cols].mean()
        stock_data = g.loc[g["date"].idxmax(), stock_cols]

        yearly_data = {"ticker": ticker, "year": year}
        yearly_data.update(flow_data.to_dict())
        yearly_data.update(stock_data.to_dict())

        yearly_list.append(yearly_data)

    return pd.DataFrame(yearly_list)


# ----------------------------------------------------------------------
# 4️⃣ 안정성 평가 점수 계산
# ----------------------------------------------------------------------
def stability_score(df):
    results = []

    for ticker, g in df.groupby("ticker"):
        latest = g.iloc[0]

        # Debt Ratio
        debt_ratio = latest["total_liabilities"] / latest["total_assets"] if latest["total_assets"] else None
        if debt_ratio is not None:
            if debt_ratio < 0.4: debt_score = 5
            elif debt_ratio < 0.6: debt_score = 4
            elif debt_ratio < 0.8: debt_score = 3
            elif debt_ratio < 1.0: debt_score = 2
            else: debt_score = 1
        else:
            debt_score = 3

        # ROA
        roa = latest["net_income"] / latest["total_assets"] if latest["total_assets"] else None
        if roa is not None:
            if roa >= 0.08: roa_score = 5
            elif roa >= 0.05: roa_score = 4
            elif roa >= 0.02: roa_score = 3
            elif roa >= 0: roa_score = 2
            else: roa_score = 1
        else:
            roa_score = 3

        # ROE
        roe = latest["net_income"] / latest["equity"] if latest["equity"] else None
        if roe is not None:
            if roe >= 0.12: roe_score = 5
            elif roe >= 0.08: roe_score = 4
            elif roe >= 0.04: roe_score = 3
            elif roe >= 0: roe_score = 2
            else: roe_score = 1
        else:
            roe_score = 3

        # 매출 성장률
        if len(g) >= 2:
            prev, curr = g.iloc[-2], g.iloc[-1]
            if prev["revenue"] != 0:
                rev_growth = (curr["revenue"] - prev["revenue"]) / prev["revenue"]
            else:
                rev_growth = None
        else:
            rev_growth = None

        if rev_growth is not None:
            if rev_growth >= 0.10: rev_score = 5
            elif rev_growth >= 0.05: rev_score = 4
            elif rev_growth >= 0: rev_score = 3
            else: rev_score = 2
        else:
            rev_score = 3

        # EPS
        eps_score = 5 if latest["eps"] > 0 else 1 if latest["eps"] is not None else 3

        total_score = round((debt_score + roa_score + roe_score + rev_score + eps_score) / 5, 2)

        results.append({
            "ticker": ticker,
            "debt_score": debt_score,
            "roa_score": roa_score,
            "roe_score": roe_score,
            "rev_score": rev_score,
            "eps_score": eps_score,
            "stability_score": total_score
        })

    return pd.DataFrame(results)


# ----------------------------------------------------------------------
# 5️⃣ 실행 파이프라인
# ----------------------------------------------------------------------
if __name__ == "__main__":
    print("[STEP] Load fundamentals")
    company = load_company_fundamentals("db")

    print("[STEP] Fill missing values")
    c_df = fill_financials(company)

    print("[STEP] Aggregate yearly")
    year_df = aggregate_yearly_financials(c_df)

    print("[STEP] Evaluate stability")
    recent_y_df = year_df[year_df['year'] >= datetime.now().year - 3].groupby("ticker").mean()
    eval_df = stability_score(recent_y_df)

    eval_df.sort_values("stability_score", ascending=False, inplace=True)
    eval_df.to_csv(f"data/stability_score_{datetime.now().year}.csv", index=False)

    print("[DONE] stability score exported")
