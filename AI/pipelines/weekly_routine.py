# AI/pipelines/weekly_routine.py
"""
[주간 자동화 파이프라인]
- 매주 일요일 새벽 2시 자동 실행 (GitHub Actions)
- 또는 수동 실행 가능

[daily_routine.py와의 차이]
- daily_routine.py: 매일 → 매매 신호 생성 + 주문 실행
- weekly_routine.py: 매주 → 최신 데이터로 모델 재학습 + 가중치 배포

[실행 순서]
1. DB 최신 데이터 추출 → parquet
2. Kaggle 데이터셋 업데이트
3. Kaggle 노트북 학습 트리거 (모델별 순차 실행)
4. 학습 완료 후 가중치 다운로드
5. 서버에 가중치 배포

[실행 방법]
  # 로컬 (Termius 터널 켜둔 상태)
  python AI/pipelines/weekly_routine.py

  # GitHub Actions에서 자동 실행
  .github/workflows/train.yml 참고
"""

import os
import sys
import argparse
import subprocess
import traceback
from datetime import datetime

# ─────────────────────────────────────────────────────────────────────────────
# 경로 설정
# ─────────────────────────────────────────────────────────────────────────────
current_dir  = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../.."))
if project_root not in sys.path:
    sys.path.append(project_root)

SCRIPTS_DIR = os.path.join(project_root, "AI/scripts")


def log(msg: str):
    """타임스탬프 포함 로그"""
    now = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    print(f"[{now}] {msg}")


def run_script(script_name: str, desc: str) -> bool:
    """
    AI/scripts/ 하위 스크립트 실행
    성공하면 True, 실패하면 False 반환
    """
    script_path = os.path.join(SCRIPTS_DIR, script_name)
    log(f">> [{desc}] 시작...")

    result = subprocess.run(
        [sys.executable, script_path],
        cwd=project_root,
    )

    if result.returncode == 0:
        log(f">> [{desc}] 완료! ✅")
        return True
    else:
        log(f">> [{desc}] 실패! ❌")
        return False


def run_weekly_pipeline(skip_extract: bool = False, skip_upload: bool = False):
    """
    주간 학습 파이프라인 메인

    skip_extract: True면 DB 추출 스킵 (parquet 이미 있을 때)
    skip_upload:  True면 Kaggle 업로드 스킵 (데이터 변경 없을 때)
    """
    start_time = datetime.now()

    log("=" * 50)
    log(" 주간 학습 파이프라인 시작")
    log(f" skip_extract: {skip_extract}")
    log(f" skip_upload:  {skip_upload}")
    log("=" * 50)

    # ─────────────────────────────────────────────────────
    # STEP 1. DB 추출 → parquet
    # 서버 DB에서 최신 데이터를 parquet으로 추출
    # 로컬: Termius 터널 필요
    # Actions: paramiko SSH 터널 자동 오픈
    # ─────────────────────────────────────────────────────
    if not skip_extract:
        success = run_script("extract_to_parquet.py", "DB 추출")
        if not success:
            log("❌ DB 추출 실패. 파이프라인 중단.")
            return False
    else:
        log(">> [DB 추출] 스킵")

    # ─────────────────────────────────────────────────────
    # STEP 2. Kaggle 데이터셋 업데이트
    # 최신 parquet + GitHub 최신 코드 Kaggle에 업로드
    # ─────────────────────────────────────────────────────
    if not skip_upload:
        success = run_script("upload_to_kaggle.py", "Kaggle 업로드")
        if not success:
            log("❌ Kaggle 업로드 실패. 파이프라인 중단.")
            return False
    else:
        log(">> [Kaggle 업로드] 스킵")

    # ─────────────────────────────────────────────────────
    # STEP 3. Kaggle 노트북 학습 트리거 + 완료 대기
    # PatchTST → Transformer 순서로 순차 실행
    # (iTransformer, TCN 머지 후 추가 예정)
    # ─────────────────────────────────────────────────────
    success = run_script("trigger_training.py", "Kaggle 학습 트리거")
    if not success:
        log("❌ 학습 트리거 실패. 파이프라인 중단.")
        return False

    # ─────────────────────────────────────────────────────
    # STEP 4. 가중치 다운로드
    # Kaggle Output → 로컬 AI/data/weights/
    # ─────────────────────────────────────────────────────
    success = run_script("download_weights.py", "가중치 다운로드")
    if not success:
        log("❌ 가중치 다운로드 실패. 파이프라인 중단.")
        return False

    # ─────────────────────────────────────────────────────
    # STEP 5. 서버 배포
    # SCP로 운영 서버 AI/data/weights/ 에 가중치 덮어씌움
    # ─────────────────────────────────────────────────────
    success = run_script("deploy_to_server.py", "서버 배포")
    if not success:
        log("❌ 서버 배포 실패.")
        return False

    # ─────────────────────────────────────────────────────
    # 완료
    # ─────────────────────────────────────────────────────
    elapsed = datetime.now() - start_time
    hours   = int(elapsed.total_seconds() // 3600)
    minutes = int((elapsed.total_seconds() % 3600) // 60)

    log("=" * 50)
    log("✅ 주간 학습 파이프라인 완료!")
    log(f"   총 소요 시간: {hours}시간 {minutes}분")
    log("   → 새 가중치로 daily_routine.py 동작 가능")
    log("=" * 50)
    return True


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="주간 모델 학습 파이프라인")
    parser.add_argument(
        "--skip-extract",
        action="store_true",
        help="DB 추출 스킵 (parquet 이미 있을 때)"
    )
    parser.add_argument(
        "--skip-upload",
        action="store_true",
        help="Kaggle 업로드 스킵 (데이터 변경 없을 때)"
    )
    args = parser.parse_args()

    run_weekly_pipeline(
        skip_extract = args.skip_extract,
        skip_upload  = args.skip_upload,
    )
