FROM python:3.12-slim

WORKDIR /app

# AI 디렉토리 전체를 /app/AI 로 복사 (컨텍스트 = AI)
COPY . /app/AI

# XAI 패키지 실행 (__init__.py 실행)
CMD ["python", "-m", "AI.xai"]
