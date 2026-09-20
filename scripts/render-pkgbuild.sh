#!/usr/bin/env bash
# Fills packaging/arch/PKGBUILD.in with a version and the checksums of that version's Linux tarballs.
#
#   render-pkgbuild.sh VERSION DIST_DIR > PKGBUILD
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "usage: $0 VERSION DIST_DIR" >&2
  exit 64
fi

version="$1"
dist="$2"
template="$(dirname "$0")/../packaging/arch/PKGBUILD.in"

sha256_of() {
  local file="$dist/jazzclub-${version}-linux-$1.tar.gz"
  if [[ ! -f "$file" ]]; then
    echo "missing $file" >&2
    exit 66
  fi
  if command -v sha256sum > /dev/null; then sha256sum "$file"; else shasum -a 256 "$file"; fi | awk '{print $1}'
}

x86_64="$(sha256_of x86_64)"
aarch64="$(sha256_of aarch64)"

# makepkg rejects a hyphen in pkgver; Arch's convention for pre-releases is an underscore
pkgver="${version//-/_}"

sed -e "s/@VERSION@/${version}/g" \
    -e "s/@PKGVER@/${pkgver}/g" \
    -e "s/@SHA256_X86_64@/${x86_64}/g" \
    -e "s/@SHA256_AARCH64@/${aarch64}/g" \
    "$template"
