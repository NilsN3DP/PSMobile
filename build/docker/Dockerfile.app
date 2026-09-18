# PSMobile Android-App-Build-Image (Gradle + SDK, ohne NDK)
#
# Getrennt vom NDK-Image, weil die beiden Builds nichts gemeinsam haben:
# der Kern wird cross-kompiliert, die App nur uebersetzt und verpackt.
#
# Bauen: docker build -f build/docker/Dockerfile.app -t psmobile-app:1 build/docker

FROM ubuntu:24.04

ARG CMDLINE_TOOLS=11076708_latest
ARG COMPILE_SDK=35
ARG BUILD_TOOLS=35.0.0

ENV DEBIAN_FRONTEND=noninteractive \
    LANG=C.UTF-8 \
    LC_ALL=C.UTF-8

RUN apt-get update && apt-get install -y --no-install-recommends \
        openjdk-21-jdk-headless \
        curl unzip git ca-certificates \
    && rm -rf /var/lib/apt/lists/*

ENV JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
ENV ANDROID_HOME=/opt/android-sdk
ENV ANDROID_SDK_ROOT=/opt/android-sdk

RUN mkdir -p ${ANDROID_HOME}/cmdline-tools \
    && curl -fsSL "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS}.zip" \
         -o /tmp/cmdline.zip \
    && unzip -q /tmp/cmdline.zip -d ${ANDROID_HOME}/cmdline-tools \
    && mv ${ANDROID_HOME}/cmdline-tools/cmdline-tools ${ANDROID_HOME}/cmdline-tools/latest \
    && rm /tmp/cmdline.zip

ENV PATH="${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${PATH}"

RUN yes | sdkmanager --licenses > /dev/null 2>&1 || true \
    && sdkmanager --install \
         "platform-tools" \
         "platforms;android-${COMPILE_SDK}" \
         "build-tools;${BUILD_TOOLS}" > /dev/null

# Gradle-Cache im Image vorhalten waere schoen, ist aber vom Wrapper
# abhaengig - der Cache wird stattdessen als Volume durchgereicht.
ENV GRADLE_USER_HOME=/gradle-cache

WORKDIR /work
CMD ["/bin/bash"]
