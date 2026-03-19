# AI/modules/collector/news_data.py
"""
[뉴스 데이터 수집기]
- Google News RSS를 통해 종목 관련 최신 뉴스를 수집합니다.
- 수집된 뉴스의 본문을 스크래핑하고, LLM을 사용하여 핵심 내용을 요약합니다.
- 기존 libs/utils/news_processing.py 의 기능을 대체 및 고도화했습니다.
"""

import sys
import os
import requests
from bs4 import BeautifulSoup
from datetime import datetime
from typing import List, Dict

# 프로젝트 루트 경로 추가
current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../../.."))
if project_root not in sys.path:
    sys.path.append(project_root)

# LLM 클라이언트 (요약용)
# 만약 LLM 설정이 안 되어 있다면 요약은 건너뛰도록 처리합니다.
try:
    from AI.libs.llm import GroqClient
    LLM_AVAILABLE = True
except ImportError:
    LLM_AVAILABLE = False

def fetch_news_links(ticker: str, limit: int = 3) -> List[Dict]:
    """
    Google News RSS에서 최신 뉴스 링크와 제목을 가져옵니다.
    """
    # 검색 쿼리: Ticker + "stock" (예: AAPL stock)
    query = f"{ticker} stock"
    url = f"https://news.google.com/rss/search?q={query}&hl=en-US&gl=US&ceid=US:en"
    
    try:
        response = requests.get(url, timeout=10)
        response.raise_for_status()
        
        # XML 파싱
        soup = BeautifulSoup(response.content, features="xml")
        items = soup.findAll('item')
        
        news_list = []
        for item in items[:limit]:
            news_item = {
                'title': item.title.text,
                'link': item.link.text,
                'pubDate': item.pubDate.text
            }
            news_list.append(news_item)
            
        return news_list
    except Exception as e:
        print(f"[NewsCollector] RSS 수집 실패 ({ticker}): {e}")
        return []

def fetch_article_content(url: str) -> str:
    """
    뉴스 URL에 접속하여 본문 텍스트를 추출합니다.
    (간단한 스크래핑 로직으로, 사이트 구조에 따라 실패할 수 있음)
    """
    try:
        # 헤더 설정 (봇 차단 방지)
        headers = {
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
        }
        # 구글 뉴스 링크는 리다이렉트가 발생하므로 allow_redirects=True
        response = requests.get(url, headers=headers, timeout=10, allow_redirects=True)
        soup = BeautifulSoup(response.content, 'html.parser')
        
        # 본문 추정 (p 태그 수집)
        paragraphs = soup.find_all('p')
        text_content = " ".join([p.get_text() for p in paragraphs])
        
        # 내용이 너무 짧으면 실패로 간주 (광고나 네비게이션일 수 있음)
        if len(text_content) < 200:
            return ""
            
        return text_content[:3000] # LLM 입력 제한(토큰) 고려하여 3000자 절삭
    except Exception:
        return ""

def summarize_news(content: str, llm_client) -> str:
    """
    수집된 뉴스 본문을 LLM을 사용하여 3줄로 요약합니다.
    """
    if not content:
        return "본문 수집 실패로 요약 불가"
        
    prompt = f"""
    아래 뉴스 기사를 투자자 관점에서 핵심만 3줄로 요약해주세요. 
    반드시 '한국어'로 번역하여 출력하세요.
    
    [기사 본문]
    {content}
    """
    
    try:
        summary = llm_client.generate_text(prompt, temperature=0.3)
        return summary
    except Exception as e:
        return f"요약 중 에러 발생: {e}"

def collect_news(ticker: str) -> List[Dict]:
    """
    [메인 함수] 특정 종목의 뉴스를 수집하고 요약하여 반환합니다.
    
    Args:
        ticker (str): 종목 코드 (예: AAPL)
        
    Returns:
        List[Dict]: 뉴스 정보 리스트 [{'title', 'link', 'summary', ...}, ...]
    """
    print(f"[NewsCollector] {ticker} 뉴스 수집 및 분석 시작...")
    
    # 1. 링크 수집
    news_items = fetch_news_links(ticker)
    if not news_items:
        print(f"   - {ticker} 관련 최신 뉴스가 없습니다.")
        return []
    
    # 2. LLM 초기화 (요약용)
    llm = None
    can_summarize = False
    
    if LLM_AVAILABLE:
        try:
            # Groq가 빠르므로 우선 사용
            llm = GroqClient(model_name="llama-3.3-70b-versatile")
            can_summarize = True
        except Exception:
            print("   [Warning] LLM 클라이언트 초기화 실패 (API Key 확인 필요). 요약 없이 진행합니다.")

    results = []
    
    # 3. 본문 스크래핑 및 요약
    for item in news_items:
        print(f"   - 뉴스 분석 중: {item['title'][:30]}...")
        
        summary = "요약 기능 비활성화"
        if can_summarize:
            content = fetch_article_content(item['link'])
            if content:
                summary = summarize_news(content, llm)
            else:
                summary = "본문 접근 불가 (보안 또는 구조 문제)"
        
        results.append({
            "ticker": ticker,
            "title": item['title'],
            "link": item['link'],
            "pub_date": item['pubDate'],
            "summary": summary
        })
        
    print(f"   - {len(results)}건 뉴스 처리 완료.")
    return results

if __name__ == "__main__":
    # 테스트 실행
    print("=== 뉴스 수집 테스트 ===")
    res = collect_news("AAPL")
    for r in res:
        print(f"\n[Title] {r['title']}")
        print(f"[Summary] {r['summary']}")
        print("-" * 30)