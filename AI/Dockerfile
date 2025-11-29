FROM python:3.12-slim

WORKDIR /app

# 1. requirements.txt 경로: 컨텍스트 ./AI 기준이니 xai/부터 시작
COPY xai/requirements.txt /app/requirements.txt
RUN pip install --no-cache-dir -r requirements.txt

# 2. 컨텍스트 전체(AI 밑)를 /app/AI 로 복사
#    - 컨텍스트 ./AI 안의 모든 파일/폴더(xai 포함)가 /app/AI 아래로 들어감
COPY . /app/AI

# 3. 패키지 실행
#    컨테이너 안 경로 기준: /app/AI/xai/...
#    ⇒ python -m AI.xai 로 실행 가능
CMD ["python", "-m", "AI.xai"]
