# syntax=docker/dockerfile:1.7
FROM eclipse-temurin:17-jdk-jammy

ARG ANDROID_CMDLINE_TOOLS_VERSION=15859902
ARG ANDROID_CMDLINE_TOOLS_SHA256=4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583
ARG ANDROID_PLATFORM=36
ARG ANDROID_BUILD_TOOLS=36.0.0
ARG FLATC_VERSION=25.9.23
ARG FLATC_ARCHIVE=Linux.flatc.binary.g++-13.zip
ARG FLATC_URL=https://github.com/google/flatbuffers/releases/download/v25.9.23/Linux.flatc.binary.g%2B%2B-13.zip
ARG FLATC_SHA256=de0c6ad114a5a686ecf64322528c602c7d4512446a93f290f54f00ee5abea487
ARG FLATBUFFERS_COMMIT=187240970746d00bbd26b0f5873ed54d2477f9f3
ARG FLATBUFFERS_SOURCE_SHA256=cb4a0546919105755629e29913a0081f999a9296657469839f05b6fcd0e02ef1
ARG FLATBUFFERS_JAVA_SHA256=89e9f694825de4848e0c676dd6478b6c005e8cef2514bc77c54ecd24446d9c17
ARG TARGETARCH

ENV DEBIAN_FRONTEND=noninteractive \
    ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    FLATC=/usr/local/bin/flatc \
    FLATBUFFERS_MAVEN_REPOSITORY=/opt/flatbuffers-maven \
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
    test "${TARGETARCH}" = "amd64"; \
    curl --fail --location --show-error --silent \
        "${FLATC_URL}" \
        --output "/tmp/${FLATC_ARCHIVE}"; \
    echo "${FLATC_SHA256}  /tmp/${FLATC_ARCHIVE}" | sha256sum --check --strict; \
    test "$(unzip -Z1 "/tmp/${FLATC_ARCHIVE}")" = "flatc"; \
    unzip -q "/tmp/${FLATC_ARCHIVE}" -d /tmp/flatc-release; \
    install -m 0755 /tmp/flatc-release/flatc "${FLATC}"; \
    test "$("${FLATC}" --version)" = "flatc version ${FLATC_VERSION}"; \
    rm -rf "/tmp/${FLATC_ARCHIVE}" /tmp/flatc-release

# FlatBuffers 25.9.23 declares this Java coordinate in its official POM, but
# that release was not published to Maven Central. Build the runtime from the
# exact tagged source and expose it through a read-only local Maven repository.
RUN set -eux; \
    source_archive="/tmp/flatbuffers-${FLATBUFFERS_COMMIT}.tar.gz"; \
    source_url="https://github.com/google/flatbuffers/archive/${FLATBUFFERS_COMMIT}.tar.gz"; \
    source_root="/tmp/flatbuffers-${FLATBUFFERS_COMMIT}"; \
    classes_dir="/tmp/flatbuffers-java-classes"; \
    artifact_dir="${FLATBUFFERS_MAVEN_REPOSITORY}/com/google/flatbuffers/flatbuffers-java/${FLATC_VERSION}"; \
    curl --fail --location --show-error --silent "${source_url}" --output "${source_archive}"; \
    echo "${FLATBUFFERS_SOURCE_SHA256}  ${source_archive}" | sha256sum --check --strict; \
    mkdir -p "${source_root}" "${classes_dir}" "${artifact_dir}"; \
    tar -xzf "${source_archive}" --strip-components=1 -C "${source_root}"; \
    find "${source_root}/java/src/main/java" -type f -name '*.java' -print \
        | LC_ALL=C sort > /tmp/flatbuffers-java-sources.list; \
    javac --release 8 -d "${classes_dir}" @/tmp/flatbuffers-java-sources.list; \
    jar --create \
        --file "${artifact_dir}/flatbuffers-java-${FLATC_VERSION}.jar" \
        --date 2025-09-24T05:24:28Z \
        -C "${classes_dir}" .; \
    install -m 0644 "${source_root}/java/pom.xml" \
        "${artifact_dir}/flatbuffers-java-${FLATC_VERSION}.pom"; \
    echo "${FLATBUFFERS_JAVA_SHA256}  ${artifact_dir}/flatbuffers-java-${FLATC_VERSION}.jar" \
        | sha256sum --check --strict; \
    test "$(sed -n 's:.*<version>\([^<]*\)</version>.*:\1:p' "${artifact_dir}/flatbuffers-java-${FLATC_VERSION}.pom" | head -n 1)" = "${FLATC_VERSION}"; \
    rm -rf "${source_archive}" "${source_root}" "${classes_dir}" \
        /tmp/flatbuffers-java-sources.list

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
