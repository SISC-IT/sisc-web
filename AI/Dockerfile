FROM python:3.10-slim

WORKDIR /app

# 2. 라이브러리 설치
COPY requirements.txt /app/requirements.txt
RUN pip install --no-cache-dir -r requirements.txt

# 3. 소스 코드 복사
COPY . /app

# 4. 환경 변수 설정
ENV PYTHONPATH=/app

# 5. 실행 명령어 (배치 작업용, 경로 수정 완료)
CMD ["python", "pipelines/daily_routine.py"]