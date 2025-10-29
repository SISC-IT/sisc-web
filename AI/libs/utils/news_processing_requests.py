import pandas as pd
import time
import re
from tqdm import tqdm
from datetime import datetime, timedelta
from typing import List
import warnings
warnings.filterwarnings("ignore", category=FutureWarning)

from langchain_community.llms import Ollama

import requests
from bs4 import BeautifulSoup

# --- private 함수 ---

def _news_href_crawl(target_date: datetime) -> pd.DataFrame:
    '''
    네이버 페이증권 해외증시 뉴스 링크 수집 함수 (requests + bs4, page 파라미터 방식)
    '''
    base_url = "https://finance.naver.com/news/news_list.naver"
    base_params = {
        "mode": "LSS3D",
        "section_id": "101",
        "section_id2": "258",
        "section_id3": "403",
        "date": target_date
    }

    href_list, title_list, time_list = [], [], []
    page = 1

    while True:
        params = base_params.copy()
        params['page'] = page
        
        response = requests.get(base_url, params=params, headers={'User-Agent': 'Mozilla/5.0'})
        response.raise_for_status()
        soup = BeautifulSoup(response.content, 'html.parser')

        articles = soup.select(".realtimeNewsList .articleSubject")
        if not articles:
            break # 기사가 없으면 중단

        summaries = soup.select(".realtimeNewsList .articleSummary")
        for i, article in enumerate(articles):
            link_tag = article.find('a')
            if link_tag and link_tag.has_attr('href'):
                from urllib.parse import urlparse, parse_qs
                
                # 파라미터 파싱으로 office_id와 article_id 추출
                parsed_url = urlparse(link_tag['href'])
                query_params = parse_qs(parsed_url.query)
                
                office_id = query_params.get('office_id', [None])[0]
                article_id = query_params.get('article_id', [None])[0]

                if office_id and article_id:
                    # 새로운 표준 URL로 재조립
                    clean_url = f"https://n.news.naver.com/mnews/article/{office_id}/{article_id}"
                    href_list.append(clean_url)
                    title_list.append(link_tag.get('title', ''))
                    if i < len(summaries):
                        time_list.append(summaries[i].find('span', class_='wdate').text.strip())
                    else:
                        time_list.append('')
        
        page += 1 # 다음 페이지로

    return pd.DataFrame({"href": href_list, "title": title_list, "date": time_list})

def _news_content_crawl(url_list: List[str]) -> List[str]:
    '''
    news_href_crawl에서 수집된 뉴스 본문 수집 함수 (requests + bs4)
    '''
    content_list = []
    for url in tqdm(url_list):
        try:
            response = requests.get(url, headers={'User-Agent': 'Mozilla/5.0'})
            response.raise_for_status()
            soup = BeautifulSoup(response.content, 'html.parser')

            # 기사 본문 선택
            article_body = soup.find('article')
            if article_body:
                text = article_body.get_text()
            else:
                # 일부 다른 구조의 뉴스 페이지 대응
                article_body = soup.select_one('#newsct_article')
                if article_body:
                    text = article_body.get_text()
                else:
                    text = "" # 본문을 찾지 못한 경우

            # 정규식을 이용한 텍스트 클리닝
            text = re.sub(r"\([^)]*기자\)|[a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\.[a-zA-Z0-9-.]+", "", text)
            text = re.sub(r"\['.*?']", "", text)
            text = re.sub(r"\s+", " ", text).strip()
            
            content_list.append(text)
        except requests.exceptions.RequestException as e:
            print(f"Error fetching {url}: {e}")
            content_list.append("") # 오류 발생 시 빈 문자열 추가
    
    return content_list

def _llm_prompt(text: str) -> str:
    '''
    뉴스 요약을 위한 프롬프트 생성 함수
    '''
    prompt = f"""You're an expert analyst. Extract key information from the news article below. Use only information from news articles, do not add unknown information.

News:
{text}

Please respond in English and No output other than the output format is required. Summarize output format(JSON):
{{
  "Stock": "",
  "Event": "",
  "Factor": "",
  "Reason": "",
  "Sentiment": "",
  "Confidence": ""
}}
"""
    
    return prompt

def _llm_summary(time_list: List, content_list: List, llm_client) -> pd.DataFrame:
    '''
    뉴스 요약 함수
    '''
    summary_list = []
    
    for text in tqdm(content_list):
        input_txt = _llm_prompt(text)
        summary_list.append(llm_client.invoke(input_txt))
    
    return pd.DataFrame({"date": time_list,
                         "summary": summary_list
                        })

def _collect_and_summarize_news(target_date: datetime, llm_client) -> pd.DataFrame:
    '''
    지정된 날짜의 해외증시 뉴스를 수집하고 LLM으로 요약하는 함수
    '''
    print(f"===== [News Processing] {target_date} 뉴스 수집 및 요약 시작 =====")
    
    href_df = _news_href_crawl(target_date)
    
    content_list = _news_content_crawl(list(href_df['href']))
    href_df['content'] = content_list
    
    valid_df = href_df[href_df['content'].isnull()==False].reset_index(drop=True)
    
    summary_df = _llm_summary(valid_df['date'], valid_df['content'], llm_client=llm_client)
    
    print("===== [News Processing] 완료 =====")

    return summary_df

# --- public 함수 ---
def get_weekly_news_summary(days: int, llm_client) -> pd.DataFrame:
    '''
    지정된 기간(일)만큼 뉴스를 하루씩 순차적으로 수집하고 요약하여 합치는 역할을 합니다.
    '''
    print(f"===== [Weekly News Summary] 지난 {days}일치 뉴스 요약 시작 =====")
    
    all_summaries = []
    
    for i in range(1, days + 1):
        date = datetime.now() - timedelta(days=i)
        target_date = date.strftime('%Y%m%d')

        try:
            daily_summary_df = _collect_and_summarize_news(
                target_date = target_date,
                llm_client = llm_client
            )
            all_summaries.append(daily_summary_df)
            print(f"--- {target_date} 뉴스 요약 완료 ---")
        
        except Exception as e:
            print(f"!!! {target_date} 처리 중 오류 발생: {e} !!!")
            continue
    
    if not all_summaries:
        print("수집된 뉴스 요약이 없습니다.")
        return pd.DataFrame()

    weekly_summary_df = pd.concat(all_summaries, ignore_index=True)
    
    print(f"===== [Weekly News Summary] 총 {len(weekly_summary_df)}개 뉴스 요약 완료 =====")
    
    return weekly_summary_df

# --- 테스트 코드 ---
if __name__ == '__main__':
    print("--- news_processing_requests.py 테스트 모드 (주간 수집) ---")

    my_llm = Ollama(model="llama3.2")
    DAYS_TO_COLLECT = 1 # 테스트를 위해 1일치만 수집

    try:
        weekly_output_df = get_weekly_news_summary(days=DAYS_TO_COLLECT, llm_client=my_llm)

        print(f"\n[최종 {DAYS_TO_COLLECT}일치 요약 결과 (상위 5개)]")
        print(weekly_output_df.head())
        print(f"\n전체 요약 개수: {len(weekly_output_df)}")

    except Exception as e:
        print(f"\n테스트 중 오류 발생: {e}")
