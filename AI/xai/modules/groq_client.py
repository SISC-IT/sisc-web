# AI/xai/modules/groq_client.py
from groq import Groq
import re   # 🔥 추가: 정규식 사용

DEFAULT_MODEL = "llama-3.3-70b-versatile"


def _remove_strikethrough(text: str) -> str:
    """
    XAI 리포트 생성 결과에서 ~~내용~~ 형태의 취소선과 내용을 제거한다.
    """
    # ~~내용~~ 전체 제거 (줄바꿈 포함)
    cleaned = re.sub(r"~~.*?~~", "", text, flags=re.DOTALL)

    # 2칸 이상 공백 제거
    cleaned = re.sub(r"\s{2,}", " ", cleaned)

    return cleaned.strip()


def ask_groq(prompt: str, api_key: str, model: str = DEFAULT_MODEL) -> str:
    """
    Groq API 호출 → 응답 텍스트 → 취소선 제거 → 최종 결과 반환
    """
    client = Groq(api_key=api_key)
    chat = client.chat.completions.create(
        model=model,
        messages=[
            {
                "role": "system",
                "content": (
                    "너는 데이터 기반으로만 판단하는 시니어 퀀트 애널리스트다. "
                    "항상 구체적 수치와 날짜로 논리를 뒷받침하라."
                ),
            },
            {"role": "user", "content": prompt},
        ],
        temperature=0.2,
        max_tokens=1100,
        top_p=0.85,
    )

    raw_output = chat.choices[0].message.content.strip()

    # 🔥 이 한 줄로 취소선 제거
    cleaned_output = _remove_strikethrough(raw_output)

    return cleaned_output
