FROM python:3.10-slim

WORKDIR /app

# 1. 라이브러리 설치
COPY requirements.txt /app/requirements.txt
RUN pip install --no-cache-dir -r requirements.txt

# 2. 소스 코드 복사
COPY . /app

# 3. 환경 변수 설정
ENV PYTHONPATH=/app

# 4. 실행 명령어 (데이터 수집 -> 분석 루틴 순차 실행)
CMD ["/bin/sh", "-c", "python modules/data_collector/run.py && python pipelines/daily_routine.py"]