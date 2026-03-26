# AI/scripts/download_weights.py
"""
[목적]
  Kaggle 노트북 Output에서 학습된 가중치 파일 다운로드
  → AI/data/weights/ 하위 각 모델 폴더에 저장

[실행 방법]
  python AI/scripts/download_weights.py

[GitHub Actions에서]
  trigger_training.py 완료 후 자동 실행
"""
import os
import subprocess
import sys

# ─────────────────────────────────────────────────────────────────────────────
# 경로 설정
# ─────────────────────────────────────────────────────────────────────────────
current_dir  = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../.."))

KAGGLE_USERNAME = os.environ.get("KAGGLE_USERNAME", "jihyeongkimm")

# ─────────────────────────────────────────────────────────────────────────────
# 모델별 다운로드 설정
# slug    : Kaggle 노트북 슬러그
# dst_dir : 로컬 저장 경로
# ─────────────────────────────────────────────────────────────────────────────
MODELS = [
    {
        "name"   : "PatchTST",
        "slug"   : f"{KAGGLE_USERNAME}/patchtst-training",
        "dst_dir": os.path.join(project_root, "AI/data/weights/PatchTST"),
    },
    {
        "name"   : "Transformer",
        "slug"   : f"{KAGGLE_USERNAME}/transformer-training",
        "dst_dir": os.path.join(project_root, "AI/data/weights/transformer/tests"),
    },
    # 머지 후 추가
    # {
    #     "name"   : "iTransformer",
    #     "slug"   : f"{KAGGLE_USERNAME}/itransformer-training",
    #     "dst_dir": os.path.join(project_root, "AI/data/weights/itransformer"),
    # },
    # {
    #     "name"   : "TCN",
    #     "slug"   : f"{KAGGLE_USERNAME}/tcn-training",
    #     "dst_dir": os.path.join(project_root, "AI/data/weights/tcn"),
    # },
]


def download_weights(model: dict) -> bool:
    """Kaggle 노트북 Output에서 가중치 다운로드"""
    print(f"\n>> [{model['name']}] 가중치 다운로드 중...")
    print(f"   소스: {model['slug']}")
    print(f"   저장: {model['dst_dir']}")

    os.makedirs(model['dst_dir'], exist_ok=True)

    result = subprocess.run(
        [
            "kaggle", "kernels", "output",
            model['slug'],
            "-p", model['dst_dir']
        ],
        capture_output=True,
        text=True
    )

    if result.returncode == 0:
        print(f"   [{model['name']}] 다운로드 완료! ✅")
        # 다운로드된 파일 목록 출력
        for f in os.listdir(model['dst_dir']):
            fpath = os.path.join(model['dst_dir'], f)
            size  = os.path.getsize(fpath) / (1024 * 1024)
            print(f"   - {f} ({size:.1f} MB)")
        return True
    else:
        print(f"   [{model['name']}] 다운로드 실패! ❌")
        print(result.stderr)
        return False


print("=" * 50)
print(">> download_weights.py 시작")
print("=" * 50)

failed = []

for model in MODELS:
    success = download_weights(model)
    if not success:
        failed.append(model['name'])

print("\n" + "=" * 50)
if failed:
    print(f">> 실패한 모델: {failed}")
    sys.exit(1)
else:
    print(">> 전체 가중치 다운로드 완료! ✅")
print("=" * 50)
