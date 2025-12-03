FROM python:3.12-slim

WORKDIR /app

# 1. requirements 설치 (컨텍스트 = AI 이므로, AI/requirements.txt 기준)
COPY requirements.txt /app/requirements.txt
RUN pip install --no-cache-dir -r requirements.txt

# 2. AI 디렉토리 내용물을 /app 루트에 그대로 복사
#    => /app/libs/core/pipeline.py 이런 구조가 됨
COPY . /app

# 3. 파이썬 모듈 검색 경로에 /app 추가
ENV PYTHONPATH=/app

# 4. libs.core.pipeline 모듈을 "패키지로" 실행
CMD ["python", "-m", "libs.core.pipeline"]
