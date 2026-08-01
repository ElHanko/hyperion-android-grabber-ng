#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IMAGE_NAME="${IMAGE_NAME:-hyperion-android-grabber-builder}"
CACHE_VOLUME="${CACHE_VOLUME:-hyperion-android-gradle-cache}"
BUILD_VARIANT="${1:-debug}"

usage() {
    cat <<'HELP'
Nutzung:
  ./docker/build.sh [debug|release]

Optionale Umgebungsvariablen:
  IMAGE_NAME      Name des Docker-Images
  CACHE_VOLUME    Name des persistenten Gradle-Cache-Volumes
HELP
}

case "$BUILD_VARIANT" in
    debug)
        gradle_variant="Debug"
        output_variant="debug"
        ;;
    release)
        gradle_variant="Release"
        output_variant="release"
        ;;
    -h|--help)
        usage
        exit 0
        ;;
    *)
        echo "Fehler: Unbekannte Build-Variante: $BUILD_VARIANT" >&2
        usage >&2
        exit 2
        ;;
esac

if ! command -v docker >/dev/null 2>&1; then
    echo "Fehler: Docker wurde nicht gefunden." >&2
    exit 1
fi

if ! docker info >/dev/null 2>&1; then
    echo "Fehler: Der Docker-Daemon ist nicht erreichbar." >&2
    exit 1
fi

if [[ ! -x "$ROOT_DIR/gradlew" ]]; then
    echo "Fehler: $ROOT_DIR/gradlew fehlt oder ist nicht ausführbar." >&2
    exit 1
fi

docker volume inspect "$CACHE_VOLUME" >/dev/null 2>&1 \
    || docker volume create "$CACHE_VOLUME" >/dev/null

echo "Baue Builder-Image: $IMAGE_NAME"
docker build \
    --file "$ROOT_DIR/docker/Dockerfile" \
    --tag "$IMAGE_NAME" \
    "$ROOT_DIR/docker"

tasks=(
    ":mobile:assemble${gradle_variant}"
    ":tv:assemble${gradle_variant}"
)

echo "Baue Android-APKs (${BUILD_VARIANT})"
docker run --rm \
    --env "LOCAL_UID=$(id -u)" \
    --env "LOCAL_GID=$(id -g)" \
    --env GRADLE_USER_HOME=/gradle-cache \
    --volume "$CACHE_VOLUME:/gradle-cache" \
    --volume "$ROOT_DIR:/workspace" \
    --workdir /workspace \
    "$IMAGE_NAME" \
    ./gradlew \
        --no-daemon \
        --build-cache \
        --console=plain \
        "${tasks[@]}"

for module in mobile tv; do
    source_dir="$ROOT_DIR/$module/build/outputs/apk/$output_variant"
    target_dir="$ROOT_DIR/dist/$module"

    mkdir -p "$target_dir"

    if [[ "$target_dir" != "$ROOT_DIR/dist/$module" ]]; then
        echo "Fehler: Unerwartetes Zielverzeichnis: $target_dir" >&2
        exit 1
    fi

    find "$target_dir" -maxdepth 1 -type f -name '*.apk' -delete

    if [[ ! -d "$source_dir" ]]; then
        echo "Fehler: APK-Ausgabeverzeichnis fehlt: $source_dir" >&2
        exit 1
    fi

    mapfile -d '' apks < <(
        find "$source_dir" -maxdepth 1 -type f -name '*.apk' -print0
    )

    if (( ${#apks[@]} == 0 )); then
        echo "Fehler: Keine APK für Modul '$module' unter '$source_dir' gefunden." >&2
        exit 1
    fi

    for apk in "${apks[@]}"; do
        cp -- "$apk" "$target_dir/"
        printf 'Erstellt: %s\n' "$target_dir/$(basename "$apk")"
    done
done
