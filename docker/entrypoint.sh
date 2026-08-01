#!/usr/bin/env bash
set -euo pipefail

LOCAL_UID="${LOCAL_UID:-1000}"
LOCAL_GID="${LOCAL_GID:-1000}"
GRADLE_USER_HOME="${GRADLE_USER_HOME:-/gradle-cache}"

if [[ ! "$LOCAL_UID" =~ ^[0-9]+$ || ! "$LOCAL_GID" =~ ^[0-9]+$ ]]; then
    echo "Fehler: LOCAL_UID und LOCAL_GID müssen numerisch sein." >&2
    exit 2
fi

mkdir -p "$GRADLE_USER_HOME"

if [[ "$LOCAL_UID" == "0" ]]; then
    export GRADLE_USER_HOME
    exec "$@"
fi

group_name="$(getent group "$LOCAL_GID" | cut -d: -f1 || true)"
if [[ -z "$group_name" ]]; then
    group_name="android-builder"
    groupadd --gid "$LOCAL_GID" "$group_name"
fi

user_name="$(getent passwd "$LOCAL_UID" | cut -d: -f1 || true)"
if [[ -z "$user_name" ]]; then
    user_name="android-builder"
    useradd \
        --create-home \
        --uid "$LOCAL_UID" \
        --gid "$LOCAL_GID" \
        --shell /bin/bash \
        "$user_name"
fi

home_dir="$(getent passwd "$LOCAL_UID" | cut -d: -f6)"
mkdir -p "$home_dir"

cache_uid="$(stat --format='%u' "$GRADLE_USER_HOME")"
cache_gid="$(stat --format='%g' "$GRADLE_USER_HOME")"
if [[ "$cache_uid" != "$LOCAL_UID" || "$cache_gid" != "$LOCAL_GID" ]]; then
    chown -R "$LOCAL_UID:$LOCAL_GID" "$GRADLE_USER_HOME"
fi

exec gosu "$LOCAL_UID:$LOCAL_GID" \
    env \
        HOME="$home_dir" \
        GRADLE_USER_HOME="$GRADLE_USER_HOME" \
        "$@"
