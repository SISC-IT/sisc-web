# AI/scripts/download_weights.py
"""
[목적]
  Kaggle 노트북 Output에서 학습된 가중치 파일만 선택적 다운로드
  kaggle kernels output CLI 대신 Kaggle API 직접 사용 (안정적 다운로드)
"""
import os
import shutil
import sys
import requests
import json

# ─────────────────────────────────────────────────────────────────────────────
# 경로 설정
# ─────────────────────────────────────────────────────────────────────────────
current_dir  = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../.."))

KAGGLE_USERNAME = os.environ.get("KAGGLE_USERNAME", "jihyeongkimm")
KAGGLE_KEY      = os.environ.get("KAGGLE_KEY", "")

# kaggle.json에서 읽기 (로컬 환경)
if not KAGGLE_KEY:
    kaggle_json = os.path.expanduser("~/.kaggle/kaggle.json")
    if os.path.exists(kaggle_json):
        with open(kaggle_json) as f:
            creds = json.load(f)
            KAGGLE_USERNAME = creds.get("username", KAGGLE_USERNAME)
            KAGGLE_KEY      = creds.get("key", "")

MODELS = [
    {
        "name"      : "PatchTST",
        "slug"      : f"{KAGGLE_USERNAME}/patchtst-training",
        "dst_dir"   : os.path.join(project_root, "AI/data/weights/patchtst"),
        "keep_files": ["patchtst_model.pt", "patchtst_scaler.pkl"],
    },
    {
        "name"      : "Transformer",
        "slug"      : f"{KAGGLE_USERNAME}/transformer-training",
        "dst_dir"   : os.path.join(project_root, "AI/data/weights/transformer/prod"),
        "keep_files": [
            {"source": "multi_horizon_model.keras", "dest": "multi_horizon_model_prod.keras"},
            {"source": "multi_horizon_scaler.pkl", "dest": "multi_horizon_scaler_prod.pkl"},
        ],
    },
    {
        "name"      : "iTransformer",
        "slug"      : f"{KAGGLE_USERNAME}/itransformer-training",
        "dst_dir"   : os.path.join(project_root, "AI/data/weights/itransformer"),
        "keep_files": ["multi_horizon_model.keras", "multi_horizon_scaler.pkl", "metadata.json"],
    },
    {
        "name"      : "TCN",
        "slug"      : f"{KAGGLE_USERNAME}/tcn-training",
        "dst_dir"   : os.path.join(project_root, "AI/data/weights/tcn"),
        "keep_files": ["model.pt", "scaler.pkl", "metadata.json"],
    },
]


def normalize_file_spec(file_spec) -> tuple[str, str]:
    """Kaggle output 파일명과 로컬 저장 파일명을 분리한다."""
    if isinstance(file_spec, dict):
        source = file_spec["source"]
        return source, file_spec.get("dest", source)
    return file_spec, file_spec


def list_output_files(slug: str) -> list:
    """Kaggle API로 노트북 output 파일 목록 조회"""
    owner, kernel = slug.split("/")
    url = f"https://www.kaggle.com/api/v1/kernels/output/{owner}/{kernel}?page_token=START"
    try:
        resp = requests.get(url, auth=(KAGGLE_USERNAME, KAGGLE_KEY), timeout=60)
    except requests.exceptions.RequestException as e:
        print(f"   [오류] 파일 목록 조회 요청 실패: {e}")
        return []
    if resp.status_code != 200:
        print(f"   [오류] 파일 목록 조회 실패: {resp.status_code}")
        return []
    data = resp.json()
    files = data.get("files", [])
    return files


def download_file(slug: str, file_name: str, dst_path: str) -> bool:
    """Kaggle API로 특정 파일 다운로드 (스트리밍)"""
    owner, kernel = slug.split("/")
    url = f"https://www.kaggle.com/api/v1/kernels/output/{owner}/{kernel}?fileName={file_name}"

    try:
        resp_ctx = requests.get(
            url,
            auth=(KAGGLE_USERNAME, KAGGLE_KEY),
            stream=True,
            timeout=(10, 300),
        )
    except requests.exceptions.RequestException as e:
        print(f"   [오류] 다운로드 요청 실패: {file_name} ({e})")
        return False

    with resp_ctx as resp:
        if resp.status_code != 200:
            print(f"   [오류] 다운로드 실패: {file_name} ({resp.status_code})")
            return False
        
        total = int(resp.headers.get("content-length", 0))
        downloaded = 0
        with open(dst_path, "wb") as f:
            for chunk in resp.iter_content(chunk_size=8192):
                f.write(chunk)
                downloaded += len(chunk)
        
        actual = os.path.getsize(dst_path)
        if total > 0 and actual != total:
            print(f"   [경고] {file_name} 크기 불일치: 예상 {total}, 실제 {actual}")
            return False
        return True


def download_weights(model: dict) -> bool:
    print(f"\n>> [{model['name']}] 가중치 다운로드 중...")
    print(f"   소스: {model['slug']}")
    print(f"   저장: {model['dst_dir']}")
    targets = [normalize_file_spec(spec) for spec in model["keep_files"]]
    print(f"   대상: {[f'{source} -> {dest}' if source != dest else source for source, dest in targets]}")

    os.makedirs(model['dst_dir'], exist_ok=True)

    # 파일 목록 조회
    all_files = list_output_files(model['slug'])
    if not all_files:
        print(f"   [{model['name']}] 파일 목록 없음 (CLI 폴백)")
        # CLI 폴백
        import subprocess, tempfile
        with tempfile.TemporaryDirectory() as tmp_dir:
            result = subprocess.run(
                ["kaggle", "kernels", "output", model['slug'], "-p", tmp_dir, "-o"],
                capture_output=True, text=True
            )
            if result.returncode != 0:
                print(f"   [CLI 오류] {result.stderr.strip()}")
            if result.stdout.strip():
                print(f"   [CLI 출력] {result.stdout.strip()}")
            file_map = {}
            for root, _dirs, files in os.walk(tmp_dir):
                for f in files:
                    if f not in file_map:
                        file_map[f] = os.path.join(root, f)
            copied = []
            for file_spec in model['keep_files']:
                source_name, dest_name = normalize_file_spec(file_spec)
                if source_name in file_map:
                    src = file_map[source_name]
                    if os.path.getsize(src) < 100:
                        continue
                    dst = os.path.join(model['dst_dir'], dest_name)
                    shutil.copy2(src, dst)
                    size = os.path.getsize(dst) / (1024 * 1024)
                    copied.append(f"{dest_name} ({size:.1f} MB)")
            expected_count = len(model["keep_files"])
            if len(copied) == expected_count:
                print(f"   [{model['name']}] 다운로드 완료!")
                for f in copied:
                    print(f"   - {f}")
                return True
            missing_sources = [
                normalize_file_spec(spec)[0]
                for spec in model["keep_files"]
                if normalize_file_spec(spec)[1] not in {item.split(" (", 1)[0] for item in copied}
            ]
            print(f"   [{model['name']}] 실패: 누락 파일 -> {missing_sources}")
            return False

    # 파일명 → URL 매핑
    file_map = {}
    for f in all_files:
        name = f.get("name", "").split("/")[-1]  # 경로에서 파일명만 추출
        if name and name not in file_map:
            file_map[name] = f.get("name", name)

    copied = []
    missing = []
    for file_spec in model['keep_files']:
        source_name, dest_name = normalize_file_spec(file_spec)
        if source_name in file_map:
            dst = os.path.join(model['dst_dir'], dest_name)
            print(f"   다운로드 중: {source_name}...")
            success = download_file(model['slug'], file_map[source_name], dst)
            if success:
                size = os.path.getsize(dst) / (1024 * 1024)
                copied.append(f"{dest_name} ({size:.1f} MB)")
            else:
                missing.append(source_name)
        else:
            missing.append(source_name)

    if missing:
        print(f"   [{model['name']}] 실패: 파일 없음 -> {missing}")
        return False
    if not copied:
        print(f"   [{model['name']}] 실패: 가중치 파일 없음")
        return False

    print(f"   [{model['name']}] 다운로드 완료!")
    for f in copied:
        print(f"   - {f}")
    return True


print("=" * 50)
print(">> download_weights.py 시작")
print("=" * 50)

if not KAGGLE_KEY:
    print(">> Kaggle API 키 없음. kaggle.json 확인 필요")
    sys.exit(1)

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
    print(">> 전체 가중치 다운로드 완료!")
print("=" * 50)
