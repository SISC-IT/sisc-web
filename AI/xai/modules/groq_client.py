from groq import Groq

DEFAULT_MODEL = "llama-3.3-70b-versatile"

def ask_groq(prompt: str, api_key: str, model: str = DEFAULT_MODEL) -> str:
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
    return chat.choices[0].message.content.strip()
