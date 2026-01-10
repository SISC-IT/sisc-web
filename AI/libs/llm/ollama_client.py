# libs/llm/ollama_client.py
import os
import requests
from typing import Optional
from langchain_community.llms import Ollama

# ---- 기본 설정 (환경변수로 오버라이드 가능) ----
OLLAMA_BASE_URL = os.environ.get("OLLAMA_BASE_URL", "http://127.0.0.1:11434")
OLLAMA_MODEL = os.environ.get("OLLAMA_MODEL", "llama3.2")

def _ollama_alive(base_url: str, timeout: float = 3.0) -> bool:
    """
    Ollama 서버 헬스체크: /api/tags 로 간단 확인
    """
    try:
        r = requests.get(f"{base_url}/api/tags", timeout=timeout)
        return r.ok
    except requests.exceptions.RequestException:
        return False

def _model_available(base_url: str, model: str) -> bool:
    """
    지정 모델이 로컬 Ollama에 존재하는지 확인
    """
    try:
        r = requests.get(f"{base_url}/api/tags", timeout=5)
        r.raise_for_status()
        tags = r.json().get("models", [])
        names = {m.get("name") for m in tags if isinstance(m, dict)}
        # ollama는 "llama3.2" 또는 "llama3.2:latest" 식으로 존재 가능
        return model in names or f"{model}:latest" in names
    except Exception:
        return False

def get_ollama_client(
    model: Optional[str] = None,
    base_url: Optional[str] = None,
    # langchain 0.2+에서 request_timeout 인자를 직접 받지 않는 경우가 있어 주석 처리
    # request_timeout: float = 60.0,
) -> Ollama:
    """
    Ollama LangChain LLM 클라이언트 생성
    - 서버와 모델 존재 여부를 사전 점검
    """
    model = model or OLLAMA_MODEL
    base_url = base_url or OLLAMA_BASE_URL

    if not _ollama_alive(base_url):
        raise RuntimeError(
            f"[연결 실패] Ollama 서버에 접속할 수 없습니다. llama3.2 설치 여부 확인해주세요.\n"
            f"- base_url: {base_url}\n"
            f"- 조치: (1) 'ollama serve' 실행 여부 확인 (2) 방화벽/프록시 (NO_PROXY=localhost,127.0.0.1) (3) 11434 포트 개방\n"
            f"- 테스트: curl {base_url}/api/tags"
        )

    if not _model_available(base_url, model):
        raise RuntimeError(
            f"[모델 없음] '{model}' 모델이 Ollama에 없습니다.\n"
            f"- 조치: ollama pull {model}\n"
            f"- 보유 모델 확인: curl {base_url}/api/tags"
        )

    return Ollama(
        model=model,
        base_url=base_url,
        # 필요 시 model_kwargs로 세부 파라미터 전달 가능
        # model_kwargs={"num_ctx": 4096},
    )
