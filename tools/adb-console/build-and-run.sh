#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/../.." && pwd)"

IMAGE_NAME="${IMAGE_NAME:-firetv-adb}"
CONTAINER_NAME="${CONTAINER_NAME:-firetv-adb}"
KEYS_VOLUME="${KEYS_VOLUME:-firetv-adb-keys}"

echo "Baue Docker-Image: $IMAGE_NAME"
docker build \
  --tag "$IMAGE_NAME" \
  "$SCRIPT_DIR"

if docker container inspect "$CONTAINER_NAME" >/dev/null 2>&1; then
  echo "Entferne vorhandenen Container: $CONTAINER_NAME"
  docker rm --force "$CONTAINER_NAME" >/dev/null
fi

echo "Starte Container: $CONTAINER_NAME"
docker run -d \
  --name "$CONTAINER_NAME" \
  --restart unless-stopped \
  --network host \
  --volume "$KEYS_VOLUME:/root/.android" \
  --volume "$REPO_ROOT:/workspace:ro" \
  "$IMAGE_NAME"

echo
echo "ADB-Konsole läuft."
echo "Konsole öffnen:"
echo "  docker exec -it $CONTAINER_NAME bash"
echo
echo "Beispiel:"
echo "  docker exec -it $CONTAINER_NAME adb devices"
