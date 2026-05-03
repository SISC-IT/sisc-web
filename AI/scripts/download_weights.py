# AI/scripts/download_weights.py
"""
Kaggle 노트북 Output에서 학습 산출물을 내려받아 운영 artifact 루트에 반영한다.

다운로드는 임시 staging 디렉터리에 먼저 수행하고, 모든 모델의 필수 파일 검증이
끝난 뒤에만 최종 디렉터리로 승격한다. 검증 실패 시 기존 운영 weights는 건드리지
않는다.
"""
from __future__ import annotations

import argparse
import json
import os
import shutil
import subprocess
import sys
import tempfile
from datetime import datetime
from pathlib import Path
from typing import Any

import requests


PROJECT_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_ARTIFACT_ROOT = PROJECT_ROOT / "AI/data/weights"
MIN_ARTIFACT_BYTES = int(os.environ.get("MIN_ARTIFACT_BYTES", "100"))


def resolve_artifact_root(raw_root: str | None = None) -> Path:
    """cron과 운영 wrapper가 공유할 artifact 루트 경로를 결정한다."""
    selected = (
        raw_root
        or os.environ.get("AI_MODEL_WEIGHTS_DIR")
        or os.environ.get("WEIGHTS_DIR")
        or str(DEFAULT_ARTIFACT_ROOT)
    )
    path = Path(selected).expanduser()
    if not path.is_absolute():
        path = PROJECT_ROOT / path
    return path.resolve()


def warn_artifact_root_env(artifact_root: Path) -> None:
    """cron에서 경로 불일치가 생기지 않도록 명시 환경변수를 안내한다."""
    if os.environ.get("AI_MODEL_WEIGHTS_DIR"):
        return
    print(
        "[경고] AI_MODEL_WEIGHTS_DIR가 설정되지 않았습니다. "
        f"현재 artifact root={artifact_root}"
    )
    print("       서버 cron에서는 AI_MODEL_WEIGHTS_DIR=/mnt/storage/ai-artifacts 처럼 명시하세요.")
    if os.environ.get("REQUIRE_AI_MODEL_WEIGHTS_DIR", "").lower() in {"1", "true", "yes", "y"}:
        raise RuntimeError("REQUIRE_AI_MODEL_WEIGHTS_DIR가 켜져 있어 실행을 중단합니다.")


def load_kaggle_credentials() -> tuple[str, str]:
    """환경변수 또는 ~/.kaggle/kaggle.json에서 Kaggle 인증 정보를 읽는다."""
    username = os.environ.get("KAGGLE_USERNAME", "jihyeongkimm")
    key = os.environ.get("KAGGLE_KEY", "")
    if key:
        return username, key

    kaggle_json = Path.home() / ".kaggle/kaggle.json"
    if kaggle_json.exists():
        with kaggle_json.open("r", encoding="utf-8") as handle:
            creds = json.load(handle)
        username = creds.get("username", username)
        key = creds.get("key", "")
    return username, key


def build_model_specs(artifact_root: Path, kaggle_username: str) -> list[dict[str, Any]]:
    """모델별 Kaggle output 파일명과 운영 저장 파일명을 정의한다."""
    return [
        {
            "name": "PatchTST",
            "slug": f"{kaggle_username}/patchtst-training",
            "relative_dir": "patchtst",
            "dst_dir": str(artifact_root / "patchtst"),
            "keep_files": ["patchtst_model.pt", "patchtst_scaler.pkl", "metadata.json"],
        },
        {
            "name": "Transformer",
            "slug": f"{kaggle_username}/transformer-training",
            "relative_dir": "transformer/prod",
            "dst_dir": str(artifact_root / "transformer/prod"),
            "keep_files": [
                {"source": "multi_horizon_model.keras", "dest": "multi_horizon_model_prod.keras"},
                {"source": "multi_horizon_scaler.pkl", "dest": "multi_horizon_scaler_prod.pkl"},
            ],
        },
        {
            "name": "iTransformer",
            "slug": f"{kaggle_username}/itransformer-training",
            "relative_dir": "itransformer",
            "dst_dir": str(artifact_root / "itransformer"),
            "keep_files": ["multi_horizon_model.keras", "multi_horizon_scaler.pkl", "metadata.json"],
        },
        {
            "name": "TCN",
            "slug": f"{kaggle_username}/tcn-training",
            "relative_dir": "tcn",
            "dst_dir": str(artifact_root / "tcn"),
            "keep_files": ["model.pt", "scaler.pkl", "metadata.json"],
        },
    ]


def normalize_file_spec(file_spec: str | dict[str, str]) -> tuple[str, str]:
    """Kaggle output 파일명과 로컬 저장 파일명을 분리한다."""
    if isinstance(file_spec, dict):
        source = file_spec["source"]
        return source, file_spec.get("dest", source)
    return file_spec, file_spec


def model_targets(model: dict[str, Any]) -> list[tuple[str, str]]:
    return [normalize_file_spec(spec) for spec in model["keep_files"]]


def with_staging_destination(model: dict[str, Any], staging_root: Path) -> dict[str, Any]:
    staged = dict(model)
    staged["dst_dir"] = str(staging_root / model["relative_dir"])
    return staged


def validate_model_artifacts(model: dict[str, Any], *, min_bytes: int = MIN_ARTIFACT_BYTES) -> list[str]:
    """모델별 필수 artifact가 모두 있고 비정상적으로 작지 않은지 검증한다."""
    errors: list[str] = []
    dst_dir = Path(model["dst_dir"])
    for _source_name, dest_name in model_targets(model):
        path = dst_dir / dest_name
        if not path.exists():
            errors.append(f"{model['name']}: 필수 파일 없음: {path}")
            continue
        if not path.is_file():
            errors.append(f"{model['name']}: 파일이 아님: {path}")
            continue
        size = path.stat().st_size
        if size < min_bytes:
            errors.append(
                f"{model['name']}: 파일 크기가 너무 작음: {path} ({size} bytes, min={min_bytes})"
            )
    return errors


def validate_all_model_artifacts(models: list[dict[str, Any]]) -> list[str]:
    errors: list[str] = []
    for model in models:
        errors.extend(validate_model_artifacts(model))
    return errors


def list_output_files(slug: str, kaggle_username: str, kaggle_key: str) -> list[dict[str, Any]]:
    """Kaggle API로 노트북 output 파일 목록을 조회한다."""
    owner, kernel = slug.split("/")
    url = f"https://www.kaggle.com/api/v1/kernels/output/{owner}/{kernel}?page_token=START"
    try:
        resp = requests.get(url, auth=(kaggle_username, kaggle_key), timeout=60)
    except requests.exceptions.RequestException as exc:
        print(f"   [오류] 파일 목록 조회 요청 실패: {exc}")
        return []
    if resp.status_code != 200:
        print(f"   [오류] 파일 목록 조회 실패: {resp.status_code}")
        return []
    return resp.json().get("files", [])


def download_file(
    slug: str,
    file_name: str,
    dst_path: Path,
    kaggle_username: str,
    kaggle_key: str,
) -> bool:
    """Kaggle API로 특정 파일을 스트리밍 다운로드한다."""
    owner, kernel = slug.split("/")
    url = f"https://www.kaggle.com/api/v1/kernels/output/{owner}/{kernel}?fileName={file_name}"

    try:
        resp_ctx = requests.get(
            url,
            auth=(kaggle_username, kaggle_key),
            stream=True,
            timeout=(10, 300),
        )
    except requests.exceptions.RequestException as exc:
        print(f"   [오류] 다운로드 요청 실패: {file_name} ({exc})")
        return False

    dst_path.parent.mkdir(parents=True, exist_ok=True)
    with resp_ctx as resp:
        if resp.status_code != 200:
            print(f"   [오류] 다운로드 실패: {file_name} ({resp.status_code})")
            return False

        total = int(resp.headers.get("content-length", 0))
        with dst_path.open("wb") as handle:
            for chunk in resp.iter_content(chunk_size=8192):
                if chunk:
                    handle.write(chunk)

        actual = dst_path.stat().st_size
        if total > 0 and actual != total:
            print(f"   [오류] {file_name} 크기 불일치: 예상 {total}, 실제 {actual}")
            return False
        return True


def _copy_cli_outputs(model: dict[str, Any], tmp_dir: Path) -> bool:
    file_map: dict[str, Path] = {}
    for root, _dirs, files in os.walk(tmp_dir):
        for file_name in files:
            file_map.setdefault(file_name, Path(root) / file_name)

    dst_dir = Path(model["dst_dir"])
    dst_dir.mkdir(parents=True, exist_ok=True)
    missing: list[str] = []
    for source_name, dest_name in model_targets(model):
        source_path = file_map.get(source_name)
        if source_path is None:
            missing.append(source_name)
            continue
        shutil.copy2(source_path, dst_dir / dest_name)

    if missing:
        print(f"   [{model['name']}] 실패: 누락 파일 -> {missing}")
        return False
    return True


def download_with_cli_fallback(model: dict[str, Any]) -> bool:
    """API 목록 조회가 실패했을 때 Kaggle CLI output 명령으로 대체한다."""
    print(f"   [{model['name']}] 파일 목록 없음 또는 조회 실패 (CLI 폴백)")
    with tempfile.TemporaryDirectory(prefix=f"kaggle_output_{model['name'].lower()}_") as tmp:
        tmp_dir = Path(tmp)
        try:
            result = subprocess.run(
                ["kaggle", "kernels", "output", model["slug"], "-p", str(tmp_dir), "-o"],
                capture_output=True,
                text=True,
                check=False,
            )
        except FileNotFoundError:
            print("   [CLI 오류] kaggle CLI가 PATH에 없습니다.")
            return False

        if result.stdout.strip():
            print(f"   [CLI 출력] {result.stdout.strip()}")
        if result.returncode != 0:
            print(f"   [CLI 오류] {result.stderr.strip()}")
            return False
        return _copy_cli_outputs(model, tmp_dir)


def download_weights(model: dict[str, Any], kaggle_username: str, kaggle_key: str) -> bool:
    print(f"\n>> [{model['name']}] 가중치 다운로드 중...")
    print(f"   소스: {model['slug']}")
    print(f"   저장: {model['dst_dir']}")
    targets = [
        f"{source} -> {dest}" if source != dest else source
        for source, dest in model_targets(model)
    ]
    print(f"   필수 파일: {targets}")

    all_files = list_output_files(model["slug"], kaggle_username, kaggle_key)
    if not all_files:
        success = download_with_cli_fallback(model)
        if not success:
            return False
    else:
        file_map: dict[str, str] = {}
        for output_file in all_files:
            output_name = output_file.get("name", "")
            name = output_name.split("/")[-1]
            if name:
                file_map.setdefault(name, output_name or name)

        missing: list[str] = []
        for source_name, dest_name in model_targets(model):
            if source_name not in file_map:
                missing.append(source_name)
                continue
            dst_path = Path(model["dst_dir"]) / dest_name
            print(f"   다운로드 중: {source_name}...")
            if not download_file(model["slug"], file_map[source_name], dst_path, kaggle_username, kaggle_key):
                missing.append(source_name)

        if missing:
            print(f"   [{model['name']}] 실패: 파일 없음 또는 다운로드 실패 -> {missing}")
            return False

    errors = validate_model_artifacts(model)
    if errors:
        for error in errors:
            print(f"   [오류] {error}")
        return False

    print(f"   [{model['name']}] staging 다운로드 검증 완료")
    return True


def promote_staged_artifacts(staged_models: list[dict[str, Any]], artifact_root: Path) -> None:
    """검증된 staging artifact를 최종 경로로 승격하고 실패 시 원복한다."""
    run_id = datetime.now().strftime("%Y%m%d_%H%M%S")
    backup_root = artifact_root / ".backup" / f"download_{run_id}"
    promoted: list[tuple[Path, Path | None]] = []

    try:
        for model in staged_models:
            relative_dir = Path(model["relative_dir"])
            staged_dir = Path(model["dst_dir"])
            final_dir = artifact_root / relative_dir
            backup_dir = backup_root / relative_dir

            if final_dir.exists():
                backup_dir.parent.mkdir(parents=True, exist_ok=True)
                shutil.move(str(final_dir), str(backup_dir))
                active_backup: Path | None = backup_dir
            else:
                final_dir.parent.mkdir(parents=True, exist_ok=True)
                active_backup = None

            shutil.copytree(staged_dir, final_dir)
            promoted.append((final_dir, active_backup))

        if backup_root.exists():
            shutil.rmtree(backup_root)
    except Exception as original_exc:
        rollback_errors: list[str] = []
        for final_dir, backup_dir in reversed(promoted):
            try:
                if final_dir.exists():
                    shutil.rmtree(final_dir)
                if backup_dir is not None and backup_dir.exists():
                    shutil.move(str(backup_dir), str(final_dir))
            except Exception as rollback_exc:
                rollback_errors.append(
                    f"{final_dir}: {type(rollback_exc).__name__}: {rollback_exc}"
                )
        if rollback_errors:
            raise RuntimeError(
                "artifact 승격 실패 후 롤백에도 실패했습니다: "
                + "; ".join(rollback_errors)
            ) from original_exc
        raise


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Kaggle 학습 산출물 다운로드")
    parser.add_argument(
        "--artifact-root",
        default=None,
        help="저장할 artifact 루트. 기본값은 AI_MODEL_WEIGHTS_DIR, WEIGHTS_DIR, AI/data/weights 순서입니다.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    artifact_root = resolve_artifact_root(args.artifact_root)

    print("=" * 50)
    print(">> download_weights.py 시작")
    print(f">> artifact root: {artifact_root}")
    print("=" * 50)

    try:
        warn_artifact_root_env(artifact_root)
    except RuntimeError as exc:
        print(f"[오류] {exc}")
        return 1

    kaggle_username, kaggle_key = load_kaggle_credentials()
    if not kaggle_key:
        print(">> Kaggle API 키 없음. KAGGLE_KEY 또는 ~/.kaggle/kaggle.json 확인 필요")
        return 1

    models = build_model_specs(artifact_root, kaggle_username)
    staging_parent = artifact_root / ".staging"
    staging_parent.mkdir(parents=True, exist_ok=True)

    with tempfile.TemporaryDirectory(prefix="kaggle_download_", dir=staging_parent) as tmp:
        staging_root = Path(tmp)
        staged_models = [with_staging_destination(model, staging_root) for model in models]

        failed: list[str] = []
        for model in staged_models:
            if not download_weights(model, kaggle_username, kaggle_key):
                failed.append(model["name"])

        validation_errors = validate_all_model_artifacts(staged_models)
        if validation_errors:
            print("\n>> staging 검증 실패")
            for error in validation_errors:
                print(f"   - {error}")
            return 1

        if failed:
            print(f"\n>> 실패한 모델: {failed}")
            print(">> 최종 weights 디렉터리는 변경하지 않았습니다.")
            return 1

        promote_staged_artifacts(staged_models, artifact_root)

    final_errors = validate_all_model_artifacts(models)
    if final_errors:
        print("\n>> 최종 artifact 검증 실패")
        for error in final_errors:
            print(f"   - {error}")
        return 1

    print("\n" + "=" * 50)
    print(">> 전체 가중치 다운로드 및 검증 완료")
    print("=" * 50)
    return 0


if __name__ == "__main__":
    sys.exit(main())
