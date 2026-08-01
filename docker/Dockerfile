# syntax=docker/dockerfile:1.7
FROM eclipse-temurin:17-jdk-jammy

ARG ANDROID_CMDLINE_TOOLS_VERSION=15859902
ARG ANDROID_CMDLINE_TOOLS_SHA256=4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583
ARG ANDROID_PLATFORM=36
ARG ANDROID_BUILD_TOOLS=36.0.0

ENV DEBIAN_FRONTEND=noninteractive \
    ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    PATH=/opt/android-sdk/cmdline-tools/latest/bin:/opt/android-sdk/platform-tools:${PATH}

RUN apt-get update \
    && apt-get install --yes --no-install-recommends \
        ca-certificates \
        curl \
        git \
        gosu \
        unzip \
    && rm -rf /var/lib/apt/lists/*

RUN set -eux; \
    archive="commandlinetools-linux-${ANDROID_CMDLINE_TOOLS_VERSION}_latest.zip"; \
    url="https://dl.google.com/android/repository/${archive}"; \
    curl --fail --location --show-error --silent "$url" --output "/tmp/${archive}"; \
    echo "${ANDROID_CMDLINE_TOOLS_SHA256}  /tmp/${archive}" | sha256sum --check --strict; \
    mkdir -p "${ANDROID_SDK_ROOT}/cmdline-tools" /tmp/android-command-line-tools; \
    unzip -q "/tmp/${archive}" -d /tmp/android-command-line-tools; \
    mv /tmp/android-command-line-tools/cmdline-tools "${ANDROID_SDK_ROOT}/cmdline-tools/latest"; \
    rm -rf "/tmp/${archive}" /tmp/android-command-line-tools; \
    yes | sdkmanager --licenses >/dev/null; \
    sdkmanager --install \
        "platform-tools" \
        "platforms;android-${ANDROID_PLATFORM}" \
        "build-tools;${ANDROID_BUILD_TOOLS}"

COPY entrypoint.sh /usr/local/bin/android-builder-entrypoint
RUN chmod 0755 /usr/local/bin/android-builder-entrypoint

WORKDIR /workspace
ENTRYPOINT ["/usr/local/bin/android-builder-entrypoint"]
CMD ["./gradlew", "--no-daemon", "tasks"]
