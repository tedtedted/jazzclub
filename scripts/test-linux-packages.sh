#!/usr/bin/env bash
# Install the release packages into clean supported distributions, then exercise native audio.
set -euo pipefail
[[ $# -eq 4 ]] || { echo "usage: $0 VERSION DEB_ARCH ARCH_ARCH DIST" >&2; exit 64; }
version=$1
deb_arch=$2
arch_arch=$3
dist=$4
# Paths passed to the containers are repository relative; reject shell metacharacters.
[[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.-]+)?$ ]] || exit 64
[[ "$deb_arch" == amd64 || "$deb_arch" == arm64 ]] || exit 64
[[ "$arch_arch" == x86_64 || "$arch_arch" == aarch64 ]] || exit 64
[[ "$dist" =~ ^[a-zA-Z0-9_./-]+$ && "$dist" != /* ]] || exit 64

for image in debian:12-slim ubuntu:22.04; do
    docker run --rm -v "$PWD:/workspace:ro" -w /workspace \
        -e "PACKAGE=$dist/jazzclub_${version}_${deb_arch}.deb" -e "VERSION=$version" "$image" \
        bash -euc '
            apt-get update -qq
            apt-get install -y --no-install-recommends python3 openssl "/workspace/$PACKAGE"
            scripts/smoke-test.sh /usr/bin/jazzclub "$VERSION"
            python3 scripts/smoke-test-audio.py /usr/bin/jazzclub
        '
done

# Arch Linux publishes an official container for x86_64 only. Debian-family tests above
# exercise both architectures; Arch ARM installation still needs a real aarch64 host.
if [[ "$arch_arch" == x86_64 ]]; then
    docker run --rm -v "$PWD:/workspace:ro" -w /workspace \
        -e "PACKAGE=$dist/jazzclub-${version}-1-${arch_arch}.pkg.tar.zst" -e "VERSION=$version" archlinux:base \
        bash -euc '
            pacman -Syu --noconfirm --needed python openssl
            pacman -U --noconfirm "/workspace/$PACKAGE"
            scripts/smoke-test.sh /usr/bin/jazzclub "$VERSION"
            python3 scripts/smoke-test-audio.py /usr/bin/jazzclub
        '
fi
