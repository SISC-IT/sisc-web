"""
[Gemini 연결 진단 테스트]
- Gemini API 연결 여부를 운영 로직 수정 없이 점검합니다.
- 모델 목록 조회, health check, 실제 텍스트 생성 호출을 각각 분리해서 확인합니다.
"""

import os
import sys
from pathlib import Path

from dotenv import load_dotenv


project_root = Path(__file__).resolve().parents[2]
if str(project_root) not in sys.path:
    sys.path.append(str(project_root))

load_dotenv(project_root / ".env")

from AI.libs.llm.gemini import DEFAULT_GEMINI_MODEL, GeminiClient


def main():
    print("=== Gemini 연결 진단 시작 ===")

    api_key = os.environ.get("GEMINI_API_KEY")
    if not api_key:
        print("[Fail] GEMINI_API_KEY가 없습니다.")
        return

    masked_key = f"{api_key[:6]}...{api_key[-4:]}" if len(api_key) >= 10 else "***"
    print(f"[Info] GEMINI_API_KEY 감지: {masked_key}")

    try:
        client = GeminiClient(model_name=DEFAULT_GEMINI_MODEL)
        print(f"[Info] GeminiClient 생성 성공 (model={client.model_name})")
    except Exception as e:
        print(f"[Fail] GeminiClient 생성 실패: {type(e).__name__}: {e}")
        return

    try:
        models = list(client.client.models.list())
        names = [getattr(model, "name", "") for model in models]
        has_target = any(
            name in {DEFAULT_GEMINI_MODEL, f"models/{DEFAULT_GEMINI_MODEL}"}
            for name in names
        )

        print(f"[Check] models.list(): 성공 ({len(names)}개)")
        print(f"[Check] target model 발견: {has_target}")
        if names:
            preview = ", ".join(names[:10])
            print(f"[Info] 모델 미리보기: {preview}")
    except Exception as e:
        print(f"[Fail] models.list() 실패: {type(e).__name__}: {e}")

    try:
        health = client.get_health()
        print(f"[Check] GeminiClient.get_health(): {health}")
    except Exception as e:
        print(f"[Fail] get_health() 실패: {type(e).__name__}: {e}")

    try:
        prompt = "Reply with exactly one short Korean word meaning the service is reachable."
        text = client.generate_text(prompt, temperature=0.1, max_tokens=16)
        if text:
            print(f"[Check] generate_text(): 성공 -> {text!r}")
        else:
            print("[Fail] generate_text(): 빈 문자열 반환")
    except Exception as e:
        print(f"[Fail] generate_text() 실패: {type(e).__name__}: {e}")

    print("=== Gemini 연결 진단 종료 ===")


if __name__ == "__main__":
    main()
