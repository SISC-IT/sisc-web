FROM python:3.12-slim

WORKDIR /app

# 1. 의존성 설치
# XAI에서 요구하는 requirements.txt가 AI/xai 아래 있다고 가정
COPY AI/xai/requirements.txt /app/requirements.txt
RUN pip install --no-cache-dir -r requirements.txt

# 2. AI 전체 복사 (xai뿐만 아니라 config, dataset 등 필요 시)
COPY AI /app/AI

# 3. XAI 패키지 실행 (__init__.py)
CMD ["python", "-m", "AI.xai"]
