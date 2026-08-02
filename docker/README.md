# Docker build

The builder uses a pinned Android SDK and a persistent Docker volume for the
Gradle cache. Source code is not copied into the image; it is mounted writable
at `/workspace` during a build.

## Requirements

- Docker
- an executable Gradle wrapper in the repository (`gradlew`)
- the Gradle and Android Gradle Plugin configuration modernized for JDK 17

## Build debug APKs

```bash
./docker/build.sh
```

or explicitly:

```bash
./docker/build.sh debug
```

## Build release APKs

```bash
./docker/build.sh release
```

Release builds require these local files, which are protected by `.gitignore`:

```text
signing/hyperion-ng-release.p12
signing/signing.properties
```

The build script mounts this directory read-only at `/signing`. Both app
modules load the same PKCS#12 configuration from
`/signing/signing.properties`. If either file is missing or the configuration
is incomplete, the release build stops before copying APKs to `dist/`.
Keystores and passwords are copied neither into the Docker image nor into the
repository.

## Outputs

Generated APKs are collected under these directories:

```text
dist/mobile/
dist/tv/
```

## Gradle cache

The named volume `hyperion-android-grabber-ng-gradle-cache` is used by default.
It preserves the Gradle wrapper, dependencies, and build cache between builds.

Remove the cache:

```bash
docker volume rm hyperion-android-grabber-ng-gradle-cache
```

Use environment variables to select different names:

```bash
IMAGE_NAME=my-android-builder CACHE_VOLUME=my-gradle-cache ./docker/build.sh debug
```
