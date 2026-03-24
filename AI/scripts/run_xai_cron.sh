#!/bin/bash
set -euo pipefail

IMAGE="ghcr.io/sisc-it/sisc-web-xai:latest"
IMAGE_REPO="ghcr.io/sisc-it/sisc-web-xai"
CONTAINER_NAME="quantbot-xai"

# GHCR 인증 정보는 서버 환경 변수나 cron 실행 계정의 profile에 준비되어 있어야 합니다.
# 예시:
#   export GHCR_READ_USER=...
#   export GHCR_READ_TOKEN=...

if [[ -z "${GHCR_READ_USER:-}" || -z "${GHCR_READ_TOKEN:-}" ]]; then
  echo "[ERROR] GHCR_READ_USER / GHCR_READ_TOKEN 환경 변수가 필요합니다."
  exit 1
fi

echo "$GHCR_READ_TOKEN" | docker login ghcr.io -u "$GHCR_READ_USER" --password-stdin

if docker pull "$IMAGE"; then
  echo "[INFO] 최신 XAI 이미지를 성공적으로 가져왔습니다."
else
  echo "[WARN] docker pull 에 실패했습니다. 로컬에 남아 있는 latest 이미지로 계속 진행합니다."
  if ! docker image inspect "$IMAGE" >/dev/null 2>&1; then
    echo "[ERROR] 사용할 수 있는 로컬 XAI 이미지가 없습니다."
    exit 1
  fi
fi

# 이미 실행 중이면 이번 실행은 건너뜁니다.
if docker ps --format '{{.Names}}' | grep -qx "$CONTAINER_NAME"; then
  echo "[INFO] XAI 작업이 이미 실행 중이므로 이번 실행은 건너뜁니다."
  exit 0
fi

# 이전 실행이 비정상 종료되어 남은 stopped 컨테이너만 정리합니다.
if docker ps -a --format '{{.Names}} {{.State}}' | grep -Eq "^${CONTAINER_NAME} (exited|created|dead)$"; then
  docker rm "$CONTAINER_NAME" >/dev/null 2>&1 || true
fi

# 필요하면 아래 docker run 에 다음 옵션을 추가해서 사용하세요.
#   --env-file /path/to/your.env
#   --network sisc-net
#   -v /host/path:/container/path
docker run --rm \
  --name "$CONTAINER_NAME" \
  "$IMAGE"

# 실행이 끝난 뒤에는 dangling 이미지만 정리합니다.
CURRENT_IMAGE_ID="$(docker image inspect "$IMAGE" --format '{{.Id}}')"

while read -r image_ref image_id; do
  if [[ -z "$image_ref" || -z "$image_id" ]]; then
    continue
  fi

  if [[ "$image_id" != "$CURRENT_IMAGE_ID" ]]; then
    docker rmi -f "$image_id" >/dev/null 2>&1 || true
  fi
done < <(docker image ls --no-trunc --format '{{.Repository}}:{{.Tag}} {{.ID}}' "$IMAGE_REPO")

docker image prune -f >/dev/null 2>&1 || true
