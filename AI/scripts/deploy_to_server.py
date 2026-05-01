# AI/scripts/deploy_to_server.py
"""
검증된 로컬 artifact를 운영 서버로 배포한다.

배포 전 모든 모델의 필수 파일을 먼저 검증한다. 검증이 실패하면 SSH 연결과 원격
전송을 시작하지 않으므로, 누락 파일 때문에 일부 모델만 배포되는 상황을 막는다.
"""
from __future__ import annotations

import argparse
import io
import os
import shlex
import sys
from datetime import datetime
from pathlib import Path
from typing import Any


PROJECT_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_ARTIFACT_ROOT = PROJECT_ROOT / "AI/data/weights"
MIN_ARTIFACT_BYTES = int(os.environ.get("MIN_ARTIFACT_BYTES", "100"))


def resolve_artifact_root(raw_root: str | None = None) -> Path:
    """download_weights.py와 같은 규칙으로 로컬 artifact 루트를 결정한다."""
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
    """cron에서 다운로드 경로와 배포 경로가 달라지는 실수를 줄인다."""
    if os.environ.get("AI_MODEL_WEIGHTS_DIR"):
        return
    print(
        "[경고] AI_MODEL_WEIGHTS_DIR가 설정되지 않았습니다. "
        f"현재 artifact root={artifact_root}"
    )
    print("       서버 cron에서는 download/deploy가 같은 AI_MODEL_WEIGHTS_DIR를 쓰도록 명시하세요.")
    if os.environ.get("REQUIRE_AI_MODEL_WEIGHTS_DIR", "").lower() in {"1", "true", "yes", "y"}:
        raise RuntimeError("REQUIRE_AI_MODEL_WEIGHTS_DIR가 켜져 있어 실행을 중단합니다.")


def build_model_specs(artifact_root: Path, server_weights_path: str) -> list[dict[str, Any]]:
    """운영 wrapper가 기대하는 모델별 artifact 파일 계약을 정의한다."""
    return [
        {
            "name": "PatchTST",
            "relative_dir": "patchtst",
            "local_dir": str(artifact_root / "patchtst"),
            "remote_dir": f"{server_weights_path}/patchtst",
            "files": ["patchtst_model.pt", "patchtst_scaler.pkl", "metadata.json"],
        },
        {
            "name": "Transformer",
            "relative_dir": "transformer/prod",
            "local_dir": str(artifact_root / "transformer/prod"),
            "remote_dir": f"{server_weights_path}/transformer/prod",
            "files": ["multi_horizon_model_prod.keras", "multi_horizon_scaler_prod.pkl"],
        },
        {
            "name": "iTransformer",
            "relative_dir": "itransformer",
            "local_dir": str(artifact_root / "itransformer"),
            "remote_dir": f"{server_weights_path}/itransformer",
            "files": ["multi_horizon_model.keras", "multi_horizon_scaler.pkl", "metadata.json"],
        },
        {
            "name": "TCN",
            "relative_dir": "tcn",
            "local_dir": str(artifact_root / "tcn"),
            "remote_dir": f"{server_weights_path}/tcn",
            "files": ["model.pt", "scaler.pkl", "metadata.json"],
        },
    ]


def validate_model_artifacts(model: dict[str, Any], *, min_bytes: int = MIN_ARTIFACT_BYTES) -> list[str]:
    """로컬 필수 파일 존재와 최소 크기를 검증한다."""
    errors: list[str] = []
    local_dir = Path(model["local_dir"])
    for file_name in model["files"]:
        path = local_dir / file_name
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


def create_ssh_client(ssh_host: str, ssh_user: str, ssh_key_str: str, ssh_port: int):
    """SSH 연결 생성. validate-only 경로에서는 paramiko를 import하지 않는다."""
    import paramiko

    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    for key_class in [paramiko.Ed25519Key, paramiko.RSAKey, paramiko.ECDSAKey]:
        try:
            private_key = key_class.from_private_key(io.StringIO(ssh_key_str))
            break
        except Exception:
            continue
    else:
        raise ValueError("SSH 키 타입을 인식할 수 없습니다.")

    ssh.connect(
        hostname=ssh_host,
        port=ssh_port,
        username=ssh_user,
        pkey=private_key,
        timeout=30,
    )
    return ssh


def run_remote_checked(ssh, command: str) -> None:
    """원격 명령 실패를 즉시 예외로 올린다."""
    _stdin, stdout, stderr = ssh.exec_command(command)
    exit_status = stdout.channel.recv_exit_status()
    if exit_status != 0:
        error_text = stderr.read().decode("utf-8", errors="replace").strip()
        raise RuntimeError(f"원격 명령 실패({exit_status}): {error_text or command}")


def deploy_model(ssh, scp, model: dict[str, Any], run_id: str) -> None:
    """모델 하나를 원격 staging 디렉터리에 올린 뒤 모델 디렉터리 단위로 교체한다."""
    print(f"\n>> [{model['name']}] 배포 중...")

    remote_dir = model["remote_dir"].rstrip("/")
    remote_parent = str(Path(remote_dir).parent).replace("\\", "/")
    remote_name = Path(remote_dir).name
    stage_dir = f"{remote_parent}/.{remote_name}.staging_{run_id}"
    backup_dir = f"{remote_parent}/.{remote_name}.backup_{run_id}"

    run_remote_checked(
        ssh,
        f"rm -rf {shlex.quote(stage_dir)} {shlex.quote(backup_dir)} && mkdir -p {shlex.quote(stage_dir)}",
    )

    for file_name in model["files"]:
        local_path = Path(model["local_dir"]) / file_name
        remote_path = f"{stage_dir}/{file_name}"
        size = local_path.stat().st_size / (1024 * 1024)
        print(f"   전송 중: {file_name} ({size:.1f} MB)...")
        scp.put(str(local_path), remote_path)

    verify_commands = [
        f"test -s {shlex.quote(f'{stage_dir}/{file_name}')}"
        for file_name in model["files"]
    ]
    run_remote_checked(ssh, " && ".join(verify_commands))

    switch_command = f"""
set -e
mkdir -p {shlex.quote(remote_parent)}
if [ -d {shlex.quote(remote_dir)} ]; then
  mv {shlex.quote(remote_dir)} {shlex.quote(backup_dir)}
fi
if mv {shlex.quote(stage_dir)} {shlex.quote(remote_dir)}; then
  rm -rf {shlex.quote(backup_dir)}
else
  if [ -d {shlex.quote(backup_dir)} ]; then
    mv {shlex.quote(backup_dir)} {shlex.quote(remote_dir)}
  fi
  exit 1
fi
"""
    run_remote_checked(ssh, switch_command)
    print(f"   [{model['name']}] 배포 완료: {remote_dir}")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="검증된 모델 artifact를 서버로 배포")
    parser.add_argument(
        "--artifact-root",
        default=None,
        help="로컬 artifact 루트. 기본값은 AI_MODEL_WEIGHTS_DIR, WEIGHTS_DIR, AI/data/weights 순서입니다.",
    )
    parser.add_argument(
        "--server-weights-path",
        default=os.environ.get("SERVER_WEIGHTS_PATH", "/app/AI/data/weights"),
        help="운영 서버에 배포할 weights 루트 경로",
    )
    parser.add_argument(
        "--validate-only",
        action="store_true",
        help="SSH 연결 없이 로컬 필수 artifact 검증만 수행",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    artifact_root = resolve_artifact_root(args.artifact_root)
    server_weights_path = args.server_weights_path.rstrip("/")
    models = build_model_specs(artifact_root, server_weights_path)

    print("=" * 50)
    print(">> deploy_to_server.py 시작")
    print(f">> local artifact root: {artifact_root}")
    print(f">> server weights path: {server_weights_path}")
    print("=" * 50)

    try:
        warn_artifact_root_env(artifact_root)
    except RuntimeError as exc:
        print(f"[오류] {exc}")
        return 1

    errors = validate_all_model_artifacts(models)
    if errors:
        print(">> 로컬 artifact 검증 실패. SSH 배포를 시작하지 않습니다.")
        for error in errors:
            print(f"   - {error}")
        return 1

    print(">> 로컬 artifact 검증 완료")
    if args.validate_only:
        return 0

    ssh_host = os.environ.get("SSH_HOST")
    ssh_user = os.environ.get("SSH_USER")
    ssh_key_str = os.environ.get("SSH_PRIVATE_KEY")
    ssh_port = int(os.environ.get("SSH_PORT", "22"))
    if not all([ssh_host, ssh_user, ssh_key_str]):
        print("[오류] SSH 접속 정보 없음")
        print("       SSH_HOST, SSH_USER, SSH_PRIVATE_KEY 환경변수를 설정하세요.")
        return 1

    print(f">> 서버: {ssh_user}@{ssh_host}:{ssh_port}")
    run_id = datetime.now().strftime("%Y%m%d_%H%M%S")

    try:
        from scp import SCPClient

        print("\n>> SSH 연결 중...")
        ssh = create_ssh_client(ssh_host, ssh_user, ssh_key_str, ssh_port)
        scp = SCPClient(ssh.get_transport())
        print(">> SSH 연결 성공")

        for model in models:
            deploy_model(ssh, scp, model, run_id)

        scp.close()
        ssh.close()
    except Exception as exc:
        print(f"[오류] 배포 실패: {exc}")
        return 1

    print("\n" + "=" * 50)
    print(">> 전체 배포 완료")
    print("=" * 50)
    return 0


if __name__ == "__main__":
    sys.exit(main())
