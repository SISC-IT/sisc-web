# AI/scripts/trigger_training.py
"""
[목적]
  Kaggle 노트북 4개를 순서대로 학습 트리거

[실행 방법]
  python AI/scripts/trigger_training.py

[전제 조건]
  - kaggle_notebooks/ 폴더에 각 모델별 kernel-metadata.json 있어야 함
  - Kaggle 노트북이 미리 만들어져 있어야 함

[GitHub Actions에서]
  upload_to_kaggle.py 실행 후 자동 실행
"""
import os
import subprocess
import sys
import time

# ─────────────────────────────────────────────────────────────────────────────
# 경로 설정
# ─────────────────────────────────────────────────────────────────────────────
current_dir  = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../.."))

KAGGLE_USERNAME = os.environ.get("KAGGLE_USERNAME", "jihyeongkimm")

# ─────────────────────────────────────────────────────────────────────────────
# 학습할 노트북 목록
# 순서대로 트리거 (병렬 실행 시 GPU 한도 초과할 수 있어서 순차 실행)
# ─────────────────────────────────────────────────────────────────────────────
NOTEBOOKS = [
    {
        "name"       : "PatchTST",
        "slug"       : "patchtst-training",
        "notebook_dir": os.path.join(project_root, "AI/kaggle_notebooks/patchtst"),
    },
    {
        "name"       : "Transformer",
        "slug"       : "transformer-training",
        "notebook_dir": os.path.join(project_root, "AI/kaggle_notebooks/transformer"),
    },
     {
         "name"       : "iTransformer",
         "slug"       : "itransformer-training",
         "notebook_dir": os.path.join(project_root, "AI/kaggle_notebooks/itransformer"),
     },
     {
         "name"       : "TCN",
         "slug"       : "tcn-training",
         "notebook_dir": os.path.join(project_root, "AI/kaggle_notebooks/tcn"),
    },
]


def trigger_notebook(notebook: dict) -> bool:
    """노트북 학습 트리거. 성공하면 True 반환"""
    print(f"\n>> [{notebook['name']}] 학습 트리거 중...")

    if not os.path.exists(notebook['notebook_dir']):
        print(f"   [오류] 노트북 폴더 없음: {notebook['notebook_dir']}")
        return False

    result = subprocess.run(
        ["kaggle", "kernels", "push", "-p", notebook['notebook_dir']],
        capture_output=True,
        text=True
    )

    if result.returncode == 0:
        print(f"   [{notebook['name']}] 트리거 성공!")
        print(f"   확인: https://www.kaggle.com/code/{KAGGLE_USERNAME}/{notebook['slug']}")
        return True
    else:
        print(f"   [{notebook['name']}] 트리거 실패!")
        print(result.stderr)
        return False


def wait_for_notebook(notebook: dict, timeout_hours: int = 12) -> bool:
    """
    노트북 완료까지 대기 (polling 방식)
    timeout_hours: 최대 대기 시간 (기본 12시간)
    """
    print(f"\n>> [{notebook['name']}] 완료 대기 중...")
    slug       = f"{KAGGLE_USERNAME}/{notebook['slug']}"
    max_checks = timeout_hours * 12  # 5분마다 체크
    check_count = 0

    while check_count < max_checks:
        result = subprocess.run(
            ["kaggle", "kernels", "status", slug],
            capture_output=True,
            text=True
        )

        output = result.stdout.lower()

        if "complete" in output:
            print(f"   [{notebook['name']}] 학습 완료!")
            return True
        elif "error" in output or "failed" in output:
            print(f"   [{notebook['name']}] 학습 실패!")
            print(result.stdout)
            return False
        elif "running" in output:
            check_count += 1
            elapsed = check_count * 5
            print(f"   [{notebook['name']}] 학습 중... ({elapsed}분 경과)")
        else:
            check_count += 1
            print(f"   [{notebook['name']}] 상태: {result.stdout.strip()}")

        time.sleep(300)  # 5분 대기

    print(f"   [{notebook['name']}] 타임아웃 ({timeout_hours}시간 초과)")
    return False


print("=" * 50)
print(">> trigger_training.py 시작")
print(f">> 학습 대상: {[n['name'] for n in NOTEBOOKS]}")
print("=" * 50)

failed = []

for notebook in NOTEBOOKS:
    # 트리거
    success = trigger_notebook(notebook)
    if not success:
        failed.append(notebook['name'])
        continue

    # 완료 대기
    success = wait_for_notebook(notebook)
    if not success:
        failed.append(notebook['name'])

    # 다음 모델 시작 전 잠깐 대기
    time.sleep(60)

print("\n" + "=" * 50)
if failed:
    print(f">> 실패한 모델: {failed}")
    sys.exit(1)
else:
    print(">> 전체 학습 완료!")
print("=" * 50)
