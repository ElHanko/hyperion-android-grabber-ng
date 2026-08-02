FROM debian:trixie-slim

RUN apt-get update \
    && apt-get install --yes --no-install-recommends \
        adb \
        ca-certificates \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /workspace

CMD ["sleep", "infinity"]
