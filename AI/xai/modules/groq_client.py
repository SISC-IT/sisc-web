from groq import Groq
import re

DEFAULT_MODEL = "llama-3.3-70b-versatile"

def clean_strikethrough(text: str) -> str:
    """~~취소선~~ 내부 내용까지 완전히 제거."""
    # ~~...~~ 패턴 전체 삭제
    cleaned = re.sub(r"~~.*?~~", "", text)
    # 중복 공백 정리 (optional)
    return re.sub(r"\s{2,}", " ", cleaned).strip()

def build_xai_prompt(payload: dict) -> str:
    """
    Evidence 기반 XAI 리포트 생성을 위한 최적화 프롬프트 템플릿.
    payload 형식:
        {
            "ticker": "AAPL",
            "date": "2024-01-10",
            "signal": "BUY",
            "price": 187.35,
            "evidence": [
                {"feature_name": "...", "contribution": 0.23},
                ...
            ]
        }
    """

    ticker = payload["ticker"]
    date = payload["date"]
    signal = payload["signal"]
    price = payload["price"]
    evidence = payload["evidence"]

    # evidence 표현
    ev_lines = []
    for i, ev in enumerate(evidence, 1):
        ev_lines.append(f"{i}) {ev['feature_name']}: 기여도 {ev['contribution']}")

    ev_block = "\n".join(ev_lines)

    # 최적화 템플릿
    prompt = f"""
다음 의사결정 데이터를 이용해 신호 설명(XAI) 리포트를 작성하라.

[입력 데이터]
- 종목: {ticker}
- 날짜: {date}
- 가격: {price}
- 신호: {signal}

[Evidence 목록]
{ev_block}

[작성 규칙]
1) 모든 분석은 위 evidence만으로 작성.
2) 수치·날짜 기반 근거를 명확히 명시.
3) 과도한 추측, 비사실적 가정, 모호한 표현은 금지.
4) 문장 구조는 아래 형식을 따름.

[출력 형식]

[1] 신호 요약
- 신호 종류와 그 의미를 2문장 이내 설명.

[2] 핵심 Evidence 분석
- Evidence 3개에 대해 각 2~3문장으로 기여 방향을 설명.

[3] 종합 해석
- 수치 기반으로 신호가 발생한 종합적 맥락을 서술 (3~4문장).

[4] 리스크 요인
- 반대 방향으로 작용할 근거 1~2개 제시.

위 형식 그대로 작성하라.
"""
    return prompt.strip()


def ask_groq(prompt_payload: dict, api_key: str, model: str = DEFAULT_MODEL) -> str:
    """최적화된 프롬프트 기반 Groq 리포트 생성."""
    client = Groq(api_key=api_key)

    # 최적화 프롬프트 구성
    prompt = build_xai_prompt(prompt_payload)

    chat = client.chat.completions.create(
        model=model,
        messages=[
            {
                "role": "system",
                "content": (
                    "너는 데이터 중심의 시니어 퀀트 애널리스트다. "
                    "근거 기반, 재현성 높은 분석을 수행한다."
                ),
            },
            {"role": "user", "content": prompt},
        ],
        temperature=0.2,
        max_tokens=1200,
        top_p=0.85,
    )

    result = chat.choices[0].message.content.strip()

    # 취소선 제거
    result = clean_strikethrough(result)
    return result
