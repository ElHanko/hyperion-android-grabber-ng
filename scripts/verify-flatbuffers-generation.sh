#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
GENERATED_DIR="$PROJECT_ROOT/common/build/generated/source/flatbuffers/main"
TASK=":common:generateFlatBuffersJava"

case "$GENERATED_DIR" in
    "$PROJECT_ROOT/common/build/"*) ;;
    *)
        echo "Refusing to manage unexpected generated directory: $GENERATED_DIR" >&2
        exit 2
        ;;
esac

if [[ -L "$GENERATED_DIR" ]]; then
    echo "Refusing to replace a symbolic-link output directory: $GENERATED_DIR" >&2
    exit 2
fi

TEMP_DIR="$(mktemp -d)"
trap 'rm -rf -- "$TEMP_DIR"' EXIT

remove_generated_output() {
    if [[ -L "$GENERATED_DIR" ]]; then
        echo "Refusing to remove a symbolic-link output directory: $GENERATED_DIR" >&2
        exit 2
    fi
    rm -rf -- "$GENERATED_DIR"
}

write_manifest() {
    local destination="$1"
    if [[ ! -d "$GENERATED_DIR" ]]; then
        echo "Generated directory is missing: $GENERATED_DIR" >&2
        exit 1
    fi

    (
        cd "$GENERATED_DIR"
        find . -type f -name '*.java' -print0 \
            | LC_ALL=C sort -z \
            | while IFS= read -r -d '' generated_file; do
                file_hash="$(sha256sum -- "$generated_file")"
                printf '%s  %s\n' "${file_hash%% *}" "${generated_file#./}"
            done
    ) > "$destination"

    if [[ ! -s "$destination" ]]; then
        echo "No generated Java files were found" >&2
        exit 1
    fi
}

generate_and_hash() {
    local manifest="$1"
    remove_generated_output
    (
        cd "$PROJECT_ROOT"
        ./gradlew --no-daemon --console=plain "$TASK"
    )
    write_manifest "$manifest"
}

generate_and_hash "$TEMP_DIR/first.manifest"
generate_and_hash "$TEMP_DIR/second.manifest"

if ! cmp --silent "$TEMP_DIR/first.manifest" "$TEMP_DIR/second.manifest"; then
    echo "FlatBuffer Java generation is not deterministic" >&2
    diff --unified "$TEMP_DIR/first.manifest" "$TEMP_DIR/second.manifest" >&2 || true
    exit 1
fi

tree_hash="$(sha256sum "$TEMP_DIR/second.manifest")"
printf 'FlatBuffer Java generation is deterministic: %s\n' "${tree_hash%% *}"
