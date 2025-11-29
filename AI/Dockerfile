FROM python:3.12-slim

WORKDIR /app

COPY requirements.txt /app/requirements.txt
RUN pip install --no-cache-dir -r requirements.txt


# 컨텍스트(AI 디렉토리)를 /app/AI 로 복사
COPY . /app/AI

# /app 을 파이썬 모듈 검색 경로에 추가
ENV PYTHONPATH=/app

# 1) 패키지 형태로 실행
#    pipeline 모듈 경로가 AI.lib.core.pipeline 이라고 가정
CMD ["python", "-m", "AI.libs.core.pipeline"]
