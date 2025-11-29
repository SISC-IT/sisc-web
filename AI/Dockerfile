FROM python:3.12-slim

WORKDIR /app


COPY requirements.txt /app/requirements.txt
RUN pip install --no-cache-dir -r requirements.txt

# 컨텍스트(AI 밑)를 /app/AI 로 복사
COPY . /app/AI

# __init__.py를 직접 실행
CMD ["python", "AI/xai/__init__.py"]
