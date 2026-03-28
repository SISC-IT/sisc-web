# AI/scripts/deploy_to_server.py
"""
[목적]
  다운로드된 가중치 파일을 운영 서버에 배포
  로컬 AI/data/weights/ → 서버 AI/data/weights/

[실행 방법]
  python AI/scripts/deploy_to_server.py

[GitHub Actions에서]
  download_weights.py 완료 후 자동 실행
  SSH_HOST, SSH_USER, SSH_PRIVATE_KEY 환경변수 필요

[전제 조건]
  - paramiko, scp 설치 필요
    pip install paramiko scp
"""
import os
import sys
import io

import paramiko
from scp import SCPClient

# ─────────────────────────────────────────────────────────────────────────────
# 서버 접속 정보 (GitHub Secrets → 환경변수로 주입)
# ─────────────────────────────────────────────────────────────────────────────
SSH_HOST    = os.environ.get("SSH_HOST")
SSH_USER    = os.environ.get("SSH_USER")
SSH_KEY_STR = os.environ.get("SSH_PRIVATE_KEY")  # 키 내용 (파일 경로 아님)
SSH_PORT    = int(os.environ.get("SSH_PORT", 22))

# ─────────────────────────────────────────────────────────────────────────────
# 경로 설정
# ─────────────────────────────────────────────────────────────────────────────
current_dir  = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../.."))

SERVER_WEIGHTS_PATH = os.environ.get(
    "SERVER_WEIGHTS_PATH",
    "/app/AI/data/weights"
)

# ─────────────────────────────────────────────────────────────────────────────
# 배포할 모델 목록 (4개 전체)
# ─────────────────────────────────────────────────────────────────────────────
MODELS = [
    {
        "name"      : "PatchTST",
        "local_dir" : os.path.join(project_root, "AI/data/weights/PatchTST"),
        "remote_dir": f"{SERVER_WEIGHTS_PATH}/PatchTST",
        "files"     : ["patchtst_model.pt", "patchtst_scaler.pkl"],
    },
    {
        "name"      : "Transformer",
        "local_dir" : os.path.join(project_root, "AI/data/weights/transformer/prod"),
        "remote_dir": f"{SERVER_WEIGHTS_PATH}/transformer/prod",
        "files"     : ["multi_horizon_model_prod.keras", "multi_horizon_scaler_prod.pkl"],
    },
    {
        "name"      : "iTransformer",
        "local_dir" : os.path.join(project_root, "AI/data/weights/itransformer"),
        "remote_dir": f"{SERVER_WEIGHTS_PATH}/itransformer",
        "files"     : ["multi_horizon_model.keras", "multi_horizon_scaler.pkl", "metadata.json"],
    },
    {
        "name"      : "TCN",
        "local_dir" : os.path.join(project_root, "AI/data/weights/tcn"),
        "remote_dir": f"{SERVER_WEIGHTS_PATH}/tcn",
        "files"     : ["model.pt", "scaler.pkl", "metadata.json"],
    },
]


def create_ssh_client() -> paramiko.SSHClient:
    """SSH 연결 생성 (키 문자열로 직접 연결)"""
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    private_key = paramiko.RSAKey.from_private_key(io.StringIO(SSH_KEY_STR))
    ssh.connect(
        hostname = SSH_HOST,
        port     = SSH_PORT,
        username = SSH_USER,
        pkey     = private_key,
        timeout  = 30,
    )
    return ssh


def deploy_model(ssh: paramiko.SSHClient, scp: SCPClient, model: dict) -> bool:
    """모델 가중치를 서버에 배포"""
    print(f"\n>> [{model['name']}] 배포 중...")

    ssh.exec_command(f"mkdir -p {model['remote_dir']}")

    for fname in model['files']:
        local_path = os.path.join(model['local_dir'], fname)

        if not os.path.exists(local_path):
            print(f"   [경고] 파일 없음 (스킵): {local_path}")
            continue

        remote_path = f"{model['remote_dir']}/{fname}"
        size = os.path.getsize(local_path) / (1024 * 1024)

        print(f"   전송 중: {fname} ({size:.1f} MB)...")
        scp.put(local_path, remote_path)
        print(f"   전송 완료: {remote_path}")

    print(f"   [{model['name']}] 배포 완료! ✅")
    return True


# ─────────────────────────────────────────────────────────────────────────────
# 환경변수 검증
# ─────────────────────────────────────────────────────────────────────────────
print("=" * 50)
print(">> deploy_to_server.py 시작")
print("=" * 50)

if not all([SSH_HOST, SSH_USER, SSH_KEY_STR]):
    print("❌ SSH 접속 정보 없음!")
    print("   SSH_HOST, SSH_USER, SSH_PRIVATE_KEY 환경변수를 설정하세요.")
    sys.exit(1)

print(f">> 서버: {SSH_USER}@{SSH_HOST}:{SSH_PORT}")
print(f">> 배포 경로: {SERVER_WEIGHTS_PATH}")

# ─────────────────────────────────────────────────────────────────────────────
# SSH 연결 + 배포
# ─────────────────────────────────────────────────────────────────────────────
try:
    print("\n>> SSH 연결 중...")
    ssh = create_ssh_client()
    scp = SCPClient(ssh.get_transport())
    print(">> SSH 연결 성공! ✅")

    failed = []
    for model in MODELS:
        success = deploy_model(ssh, scp, model)
        if not success:
            failed.append(model['name'])

    scp.close()
    ssh.close()

    print("\n" + "=" * 50)
    if failed:
        print(f">> 실패한 모델: {failed}")
        sys.exit(1)
    else:
        print(">> 전체 배포 완료! ✅")
        print(">> 서버가 새 가중치로 업데이트됐습니다.")
    print("=" * 50)

except Exception as e:
    print(f"❌ 배포 실패: {e}")
    sys.exit(1)
