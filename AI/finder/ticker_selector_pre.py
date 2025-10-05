import pandas as pd
import time
import json
import re
import os
from collections import defaultdict
from tqdm import tqdm
from datetime import datetime

import warnings
warnings.filterwarnings("ignore", category=FutureWarning)

from langchain_community.llms import Ollama


def parse_summary(x):
    '''
    JSON 문자열을 dict로 변환해주는 함수
    '''
    if isinstance(x, str):
        try:
            return json.loads(x)
        except:
            return None
    elif isinstance(x, dict):
        return x
    return None


folder_path = 'data/input_data'
file_list = os.listdir(folder_path)

all_dataframes = []

for file in file_list:
    if file.endswith('.csv'):
        df = pd.read_csv('data/input_data' + '/' + file)
        all_dataframes.append(df)

df = pd.concat(all_dataframes, ignore_index=True)

result = {}
df['parsed'] = df['summary'].apply(parse_summary)

for item in df['parsed'].dropna():
    try:
        stock = item.get("Stock").upper()
    except:
        continue


result = {}
df['parsed'] = df['summary'].apply(parse_summary)

for item in df['parsed'].dropna():
    try:
        stock = item.get("Stock").upper()
    except:
        continue
    
    if not stock:
        continue
    if not item.get("Event"):
        continue
    if not item.get("Confidence"):
        continue
    if not item.get("Factor"):
        continue
    if not item.get("Reason"):
        continue
    if not item.get("Sentiment"):
        continue

    if stock not in result:
        result[stock] = {
            "Event": [],
            "Factor": [],
            "Reason": [],
            "Sentiment": [],
            "Confidence": []
        }
    try:
        if float(item.get("Confidence")) >= 0.5: 
            result[stock]["Event"].append(item.get("Event"))
            result[stock]["Factor"].append(item.get("Factor"))
            result[stock]["Reason"].append(item.get("Reason"))
            result[stock]["Sentiment"].append(item.get("Sentiment"))
            result[stock]["Confidence"].append(item.get("Confidence"))
    except:
        pass

result_df = pd.DataFrame(result).T
result_df.reset_index(inplace=True)
result_df.rename(columns={'index': 'ticker'}, inplace=True)
result_df.head()


#  재무제표 기반 안전 종목 필터링
stability_score = pd.read_csv('data/stability_score_2025.csv')
good_list = list(stability_score[stability_score['stability_score'] > stability_score['stability_score'].mean()]['ticker'])
filter_df = pd.merge(result_df, stability_score[['ticker']], on='ticker')


#  뉴스 요약 최종 전처리
def process_row(row):
    # 1. Event, Factor, Reason 중복 제거
    row['Event'] = list(set(row['Event']))
    row['Factor'] = list(set(row['Factor']))
    row['Reason'] = list(set(row['Reason']))
    
    # 2. Sentiment -> dict 변환
    sentiment_count = {'Positive': 0, 'Neutral': 0, 'Negative': 0}
    for s in row['Sentiment']:
        if s in sentiment_count:
            sentiment_count[s] += 1
    row['Sentiment'] = sentiment_count
    
    # 3. Confidence -> 평균값 (빈 리스트면 None 표시 → drop 단계에서 제거)
    if isinstance(row['Confidence'], list) and len(row['Confidence']) > 0:
        row['Confidence'] = sum(row['Confidence']) / len(row['Confidence'])
    else:
        row['Confidence'] = None
    
    return row

df_processed = filter_df.apply(process_row, axis=1)
df_processed = df_processed.dropna(subset=['Confidence']).reset_index(drop=True)



#  상위 종목 3개 추출
prompt = f"""You are a professional investment analyst. Analyze the stock data below and recommend the top three stocks for investment.

Data:
{df_processed.to_dict(orient='records')}

Requests:
- Higher scores are awarded for higher positive sentiment and higher confidence.
- Bonus points are given for short-term positive factors (events, factors, reasons).
- Select only your top three stocks and provide a final score and brief explanation for each.

Please respond in English and No output other than the output format is required. Summarize output format(JSON):
{{
 "Top3": [
   {{"ticker": "", "score": "", "reason": ""}},
   {{"ticker": "", "score": "", "reason": ""}},
   {{"ticker": "", "score": "", "reason": ""}}
 ]
}}
"""

llm = Ollama(model="llama3.2")
response = llm.invoke(prompt)

def get_top3(response):
    top3 = []
    
    for i in range(len(json.loads(response)['Top3'])):
        top3.append(json.loads(response)['Top3'][i]['ticker'])
    
    return top3

print(get_top3(response))

