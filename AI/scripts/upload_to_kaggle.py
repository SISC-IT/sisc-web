# AI/scripts/upload_to_kaggle.py
"""
[목적]
  Kaggle 데이터셋 버전 업데이트
  (extract_to_parquet.py 실행 후 kaggle_data/ 에 parquet 파일이 있어야 함)

[실행 방법]
  python AI/scripts/upload_to_kaggle.py

[GitHub Actions에서]
  extract_to_parquet.py 완료 후 자동 실행
  env로 KAGGLE_USERNAME, KAGGLE_KEY 주입
"""
import os
import subprocess
import sys

# ─────────────────────────────────────────────────────────────────────────────
# 경로 설정
# ─────────────────────────────────────────────────────────────────────────────
current_dir  = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../.."))

OUTPUT_DIR      = os.path.join(project_root, "AI/data/kaggle_data")
KAGGLE_USERNAME = os.environ.get("KAGGLE_USERNAME", "jihyeongkimm")
DATASET_SLUG    = "sisc-ai-trading-dataset"

# ─────────────────────────────────────────────────────────────────────────────
# 업로드 전 parquet 파일 존재 여부 확인
# ─────────────────────────────────────────────────────────────────────────────
print("=" * 50)
print(">> upload_to_kaggle.py 시작")
print("=" * 50)

if not os.path.exists(OUTPUT_DIR):
    print(f"❌ kaggle_data 폴더 없음: {OUTPUT_DIR}")
    print("   extract_to_parquet.py를 먼저 실행하세요.")
    sys.exit(1)

parquet_files = [f for f in os.listdir(OUTPUT_DIR) if f.endswith(".parquet")]
if not parquet_files:
    print(f"❌ parquet 파일 없음: {OUTPUT_DIR}")
    print("   extract_to_parquet.py를 먼저 실행하세요.")
    sys.exit(1)

print(f">> 업로드할 parquet 파일: {len(parquet_files)}개")
for f in parquet_files:
    fpath = os.path.join(OUTPUT_DIR, f)
    size  = os.path.getsize(fpath) / (1024 * 1024)
    print(f"   - {f} ({size:.1f} MB)")

# ─────────────────────────────────────────────────────────────────────────────
# Kaggle 데이터셋 버전 업데이트
# ─────────────────────────────────────────────────────────────────────────────
print(f"\n>> Kaggle 데이터셋 업로드 중...")
print(f"   대상: {KAGGLE_USERNAME}/{DATASET_SLUG}")

result = subprocess.run(
    [
        "kaggle", "datasets", "version",
        "-p", OUTPUT_DIR,
        "-m", "Auto update: latest data",
        "--dir-mode", "zip"
    ],
    capture_output=True,
    text=True
)

if result.returncode == 0:
    print("   업로드 완료! ✅")
    print(result.stdout)
else:
    print("   [오류] 업로드 실패! ❌")
    print(result.stderr)
    sys.exit(1)

print("=" * 50)
print(">> upload_to_kaggle.py 완료")
print("=" * 50)
