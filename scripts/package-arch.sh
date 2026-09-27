#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "usage: $0 VERSION ARCH BINARY OUT_DIR" >&2
  echo "  ARCH is an Arch Linux architecture, for example x86_64 or aarch64" >&2
}

if [[ $# -ne 4 ]]; then
  usage
  exit 64
fi

version="$1"
arch="$2"
binary="$3"
out_dir="$4"

if [[ ! -x "$binary" ]]; then
  echo "binary is missing or not executable: $binary" >&2
  exit 66
fi

completion="${COMPLETION:-target/jazzclub_completion}"
if [[ ! -s "$completion" ]]; then
  echo "shell completion is missing: $completion (the Maven build generates it)" >&2
  exit 66
fi

# The built-in decoder's native library, installed next to the binary (see LavaplayerNatives). The
# native Maven build stages this platform's copy beside target/jazzclub.
library="${LIBCONNECTOR:-$(dirname "$binary")/libconnector.so}"
if [[ ! -s "$library" ]]; then
  echo "decoder library is missing: $library (the native Maven build stages it next to the binary)" >&2
  exit 66
fi

root="$(mktemp -d)"
trap 'rm -rf "$root"' EXIT

pkg="$root/package"
install -d "$pkg/usr/bin" "$pkg/usr/lib/jazzclub" "$pkg/usr/share/doc/jazzclub" "$pkg/usr/share/licenses/jazzclub" \
  "$pkg/usr/share/bash-completion/completions"
install -m 0755 "$binary" "$pkg/usr/bin/jazzclub"
install -m 0644 "$library" "$pkg/usr/lib/jazzclub/libconnector.so"
install -m 0644 README.md "$pkg/usr/share/doc/jazzclub/README.md"
install -m 0644 LICENSE "$pkg/usr/share/licenses/jazzclub/LICENSE"
install -m 0644 NOTICE "$pkg/usr/share/licenses/jazzclub/NOTICE"
install -m 0644 "$completion" "$pkg/usr/share/bash-completion/completions/jazzclub"

size="$(du -sk "$pkg" | awk '{print $1 * 1024}')"
builddate="$(date +%s)"

cat > "$pkg/.PKGINFO" <<PKGINFO
pkgname = jazzclub
pkgbase = jazzclub
pkgver = ${version}-1
pkgdesc = Console client for Pandora internet radio
url = https://github.com/tedtedted/jazzclub
builddate = ${builddate}
packager = GitHub Actions
size = ${size}
arch = ${arch}
license = MIT
depend = ffmpeg
depend = alsa-lib
depend = gcc-libs
PKGINFO

install -d "$out_dir"
tar_args=(-C "$pkg" -cf - .)
if tar --version 2>/dev/null | grep -qi 'gnu tar'; then
  tar_args=(--sort=name --owner=0 --group=0 --numeric-owner "${tar_args[@]}")
fi

tar "${tar_args[@]}" | zstd -f -19 -T0 -o "$out_dir/jazzclub-${version}-1-${arch}.pkg.tar.zst"
