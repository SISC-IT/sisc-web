import pandas as pd
import time
import re
from tqdm import tqdm
from datetime import datetime, timedelta
from typing import List

import warnings
warnings.filterwarnings("ignore", category=FutureWarning)

from selenium import webdriver
from webdriver_manager.chrome import ChromeDriverManager
from selenium.webdriver.chrome.service import Service
from selenium.webdriver.common.by import By

from langchain_community.llms import Ollama


# --- private 함수 ---

def _news_href_crawl(target_date: datetime) -> pd.DataFrame:
    '''
    네이버 페이증권 해외증시 뉴스 링크 수집 함수
    '''
    url = f"https://finance.naver.com/news/news_list.naver?mode=LSS3D&section_id=101&section_id2=258&section_id3=403&date={target_date}"
    
    chrome_options = webdriver.ChromeOptions()
    chrome_options.add_argument('--incognito')
    
    driver = webdriver.Chrome(
        service=Service(ChromeDriverManager().install()),
        options=chrome_options
    )
    driver.get(url)
    time.sleep(2)
    
    href_list = []
    title_list = []
    time_list = []
    url_list = []
    
    page_list = driver.find_element(By.CLASS_NAME, "Nnavi").find_element(By.TAG_NAME, 'td').find_elements(By.TAG_NAME, 'td')
    for page in page_list:
        url_list.append(page.find_element(By.TAG_NAME, 'a').get_property('href'))

    for url in list(set(url_list)):
        driver.get(url)
        time.sleep(2)
        
        news_list = driver.find_element(By.CLASS_NAME, "realtimeNewsList").find_elements(By.CLASS_NAME, 'articleSubject')
        tt_list = driver.find_element(By.CLASS_NAME, "realtimeNewsList").find_elements(By.CLASS_NAME, 'articleSummary')
        
        for i in range(len(news_list)):
            href_list.append(news_list[i].find_element(By.TAG_NAME, 'a').get_property('href'))
            title_list.append(news_list[i].find_element(By.TAG_NAME, 'a').get_property('title'))
            time_list.append(tt_list[i].find_element(By.CLASS_NAME, 'wdate').text)
    
    driver.close()
    
    return pd.DataFrame({"href": href_list,
                         "title": title_list,
                         "date": time_list
                        })

def _news_content_crawl(url_list: List[str]) -> List[str]:
    '''
    news_href_crawl에서 수집된 뉴스 본문 수집 함수
    '''
    chrome_options = webdriver.ChromeOptions()
    chrome_options.add_argument('--incognito')

    driver = webdriver.Chrome(
        service=Service(ChromeDriverManager().install()),
        options=chrome_options
    )
    
    content_list = []
    for url in tqdm(url_list):
        driver.get(url)
        time.sleep(2)
        
        text = driver.find_element(By.TAG_NAME, 'article').text
        text = re.sub(r"\([^)]*기자\)|[a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\.[a-zA-Z0-9-.]+", "", text) # 기자명, 이메일 제거
        text = re.sub(r"\[.*?\]", "", text)      # 대괄호 [] 안 내용 제거
        text = re.sub(r"\s+", " ", text).strip() # 개행/여백 정리
        
        content_list.append(text)
    
    driver.close()
    
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
    
    # llm_client = Ollama(model="llama3.2")
    
    for text in tqdm(content_list):
        input_txt = _llm_prompt(text)
        summary_list.append(llm_client.invoke(input_txt))
    
    return pd.DataFrame({"date": time_list,
                         "summary": summary_list
                        })

def _collect_and_summarize_news(target_date: datetime, llm_client) -> pd.DataFrame:
    '''
    지정된 날짜의 해외증시 뉴스를 수집하고 LLM으로 요약하는 함수

    Args:
        target_date (datetime): 수집할 뉴스의 날짜 (datetime 객체)
        llm_client: Langchain LLM 클라이언트 객체

    Returns:
        pd.DataFrame: 날짜와 요약 내용이 포함된 데이터프레임
    '''
    print(f"===== [News Processing] {target_date} 뉴스 수집 및 요약 시작 =====")
    
    # 뉴스 링크 수집
    href_df = _news_href_crawl(target_date)
    
    # 뉴스 본문 수집
    content_list = _news_content_crawl(list(href_df['href']))
    href_df['content'] = content_list
    
    # 비어있는 본문이 있는 행 제거
    valid_df = href_df[href_df['content'].isnull()==False].reset_index(drop=True)
    
    # LLM을 이용한 요약 (llm_client 전달)
    summary_df = _llm_summary(valid_df['date'], valid_df['content'], llm_client=llm_client)
    
    print("===== [News Processing] 완료 =====")

    return summary_df


# --- public 함수 ---
def get_weekly_news_summary(days: int, llm_client) -> pd.DataFrame:
    '''
    지정된 기간(일)만큼 뉴스를 하루씩 순차적으로 수집하고 요약하여 합치는 역할을 합니다.
    finder 모듈에서 일주일치 데이터를 가져오기 위해 호출할 메인 함수
    
    Args:
        days (int): 오늘을 제외하고, 과거 몇일 치의 뉴스를 수집할지 지정 (보통 7일)
        llm_client: Langchain LLM 클라이언트 객체

    Returns:
        pd.DataFrame: 지정된 기간 동안의 모든 뉴스 요약 결과가 합쳐진 데이터프레임
    '''
    print(f"===== [Weekly News Summary] 지난 {days}일치 뉴스 요약 시작 =====")
    
    all_summaries = [] # 일별 요약 결과를 저장할 리스트
    
    # 1일부터 days(7)일까지 거꾸로 반복 (어제 -> 2일전 -> ... -> 7일전)
    for i in range(1, days + 1):
        date = datetime.now() - timedelta(days=i)
        target_date = date.strftime('%Y%m%d')

        try:
            # 기존의 일별 요약 함수를 호출하여 하루치 요약을 가져옵니다.
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
        return pd.DataFrame() # 빈 데이터프레임 반환

    weekly_summary_df = pd.concat(all_summaries, ignore_index=True)
    
    print(f"===== [Weekly News Summary] 총 {len(weekly_summary_df)}개 뉴스 요약 완료 =====")
    
    return weekly_summary_df


# --- 테스트 코드 ---
if __name__ == '__main__':
    print("--- news_processing.py 테스트 모드 (주간 수집) ---")

    my_llm = Ollama(model="llama3.2")
    DAYS_TO_COLLECT = 5

    try:
        weekly_output_df = get_weekly_news_summary(days=DAYS_TO_COLLECT, llm_client=my_llm)

        print(f"\n[최종 {DAYS_TO_COLLECT}일치 요약 결과 (상위 5개)]")
        print(weekly_output_df.head())
        print(f"\n전체 요약 개수: {len(weekly_output_df)}")

    except Exception as e:
        print(f"\n테스트 중 오류 발생: {e}")
