# AI/scripts/trigger_training.py
"""
[목적]
  Kaggle 노트북 4개를 순서대로 학습 트리거

[실행 방법]
  python AI/scripts/trigger_training.py
  python AI/scripts/trigger_training.py --start-from iTransformer

[전제 조건]
  - Kaggle 노트북이 미리 만들어져 있어야 함
  - kaggle_notebooks/ 폴더 및 push 불필요
  - Kaggle 노트북은 Kaggle 웹에서 직접 관리

[GitHub Actions에서]
  upload_to_kaggle.py 실행 후 자동 실행
"""
import os
import subprocess
import sys
import time
import argparse

KAGGLE_USERNAME = os.environ.get("KAGGLE_USERNAME", "jihyeongkimm")

NOTEBOOKS = [
    {"name": "PatchTST",     "slug": f"{KAGGLE_USERNAME}/patchtst-training"},
    {"name": "Transformer",  "slug": f"{KAGGLE_USERNAME}/transformer-training"},
    {"name": "iTransformer", "slug": f"{KAGGLE_USERNAME}/itransformer-training"},
    {"name": "TCN",          "slug": f"{KAGGLE_USERNAME}/tcn-training"},
]


def trigger_notebook(notebook: dict) -> bool:
    """Kaggle API로 노트북 실행 트리거 (push 없이)"""
    print(f"\n>> [{notebook['name']}] 학습 트리거 중...")
    print(f"   슬러그: {notebook['slug']}")

    result = subprocess.run(
        ["kaggle", "kernels", "pull", notebook['slug'], "-p", "/tmp/kaggle_trigger"],
        capture_output=True, text=True
    )

    # pull 실패해도 무관 - run trigger만 필요
    result = subprocess.run(
        ["kaggle", "kernels", "push", "-p", "/tmp/kaggle_trigger"],
        capture_output=True, text=True
    )

    # push 없이 API로 직접 트리거
    import json
    try:
        import requests
        kaggle_json = os.path.expanduser("~/.kaggle/kaggle.json")
        with open(kaggle_json) as f:
            creds = json.load(f)
        username = creds.get("username", KAGGLE_USERNAME)
        key      = creds.get("key", "")

        owner, kernel = notebook['slug'].split("/")
        url = f"https://www.kaggle.com/api/v1/kernels/{owner}/{kernel}/run"
        resp = requests.post(url, auth=(username, key))

        if resp.status_code in [200, 201, 202]:
            print(f"   [{notebook['name']}] 트리거 성공!")
            print(f"   확인: https://www.kaggle.com/code/{notebook['slug']}")
            return True
        else:
            print(f"   [{notebook['name']}] API 트리거 실패: {resp.status_code}")
            print(f"   {resp.text[:200]}")
            return False
    except Exception as e:
        print(f"   [{notebook['name']}] 트리거 오류: {e}")
        return False


def wait_for_notebook(notebook: dict, timeout_hours: int = 12) -> bool:
    """노트북 완료까지 대기 (polling)"""
    print(f"\n>> [{notebook['name']}] 완료 대기 중...")
    slug       = notebook['slug']
    max_checks = timeout_hours * 12
    check_count = 0

    while check_count < max_checks:
        result = subprocess.run(
            ["kaggle", "kernels", "status", slug],
            capture_output=True, text=True
        )
        output = result.stdout.lower()

        if "complete" in output:
            print(f"   [{notebook['name']}] 학습 완료!")
            return True
        elif "error" in output or "failed" in output:
            print(f"   [{notebook['name']}] 학습 실패!")
            print(result.stdout)
            return False
        elif "running" in output or "queued" in output:
            check_count += 1
            elapsed = check_count * 5
            print(f"   [{notebook['name']}] 학습 중... ({elapsed}분 경과)")
        else:
            check_count += 1
            print(f"   [{notebook['name']}] 상태: {result.stdout.strip()}")

        time.sleep(300)

    print(f"   [{notebook['name']}] 타임아웃 ({timeout_hours}시간 초과)")
    return False


# ─────────────────────────────────────────────────────────────────────────────
# 메인
# ─────────────────────────────────────────────────────────────────────────────
parser = argparse.ArgumentParser()
parser.add_argument("--start-from", type=str, default=None,
                    help="특정 모델부터 시작 (PatchTST/Transformer/iTransformer/TCN)")
args, _ = parser.parse_known_args()

start_idx = 0
if args.start_from:
    names = [n['name'] for n in NOTEBOOKS]
    if args.start_from in names:
        start_idx = names.index(args.start_from)
        print(f">> [{args.start_from}]부터 시작합니다.")
    else:
        print(f">> [경고] 모델명 '{args.start_from}' 없음. 처음부터 시작합니다.")

notebooks_to_run = NOTEBOOKS[start_idx:]

print("=" * 50)
print(">> trigger_training.py 시작")
print(f">> 학습 대상: {[n['name'] for n in notebooks_to_run]}")
print("=" * 50)

failed = []

for notebook in notebooks_to_run:
    success = trigger_notebook(notebook)
    if not success:
        failed.append(notebook['name'])
        print(f"\n>> [{notebook['name']}] 실패. 다음 모델로 넘어갑니다.")
        continue

    success = wait_for_notebook(notebook)
    if not success:
        failed.append(notebook['name'])
        print(f"\n>> [{notebook['name']}] 학습 실패. 다음 모델로 넘어갑니다.")

    time.sleep(60)

print("\n" + "=" * 50)
if failed:
    print(f">> 실패한 모델: {failed}")
    print(f">> 재시작: python AI/scripts/trigger_training.py --start-from {failed[0]}")
    sys.exit(1)
else:
    print(">> 전체 학습 완료!")
print("=" * 50)
