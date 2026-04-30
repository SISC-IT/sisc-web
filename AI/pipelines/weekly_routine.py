# AI/pipelines/weekly_routine.py
"""
[주간 Kaggle 재학습 파이프라인]
- 이 파이프라인은 운영 서버 크론잡으로 실행한다.
- GitHub Actions는 주간 학습 자동화에 사용하지 않는다.
- 실행 예시(서버 시간이 KST인 경우):
  0 2 * * 1 cd /app/sisc-web && /usr/bin/python AI/pipelines/weekly_routine.py >> /var/log/sisc-weekly-training.log 2>&1
  매주 월요일 02:00 KST에 실행한다.

[daily_routine.py와의 차이]
- daily_routine.py: 매일 → 매매 신호 생성 + 주문 실행
- weekly_routine.py: 매주 → 최신 데이터로 모델 재학습 + 가중치 배포

[실행 순서]
1. DB 최신 데이터 추출 → parquet
2. Kaggle 데이터셋 업데이트
3. Kaggle 노트북 학습 트리거 (모델별 순차 실행)
4. 학습 완료 후 가중치 다운로드
5. 서버에 가중치 배포

[필요 환경 변수]
- DB 접속: DB_HOST, DB_PORT, DB_USER, DB_PASSWORD, DB_NAME
- Kaggle 인증: KAGGLE_USERNAME, KAGGLE_KEY
- 서버 배포: SSH_HOST, SSH_USER, SSH_PRIVATE_KEY, SSH_PORT, SERVER_WEIGHTS_PATH
- SSH 터널 접속이 필요하면 DB_CONNECT_MODE=ssh_tunnel을 명시한다.

[실패 시 재실행 기준]
- DB 추출 실패: DB 접속 정보와 권한을 확인한 뒤 처음부터 재실행한다.
- Kaggle 업로드 실패: parquet와 dataset-metadata.json, Kaggle 인증을 확인한 뒤 --skip-extract로 재실행할 수 있다.
- Kaggle 학습 실패: python AI/scripts/trigger_training.py --start-from <모델명> 으로 실패 모델부터 재실행한다.
- 가중치 다운로드 또는 배포 실패: Kaggle Output과 SSH 정보를 확인한 뒤 해당 스크립트를 개별 재실행한다.
"""

import os
import sys
import argparse
import subprocess
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
        log(f">> [{desc}] 완료")
        return True
    else:
        log(f">> [{desc}] 실패")
        return False


def run_weekly_pipeline(
    skip_extract: bool = False,
    skip_upload: bool = False,
    skip_deploy: bool = False,
):
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
    log(f" skip_deploy:  {skip_deploy}")
    log("=" * 50)

    # ─────────────────────────────────────────────────────
    # STEP 1. DB 추출 → parquet
    # 서버 DB에서 최신 데이터를 parquet으로 추출
    # 서버 크론: DB_HOST, DB_PORT로 직접 접속
    # 로컬 터널/SSH 터널은 extract_to_parquet.py에서 DB_CONNECT_MODE로 명시
    # ─────────────────────────────────────────────────────
    if not skip_extract:
        success = run_script("extract_to_parquet.py", "DB 추출")
        if not success:
            log("[오류] DB 추출 실패. 파이프라인 중단.")
            return False
    else:
        log(">> [DB 추출] 스킵")

    # ─────────────────────────────────────────────────────
    # STEP 2. Kaggle 데이터셋 업데이트
    # 최신 parquet와 현재 레포 학습 코드를 Kaggle에 업로드
    # ─────────────────────────────────────────────────────
    if not skip_upload:
        success = run_script("upload_to_kaggle.py", "Kaggle 업로드")
        if not success:
            log("[오류] Kaggle 업로드 실패. 파이프라인 중단.")
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
        log("[오류] 학습 트리거 실패. 파이프라인 중단.")
        return False

    # ─────────────────────────────────────────────────────
    # STEP 4. 가중치 다운로드
    # Kaggle Output → 로컬 AI/data/weights/
    # ─────────────────────────────────────────────────────
    success = run_script("download_weights.py", "가중치 다운로드")
    if not success:
        log("[오류] 가중치 다운로드 실패. 파이프라인 중단.")
        return False

    # ─────────────────────────────────────────────────────
    # STEP 5. 서버 배포
    # SCP로 운영 서버 AI/data/weights/ 에 가중치 덮어씌움
    # ─────────────────────────────────────────────────────
    if not skip_deploy:
        success = run_script("deploy_to_server.py", "server deploy")
        if not success:
            log("[error] server deploy failed.")
            return False
    else:
        log(">> [server deploy] skipped")

    # ─────────────────────────────────────────────────────
    # 완료
    # ─────────────────────────────────────────────────────
    elapsed = datetime.now() - start_time
    hours   = int(elapsed.total_seconds() // 3600)
    minutes = int((elapsed.total_seconds() % 3600) // 60)

    log("=" * 50)
    log("주간 학습 파이프라인 완료")
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
    parser.add_argument(
        "--skip-deploy",
        action="store_true",
        default=os.environ.get("SKIP_SERVER_DEPLOY", "").strip().lower() in {"1", "true", "yes", "y"},
        help="Skip SSH/SCP deployment after downloading weights."
    )
    args = parser.parse_args()

    success = run_weekly_pipeline(
        skip_extract = args.skip_extract,
        skip_upload  = args.skip_upload,
        skip_deploy  = args.skip_deploy,
    )
    sys.exit(0 if success else 1)
