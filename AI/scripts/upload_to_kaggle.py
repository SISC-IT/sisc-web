# AI/scripts/upload_to_kaggle.py
"""
[목적]
  1. kaggle_data/ 폴더에 최신 모델 코드 복사
  2. Kaggle 데이터셋 버전 업데이트

[실행 방법]
  python AI/scripts/upload_to_kaggle.py

[GitHub Actions에서]
  env로 KAGGLE_USERNAME, KAGGLE_KEY 주입 후 자동 실행
"""
import os
import shutil
import subprocess
import sys

# ─────────────────────────────────────────────────────────────────────────────
# 경로 설정
# ─────────────────────────────────────────────────────────────────────────────
current_dir  = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../.."))

OUTPUT_DIR = os.path.join(project_root, "AI/data/kaggle_data")
KAGGLE_USERNAME = os.environ.get("KAGGLE_USERNAME", "jihyeongkimm")
DATASET_SLUG    = "sisc-ai-trading-dataset"

# ─────────────────────────────────────────────────────────────────────────────
# Kaggle 데이터셋 버전 업데이트
# ─────────────────────────────────────────────────────────────────────────────
print(f"\n>> [2/2] Kaggle 데이터셋 업로드 중...")
print(f"   대상: {KAGGLE_USERNAME}/{DATASET_SLUG}")

result = subprocess.run(
    [
        "kaggle", "datasets", "version",
        "-p", KAGGLE_DATA_DIR,
        "-m", "Auto update: latest code + data",
        "--dir-mode", "zip"
    ],
    capture_output=True,
    text=True
)

if result.returncode == 0:
    print("   업로드 완료!")
    print(result.stdout)
else:
    print("   [오류] 업로드 실패!")
    print(result.stderr)
    sys.exit(1)

print("=" * 50)
print(">> upload_to_kaggle.py 완료")
