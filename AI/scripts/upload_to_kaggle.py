# AI/scripts/upload_to_kaggle.py
"""
[목적]
  Kaggle 데이터셋 버전 업데이트
  (extract_to_parquet.py 실행 후 kaggle_data/ 에 parquet 파일이 있어야 함)

[실행 방법]
  python AI/scripts/upload_to_kaggle.py

[서버 크론잡에서]
  extract_to_parquet.py 완료 후 weekly_routine.py가 호출한다.
  KAGGLE_USERNAME, KAGGLE_KEY가 서버 환경 변수로 필요하다.
  AI/data/kaggle_data/는 .gitignore 대상이므로 dataset-metadata.json이 없으면 자동 생성한다.
"""
import json
import os
import shutil
import subprocess
import sys
import zipfile

from preflight_oos2024_kaggle_dataset import run_preflight

# ─────────────────────────────────────────────────────────────────────────────
# 경로 설정
# ─────────────────────────────────────────────────────────────────────────────
current_dir  = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../.."))

OUTPUT_DIR      = os.path.join(project_root, "AI", "data", "kaggle_data")
KAGGLE_USERNAME = os.environ.get("KAGGLE_USERNAME", "jihyeongkimm")
DATASET_SLUG    = os.environ.get("KAGGLE_DATASET_SLUG", "sisc-ai-trading-dataset")
DATASET_ID      = os.environ.get("KAGGLE_DATASET_ID", f"{KAGGLE_USERNAME}/{DATASET_SLUG}")
DATASET_TITLE   = os.environ.get("KAGGLE_DATASET_TITLE", "SISC AI Trading Dataset")
METADATA_PATH   = os.path.join(OUTPUT_DIR, "dataset-metadata.json")
CODE_ARCHIVE_NAME = "sisc_ai_code.zip"
CODE_ARCHIVE_PATH = os.path.join(OUTPUT_DIR, CODE_ARCHIVE_NAME)
CODE_SOURCE_DIR = os.path.join(OUTPUT_DIR, "sisc_code")


def run_oos2024_dataset_preflight() -> dict:
    """Kaggle 업로드 전에 OOS2024 데이터셋 조건을 강제 검증한다."""
    print("\n>> OOS2024 dataset preflight 실행")
    summary = run_preflight(
        OUTPUT_DIR,
        train_start=os.environ.get("TRAIN_START_DATE", "2021-01-01"),
        train_cutoff=os.environ.get("TRAIN_CUTOFF_DATE", "2024-06-30"),
        eval_start=os.environ.get("EVAL_START_DATE", "2024-09-03"),
        eval_end=os.environ.get("EVAL_END_DATE", "2024-12-31"),
        holdout_start=os.environ.get("HOLDOUT_START_DATE", "2025-01-01"),
        strict=True,
    )
    price_summary = summary.get("price_data_summary", {})
    print(f"   DATA_DATE_MIN={price_summary.get('data_min')}")
    print(f"   DATA_DATE_MAX={price_summary.get('data_max')}")
    print(f"   ROWS_2025_PLUS={price_summary.get('rows_2025_plus')}")
    print("   preflight 통과")
    return summary


def build_code_archive() -> None:
    """Package AI source code needed by Kaggle kernels into the dataset."""
    source_root = os.path.join(project_root, "AI")
    skipped_dirs = {"data", "docs", "tests", "backtests", "__pycache__", ".venv", "venv", "wandb"}
    skipped_suffixes = {".pyc", ".pyo"}

    if os.path.exists(CODE_ARCHIVE_PATH):
        os.remove(CODE_ARCHIVE_PATH)
    if os.path.exists(CODE_SOURCE_DIR):
        shutil.rmtree(CODE_SOURCE_DIR)

    def ignore_ai_files(_, names):
        return {
            name
            for name in names
            if name in skipped_dirs or any(name.endswith(suffix) for suffix in skipped_suffixes)
        }

    shutil.copytree(source_root, os.path.join(CODE_SOURCE_DIR, "AI"), ignore=ignore_ai_files)

    with zipfile.ZipFile(CODE_ARCHIVE_PATH, "w", compression=zipfile.ZIP_DEFLATED) as zf:
        for root, dirs, files in os.walk(os.path.join(CODE_SOURCE_DIR, "AI")):
            for file_name in files:
                full_path = os.path.join(root, file_name)
                rel_path = os.path.relpath(full_path, CODE_SOURCE_DIR)
                zf.write(full_path, rel_path)

    size = os.path.getsize(CODE_ARCHIVE_PATH) / (1024 * 1024)
    print(f">> Kaggle 코드 아카이브 생성: {CODE_ARCHIVE_NAME} ({size:.1f} MB)")
    print(f">> Kaggle 코드 디렉터리 생성: {CODE_SOURCE_DIR}")


def ensure_dataset_metadata() -> None:
    """Kaggle 데이터셋 메타데이터 파일이 없으면 크론 환경에서 생성한다."""
    if os.path.exists(METADATA_PATH):
        with open(METADATA_PATH, "r", encoding="utf-8") as f:
            metadata = json.load(f)
        metadata_id = metadata.get("id")
        if metadata_id != DATASET_ID:
            print(f"[오류] dataset-metadata.json id 불일치: {metadata_id} != {DATASET_ID}")
            sys.exit(1)
        return

    metadata = {
        "title": DATASET_TITLE,
        "id": DATASET_ID,
        "licenses": [{"name": "CC0-1.0"}],
    }
    with open(METADATA_PATH, "w", encoding="utf-8") as f:
        json.dump(metadata, f, ensure_ascii=False, indent=2)
        f.write("\n")
    print(f">> dataset-metadata.json 자동 생성: {METADATA_PATH}")

# ─────────────────────────────────────────────────────────────────────────────
# 업로드 전 parquet 파일 존재 여부 확인
# ─────────────────────────────────────────────────────────────────────────────
print("=" * 50)
print(">> upload_to_kaggle.py 시작")
print("=" * 50)

if not os.path.exists(OUTPUT_DIR):
    print(f"[오류] kaggle_data 폴더 없음: {OUTPUT_DIR}")
    print("   extract_to_parquet.py를 먼저 실행하세요.")
    sys.exit(1)

parquet_files = [f for f in os.listdir(OUTPUT_DIR) if f.endswith(".parquet")]
if not parquet_files:
    print(f"[오류] parquet 파일 없음: {OUTPUT_DIR}")
    print("   extract_to_parquet.py를 먼저 실행하세요.")
    sys.exit(1)

ensure_dataset_metadata()
run_oos2024_dataset_preflight()
build_code_archive()

if shutil.which("kaggle") is None:
    print("[오류] kaggle CLI가 PATH에 없습니다.")
    print("   서버 크론 환경에 kaggle 패키지를 설치하고 Kaggle 인증을 설정하세요.")
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
print(f"   대상: {DATASET_ID}")

result = subprocess.run(
    [
        "kaggle", "datasets", "version",
        "-p", OUTPUT_DIR,
        "-m", "Auto update: latest data",
    ],
    capture_output=True,
    text=True,
    cwd=OUTPUT_DIR
)

if result.returncode == 0:
    print("   업로드 완료")
    print(result.stdout)
else:
    print("   [오류] 업로드 실패")
    if result.stdout:
        print(result.stdout)
    print(result.stderr)
    sys.exit(1)

print("=" * 50)
print(">> upload_to_kaggle.py 완료")
print("=" * 50)
