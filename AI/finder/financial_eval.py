import pandas as pd
import time
import json
import re
from datetime import datetime

import warnings
warnings.filterwarnings("ignore", category=FutureWarning)

import psycopg2

conn = psycopg2.connect(
    dbname="neondb",
    user="neondb_owner",
    password="npg_hWkg04MwGlYs",
    host="ep-misty-lab-adgec0kl-pooler.c-2.us-east-1.aws.neon.tech",
    port="5432"
)
cur = conn.cursor()

query = "SELECT * FROM company_fundamentals;"
company = pd.read_sql(query, conn)
conn.close()

company.head()

ticker_list = company['ticker'].unique() # 498


# 결측치 처리
def fill_financials(df: pd.DataFrame, industry_median: pd.DataFrame = None):
    df = df.copy()
    
    # 1. Forward/Backward fill (연속 시계열 기준)
    df = df.ffill().bfill()
    
    # 2. Equity 보정 (자산 - 부채)
    if 'Assets' in df.columns and 'Liabilities' in df.columns:
        df['Equity'] = df['Assets'] - df['Liabilities']
    
    # 3. EPS 보정 (순이익 / 자본 or 주식수 정보 있을 경우)
    if 'NetIncome' in df.columns and 'EPS' in df.columns:
        df['EPS'] = df['EPS'].fillna(df['NetIncome'] / (df['Equity'].replace(0, pd.NA)))
    
    # 4. 업계 중앙값으로 채우기 (옵션)
    if industry_median is not None:
        df = df.fillna(industry_median)
    
    return df

def fill_financials(df, industry_medians=None):
    df = df.copy()

    # 1. 시계열 정렬
    df['date'] = pd.to_datetime(df['date'])
    df = df.sort_values(['ticker', 'date'])

    # 2. 그룹별 처리 (기업별)
    def fill_group(g):
        g = g.sort_values('date')
        
        # revenue, net_income 보간
        g['revenue'] = g['revenue'].interpolate(method='linear').fillna(method='ffill').fillna(method='bfill')
        g['net_income'] = g['net_income'].interpolate(method='linear').fillna(method='ffill').fillna(method='bfill')
        
        # equity 보정 (assets, liabilities 둘 다 있는 경우만)
        mask = g['equity'].isna() & g['total_assets'].notna() & g['total_liabilities'].notna()
        g.loc[mask, 'equity'] = g.loc[mask, 'total_assets'] - g.loc[mask, 'total_liabilities']
        
        # liabilities 보정
        mask = g['total_liabilities'].isna() & g['total_assets'].notna() & g['equity'].notna()
        g.loc[mask, 'total_liabilities'] = g.loc[mask, 'total_assets'] - g.loc[mask, 'equity']
        
        # assets 보정
        mask = g['total_assets'].isna() & g['equity'].notna() & g['total_liabilities'].notna()
        g.loc[mask, 'total_assets'] = g.loc[mask, 'equity'] + g.loc[mask, 'total_liabilities']
        
        # 남은 결측치 시계열 보간
        for col in ['total_assets', 'total_liabilities', 'equity']:
            g[col] = g[col].interpolate(method='linear').fillna(method='ffill').fillna(method='bfill')
        
        # eps 보간
        g['eps'] = g['eps'].interpolate(method='linear').fillna(method='ffill').fillna(method='bfill')
        
        # pe_ratio는 같은 기업의 중앙값으로
        g['pe_ratio'] = g['pe_ratio'].fillna(g['pe_ratio'].median())
        
        return g
    
    df = df.groupby('ticker').apply(fill_group).reset_index(drop=True)

    # 3. 업계 평균으로 남은 결측치 채우기 (옵션)
    if industry_medians is not None:
        df = df.fillna(df['ticker'].map(industry_medians))
    
    return df


c_df = fill_financials(company)


# 연간 재무제표로 변환
def aggregate_yearly_financials(df):
    """
    연간 재무제표로 변환
    - revenue, net_income, eps: 연간 평균 (Flow)
    - total_assets, total_liabilities, equity: 연말 값 (Stock)

    Parameters
    ----------
    df : DataFrame
        columns = [ticker, date, revenue, net_income, total_assets, total_liabilities, equity, eps]

    Returns
    -------
    DataFrame
        연간 집계 데이터
    """

    # 날짜 변환 & 연도 추출
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

    yearly_df = pd.DataFrame(yearly_list)
    
    return yearly_df

year_df = aggregate_yearly_financials(c_df)
year_df.head()


# 평가표 생성
def stability_score(df):
    results = []

    # 기업별 평가
    for ticker, g in df.groupby("ticker"):
        latest = g.iloc[0]

        # 1. Debt Ratio (총부채 / 총자산)
        debt_ratio = latest["total_liabilities"] / latest["total_assets"] if latest["total_assets"] else None
        if debt_ratio is not None:
            if debt_ratio < 0.4: debt_score = 5
            elif debt_ratio < 0.6: debt_score = 4
            elif debt_ratio < 0.8: debt_score = 3
            elif debt_ratio < 1.0: debt_score = 2
            else: debt_score = 1
        else:
            debt_score = 3  # 결측 시 중립점

        # 2. ROA (순이익 / 총자산)
        roa = latest["net_income"] / latest["total_assets"] if latest["total_assets"] else None
        if roa is not None:
            if roa >= 0.08: roa_score = 5
            elif roa >= 0.05: roa_score = 4
            elif roa >= 0.02: roa_score = 3
            elif roa >= 0: roa_score = 2
            else: roa_score = 1
        else:
            roa_score = 3

        # 3. ROE (순이익 / 자본)
        roe = latest["net_income"] / latest["equity"] if latest["equity"] else None
        if roe is not None:
            if roe >= 0.12: roe_score = 5
            elif roe >= 0.08: roe_score = 4
            elif roe >= 0.04: roe_score = 3
            elif roe >= 0: roe_score = 2
            else: roe_score = 1
        else:
            roe_score = 3

        # 4. 매출 성장률 (Revenue Growth: 최근 2년)
        if len(g) >= 2:
            rev_growth = (g.iloc[-1]["revenue"] - g.iloc[-2]["revenue"]) / g.iloc[-2]["revenue"] if g.iloc[-2]["revenue"] else None
        else:
            rev_growth = None
        if rev_growth is not None:
            if rev_growth >= 0.10: rev_score = 5
            elif rev_growth >= 0.05: rev_score = 4
            elif rev_growth >= 0: rev_score = 3
            else: rev_score = 2
        else:
            rev_score = 3

        # 5. EPS
        if latest["eps"] is not None:
            eps_score = 5 if latest["eps"] > 0 else 1
        else:
            eps_score = 3

        # 최종 점수 (평균)
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

recent_y_df = year_df[year_df['year'] > datetime.now().year-3].groupby("ticker")[['revenue', 'net_income', 'eps', 
                                                                                  'total_assets', 'total_liabilities', 
                                                                                  'equity']].mean()
eval_df = stability_score(recent_y_df)
eval_df.sort_values(by='stability_score', ascending=False, inplace=True)

# save
eval_df.to_csv(f'data/stability_score_{datetime.now().year}.csv', index=False)
