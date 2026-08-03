#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IMAGE_NAME="${IMAGE_NAME:-hyperion-android-grabber-ng-builder}"
CACHE_VOLUME="${CACHE_VOLUME:-hyperion-android-grabber-ng-gradle-cache}"
TEMP_PARENT="${TMPDIR:-/tmp}"
WORK_DIR=""

required_environment=(
    HYPERION_FLATBUFFER_INTEGRATION_TEST
    HYPERION_FLATBUFFER_HOST
)
optional_environment=(
    HYPERION_FLATBUFFER_PORT
    HYPERION_FLATBUFFER_PRIORITY
    HYPERION_FLATBUFFER_CONNECT_TIMEOUT_MS
    HYPERION_FLATBUFFER_READ_TIMEOUT_MS
)

cleanup() {
    if [[ -n "$WORK_DIR" && -d "$WORK_DIR" && ! -L "$WORK_DIR" ]]; then
        case "$WORK_DIR" in
            "$TEMP_PARENT"/hyperion-flatbuffer-integration.??????)
                rm -rf -- "$WORK_DIR"
                ;;
        esac
    fi
}
trap cleanup EXIT

if [[ "${HYPERION_FLATBUFFER_INTEGRATION_TEST:-}" != "1" ]]; then
    echo "HYPERION_FLATBUFFER_INTEGRATION_TEST must be exactly 1." >&2
    exit 2
fi

if [[ -z "${HYPERION_FLATBUFFER_HOST:-}" ]]; then
    echo "HYPERION_FLATBUFFER_HOST must be set when the integration test is enabled." >&2
    exit 2
fi

for name in "${optional_environment[@]}"; do
    if [[ -n "${!name:-}" && ! "${!name}" =~ ^[0-9]+$ ]]; then
        echo "$name must be a positive integer when set." >&2
        exit 2
    fi
done

if ! command -v docker >/dev/null 2>&1; then
    echo "Docker was not found." >&2
    exit 1
fi

if ! docker info >/dev/null 2>&1; then
    echo "The Docker daemon is not reachable." >&2
    exit 1
fi

WORK_DIR="$(mktemp -d "$TEMP_PARENT/hyperion-flatbuffer-integration.XXXXXX")"
case "$WORK_DIR" in
    "$TEMP_PARENT"/hyperion-flatbuffer-integration.??????)
        ;;
    *)
        echo "Unable to create an isolated temporary workspace." >&2
        exit 1
        ;;
esac

# The isolated workspace keeps generated build outputs out of the working tree.
tar \
    --create \
    --file - \
    --exclude='./.git' \
    --exclude='./.gradle' \
    --exclude='./build' \
    --exclude='./dist' \
    --exclude='./signing' \
    --exclude='./common/build' \
    --exclude='./mobile/build' \
    --exclude='./tv/build' \
    -C "$ROOT_DIR" \
    . \
    | tar --extract --file - -C "$WORK_DIR"

docker volume inspect "$CACHE_VOLUME" >/dev/null 2>&1 \
    || docker volume create "$CACHE_VOLUME" >/dev/null

echo "Building the isolated FlatBuffer integration-test environment."
docker build \
    --file "$ROOT_DIR/docker/Dockerfile" \
    --tag "$IMAGE_NAME" \
    "$ROOT_DIR/docker"

echo "Running the opt-in FlatBuffer integration test."
docker run --rm \
    --env "LOCAL_UID=$(id -u)" \
    --env "LOCAL_GID=$(id -g)" \
    --env GRADLE_USER_HOME=/gradle-cache \
    --env HYPERION_FLATBUFFER_INTEGRATION_TEST \
    --env HYPERION_FLATBUFFER_HOST \
    --env HYPERION_FLATBUFFER_PORT \
    --env HYPERION_FLATBUFFER_PRIORITY \
    --env HYPERION_FLATBUFFER_CONNECT_TIMEOUT_MS \
    --env HYPERION_FLATBUFFER_READ_TIMEOUT_MS \
    --volume "$CACHE_VOLUME:/gradle-cache" \
    --volume "$WORK_DIR:/workspace" \
    --workdir /workspace \
    "$IMAGE_NAME" \
    ./gradlew \
        --no-daemon \
        --console=plain \
        :common:testDebugUnitTest \
        --tests com.elhanko.hyperiongrabber.ng.common.network.flatbuffer.FlatBufferHyperionIntegrationTest
