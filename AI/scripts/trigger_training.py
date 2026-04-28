# AI/scripts/trigger_training.py
"""
[목적]
  서버 크론잡에서 Kaggle 커널 4개를 순서대로 실행한다.

[운영 전제]
  - GitHub Actions는 사용하지 않는다.
  - Kaggle 웹 노트북을 수동으로 실행하지 않는다.
  - 이 스크립트가 레포의 학습 모듈과 kernel-metadata.json을 임시 디렉터리에 생성한 뒤
    kaggle kernels push 방식으로 커널 새 버전을 실행한다.
  - Kaggle 웹에서 같은 커널을 직접 수정한 내용은 다음 크론 실행 때 덮어써질 수 있다.

[실행 방법]
  python AI/scripts/trigger_training.py
  python AI/scripts/trigger_training.py --start-from iTransformer
  python AI/scripts/trigger_training.py --dry-run
"""
import argparse
import json
import os
import shutil
import subprocess
import sys
import tempfile
import textwrap
import time
from pathlib import Path


current_dir = Path(__file__).resolve().parent
project_root = current_dir.parents[1]

KAGGLE_USERNAME = os.environ.get("KAGGLE_USERNAME", "jihyeongkimm")
KAGGLE_DATASET_ID = os.environ.get(
    "KAGGLE_DATASET_ID",
    f"{KAGGLE_USERNAME}/sisc-ai-trading-dataset",
)
KAGGLE_DATASET_MOUNT = os.environ.get(
    "KAGGLE_DATASET_MOUNT",
    "/kaggle/input/sisc-ai-trading-dataset",
)

MODEL_SPECS = [
    {
        "name": "PatchTST",
        "slug": "patchtst-training",
        "title": "PatchTST Training",
        "module": "AI.modules.signal.models.patchtst.train_kaggle",
    },
    {
        "name": "Transformer",
        "slug": "transformer-training",
        "title": "Transformer Training",
        "module": "AI.modules.signal.models.transformer.train_kaggle",
    },
    {
        "name": "iTransformer",
        "slug": "itransformer-training",
        "title": "iTransformer Training",
        "module": "AI.modules.signal.models.itransformer.train_kaggle",
    },
    {
        "name": "TCN",
        "slug": "tcn-training",
        "title": "TCN Training",
        "module": "AI.modules.signal.models.TCN.train_kaggle",
    },
]


def ensure_kaggle_cli() -> bool:
    """서버 크론 환경에 Kaggle CLI가 있는지 확인한다."""
    if shutil.which("kaggle") is None:
        print("[오류] kaggle CLI가 PATH에 없습니다.")
        print("   서버에 kaggle 패키지와 인증 파일 또는 KAGGLE_USERNAME/KAGGLE_KEY를 설정하세요.")
        return False
    return True


def write_kernel_files(work_dir: Path, spec: dict) -> None:
    """Kaggle 커널 push에 필요한 코드와 메타데이터를 임시 생성한다."""
    metadata = {
        "id": f"{KAGGLE_USERNAME}/{spec['slug']}",
        "title": spec["title"],
        "code_file": "kernel.py",
        "language": "python",
        "kernel_type": "script",
        "is_private": True,
        "enable_gpu": True,
        "enable_internet": False,
        "dataset_sources": [KAGGLE_DATASET_ID],
        "competition_sources": [],
        "kernel_sources": [],
    }
    (work_dir / "kernel-metadata.json").write_text(
        json.dumps(metadata, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )

    runner = f"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
os.environ.setdefault("PARQUET_DIR", "{KAGGLE_DATASET_MOUNT}")
os.environ.setdefault("WEIGHTS_DIR", "/kaggle/working")

from {spec["module"]} import train

train()
"""
    (work_dir / "kernel.py").write_text(textwrap.dedent(runner).lstrip(), encoding="utf-8")

    def ignore_ai_files(_, names):
        ignored = {"data", "docs", "tests", "__pycache__", ".venv", "venv", "wandb"}
        return {name for name in names if name in ignored or name.endswith(".pyc")}

    shutil.copytree(project_root / "AI", work_dir / "AI", ignore=ignore_ai_files)


def trigger_kernel(spec: dict, dry_run: bool) -> bool:
    """로컬 메타데이터 기반 kaggle kernels push로 학습을 시작한다."""
    full_slug = f"{KAGGLE_USERNAME}/{spec['slug']}"
    print(f"\n>> [{spec['name']}] Kaggle 커널 push 준비")
    print(f"   커널: {full_slug}")
    print(f"   데이터셋: {KAGGLE_DATASET_ID}")

    with tempfile.TemporaryDirectory(prefix=f"kaggle_{spec['slug']}_") as tmp:
        work_dir = Path(tmp)
        write_kernel_files(work_dir, spec)

        if dry_run:
            print(f"   dry-run: {work_dir}에 커널 파일 생성 확인")
            return True

        result = subprocess.run(
            ["kaggle", "kernels", "push", "-p", str(work_dir)],
            capture_output=True,
            text=True,
        )
        if result.stdout.strip():
            print(result.stdout.strip())
        if result.returncode != 0:
            print(f"   [오류] {spec['name']} 커널 push 실패")
            if result.stderr.strip():
                print(result.stderr.strip())
            return False

    print(f"   [{spec['name']}] 커널 push 완료")
    print(f"   확인: https://www.kaggle.com/code/{full_slug}")
    return True


def wait_for_kernel(spec: dict, timeout_hours: int, poll_minutes: int) -> bool:
    """Kaggle 커널 완료까지 상태를 polling한다."""
    full_slug = f"{KAGGLE_USERNAME}/{spec['slug']}"
    max_checks = max(1, int((timeout_hours * 60) / poll_minutes))

    print(f"\n>> [{spec['name']}] 완료 대기 중")
    for check_count in range(1, max_checks + 1):
        result = subprocess.run(
            ["kaggle", "kernels", "status", full_slug],
            capture_output=True,
            text=True,
        )
        output = (result.stdout or result.stderr).strip()
        lower_output = output.lower()

        if "complete" in lower_output:
            print(f"   [{spec['name']}] 학습 완료")
            return True
        if "error" in lower_output or "failed" in lower_output:
            print(f"   [{spec['name']}] 학습 실패")
            print(output)
            return False

        elapsed = check_count * poll_minutes
        print(f"   [{spec['name']}] 상태: {output or '응답 없음'} ({elapsed}분 경과)")
        time.sleep(poll_minutes * 60)

    print(f"   [{spec['name']}] 타임아웃 ({timeout_hours}시간 초과)")
    return False


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Kaggle 커널 주간 학습 트리거")
    parser.add_argument(
        "--start-from",
        type=str,
        default=None,
        help="특정 모델부터 시작 (PatchTST/Transformer/iTransformer/TCN)",
    )
    parser.add_argument("--dry-run", action="store_true", help="Kaggle push 없이 커널 파일 생성만 확인")
    parser.add_argument("--no-wait", action="store_true", help="커널 push 후 완료 대기를 생략")
    parser.add_argument("--timeout-hours", type=int, default=12)
    parser.add_argument("--poll-minutes", type=int, default=5)
    return parser.parse_args()


def select_models(start_from: str | None) -> list[dict]:
    if not start_from:
        return MODEL_SPECS

    normalized = start_from.strip().lower()
    names = [spec["name"].lower() for spec in MODEL_SPECS]
    if normalized not in names:
        print(f">> [경고] 모델명 '{start_from}' 없음. 처음부터 시작합니다.")
        return MODEL_SPECS

    start_idx = names.index(normalized)
    print(f">> [{MODEL_SPECS[start_idx]['name']}]부터 시작합니다.")
    return MODEL_SPECS[start_idx:]


def main() -> int:
    args = parse_args()
    models_to_run = select_models(args.start_from)

    print("=" * 50)
    print(">> trigger_training.py 시작")
    print(">> 실행 방식: kaggle kernels push")
    print(f">> 학습 대상: {[spec['name'] for spec in models_to_run]}")
    print("=" * 50)

    if not args.dry_run and not ensure_kaggle_cli():
        return 1

    failed = []
    for spec in models_to_run:
        if not trigger_kernel(spec, dry_run=args.dry_run):
            failed.append(spec["name"])
            print(f"\n>> [{spec['name']}] 실패. 다음 모델로 넘어갑니다.")
            continue

        if not args.dry_run and not args.no_wait:
            if not wait_for_kernel(spec, args.timeout_hours, args.poll_minutes):
                failed.append(spec["name"])
                print(f"\n>> [{spec['name']}] 실패. 다음 모델로 넘어갑니다.")

        if not args.dry_run:
            time.sleep(60)

    print("\n" + "=" * 50)
    if failed:
        print(f">> 실패한 모델: {failed}")
        print(f">> 재시도: python AI/scripts/trigger_training.py --start-from {failed[0]}")
        return 1

    print(">> 전체 학습 트리거 완료")
    print("=" * 50)
    return 0


if __name__ == "__main__":
    sys.exit(main())
