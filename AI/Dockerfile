FROM python:3.10-slim

WORKDIR /app

# 1. 운영용 requirements 설치
# requirements.prod.txt 안에 TensorFlow + CPU 전용 PyTorch가 함께 들어 있습니다.
COPY requirements*.txt /app/AI/
RUN python -m pip install --upgrade pip && \
    python -m pip install --no-cache-dir -r AI/requirements.prod.txt

# 2. 소스 코드 복사
COPY . /app/AI

# 3. 환경 변수 설정
ENV PYTHONPATH=/app

# 4. 실행 명령어 (데이터 수집 -> 분석 루틴 순차 실행)
CMD ["/bin/sh", "-c", "python AI/modules/data_collector/crawler_bot.py && python AI/pipelines/daily_routine.py"]
