# PSMobile Android-Cross-Build-Image
#
# Enthaelt Android NDK, CMake, Ninja und alles, was der PrusaSlicer-
# deps-Baum an Hilfswerkzeugen braucht (autotools fuer GMP/MPFR,
# perl fuer OpenSSL, python fuer diverse Generatoren).
#
# Bauen:   docker build -t psmobile-ndk:1 build/docker
# Nutzen:  siehe build/scripts/dockerrun.sh

FROM ubuntu:24.04

ARG NDK_VERSION=r27c
ARG CMAKE_VERSION=3.30.5
ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update && apt-get install -y --no-install-recommends \
        build-essential \
        git curl wget unzip zip xz-utils ca-certificates \
        python3 python3-pip \
        autoconf automake libtool pkg-config m4 texinfo \
        perl patch file bison flex gettext \
        ninja-build \
        rsync locales \
    && rm -rf /var/lib/apt/lists/*

# UTF-8 ist Pflicht: das Boost-Archiv enthaelt Pfade mit Nicht-ASCII-Zeichen,
# die CMake unter der C-Locale nicht entpacken kann.
ENV LANG=C.UTF-8 \
    LC_ALL=C.UTF-8 \
    LANGUAGE=C.UTF-8

# CMake: die Ubuntu-Version ist zu alt fuer einige deps-Rezepte
RUN curl -fsSL "https://github.com/Kitware/CMake/releases/download/v${CMAKE_VERSION}/cmake-${CMAKE_VERSION}-linux-x86_64.tar.gz" \
      -o /tmp/cmake.tgz \
    && mkdir -p /opt/cmake \
    && tar -xzf /tmp/cmake.tgz -C /opt/cmake --strip-components=1 \
    && rm /tmp/cmake.tgz
ENV PATH="/opt/cmake/bin:${PATH}"

# Android NDK
RUN curl -fsSL "https://dl.google.com/android/repository/android-ndk-${NDK_VERSION}-linux.zip" \
      -o /tmp/ndk.zip \
    && unzip -q /tmp/ndk.zip -d /opt \
    && mv /opt/android-ndk-${NDK_VERSION} /opt/android-ndk \
    && rm /tmp/ndk.zip

ENV ANDROID_NDK_HOME=/opt/android-ndk
ENV ANDROID_NDK_ROOT=/opt/android-ndk
ENV PATH="${ANDROID_NDK_HOME}/toolchains/llvm/prebuilt/linux-x86_64/bin:${PATH}"

# Nicht als root bauen, damit die Artefakte auf dem Share dem Nutzer gehoeren
ARG UID=99
ARG GID=100
RUN groupadd -g ${GID} -o builder 2>/dev/null || true; \
    useradd -m -u ${UID} -g ${GID} -o -s /bin/bash builder 2>/dev/null || true

WORKDIR /work
CMD ["/bin/bash"]
