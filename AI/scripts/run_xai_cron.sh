#!/bin/bash
set -euo pipefail

IMAGE="${XAI_IMAGE:-ghcr.io/sisc-it/sisc-web-xai:latest}"
if [[ -n "${XAI_IMAGE_REPO:-}" ]]; then
  IMAGE_REPO="${XAI_IMAGE_REPO}"
elif [[ "$IMAGE" == *@* ]]; then
  IMAGE_REPO="${IMAGE%@*}"
elif [[ "$IMAGE" == *:* ]]; then
  IMAGE_REPO="${IMAGE%:*}"
else
  IMAGE_REPO="$IMAGE"
fi
CONTAINER_NAME="${XAI_CONTAINER_NAME:-quantbot-xai}"

ARTIFACT_HOST_DIR="${AI_MODEL_WEIGHTS_HOST_DIR:-/mnt/storage/ai-artifacts}"
ARTIFACT_CONTAINER_DIR="${AI_MODEL_WEIGHTS_DIR:-/mnt/ai-artifacts}"

# Optional runtime compatibility with existing cron one-liner:
#   XAI_ENV_FILE=/home/your-user/env
#   XAI_ADD_HOST_GATEWAY=true
XAI_ENV_FILE="${XAI_ENV_FILE:-}"
XAI_ADD_HOST_GATEWAY="${XAI_ADD_HOST_GATEWAY:-true}"

# Optional auth. If omitted, script uses existing docker auth state.
GHCR_READ_USER="${GHCR_READ_USER:-}"
GHCR_READ_TOKEN="${GHCR_READ_TOKEN:-}"

mkdir -p "$ARTIFACT_HOST_DIR"

if [[ -n "$GHCR_READ_USER" && -n "$GHCR_READ_TOKEN" ]]; then
  echo "$GHCR_READ_TOKEN" | docker login ghcr.io -u "$GHCR_READ_USER" --password-stdin
else
  echo "[INFO] GHCR_READ_USER/TOKEN not set. Using existing docker login state."
fi

if docker pull "$IMAGE"; then
  echo "[INFO] Pulled latest XAI image: $IMAGE"
else
  echo "[WARN] docker pull failed. Falling back to local image if present."
  if ! docker image inspect "$IMAGE" >/dev/null 2>&1; then
    echo "[ERROR] No local image available: $IMAGE"
    exit 1
  fi
fi

# Skip if same job is already running, or clean stale container with exact same name.
if docker container inspect "$CONTAINER_NAME" >/dev/null 2>&1; then
  container_state="$(docker inspect -f '{{.State.Status}}' "$CONTAINER_NAME")"
  if [[ "$container_state" == "running" ]]; then
    echo "[INFO] $CONTAINER_NAME is already running. Skip this run."
    exit 0
  fi
  if [[ "$container_state" == "exited" || "$container_state" == "created" || "$container_state" == "dead" ]]; then
    docker rm "$CONTAINER_NAME" >/dev/null 2>&1 || true
  fi
fi

run_args=(
  --rm
  --name "$CONTAINER_NAME"
  -e "AI_MODEL_WEIGHTS_DIR=$ARTIFACT_CONTAINER_DIR"
  -v "$ARTIFACT_HOST_DIR:$ARTIFACT_CONTAINER_DIR"
)

if [[ -n "$XAI_ENV_FILE" ]]; then
  run_args+=(--env-file "$XAI_ENV_FILE")
fi

if [[ "$XAI_ADD_HOST_GATEWAY" == "true" ]]; then
  run_args+=(--add-host=host.docker.internal:host-gateway)
fi

docker run "${run_args[@]}" "$IMAGE"

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
