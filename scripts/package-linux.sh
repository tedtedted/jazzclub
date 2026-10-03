#!/usr/bin/env bash
# A plain tarball of the Linux binary, laid out like the macOS archive. It is what distribution-neutral
# users download, and what packaging/arch/PKGBUILD.in installs from.
set -euo pipefail

usage() {
  echo "usage: $0 VERSION ARCH BINARY OUT_DIR" >&2
  echo "  ARCH is the machine name as uname -m prints it, for example x86_64 or aarch64" >&2
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

bundle="$root/jazzclub-${version}-linux-${arch}"
install -d "$bundle/bin" "$bundle/libexec/jazzclub" "$bundle/share/doc/jazzclub" \
  "$bundle/share/licenses/jazzclub" "$bundle/share/bash-completion/completions"
install -m 0755 "$binary" "$bundle/bin/jazzclub"
install -m 0644 "$library" "$bundle/libexec/jazzclub/libconnector.so"
install -m 0644 README.md "$bundle/share/doc/jazzclub/README.md"
install -m 0644 LICENSE "$bundle/share/licenses/jazzclub/LICENSE"
install -m 0644 NOTICE "$bundle/share/licenses/jazzclub/NOTICE"
install -m 0644 licenses/*.txt "$bundle/share/licenses/jazzclub/"
install -m 0644 "$completion" "$bundle/share/bash-completion/completions/jazzclub"

install -d "$out_dir"
tar_args=(-C "$root" -czf "$out_dir/jazzclub-${version}-linux-${arch}.tar.gz")
if tar --version 2>/dev/null | grep -qi 'gnu tar'; then
  # same input, same archive: the PKGBUILD pins its checksum
  tar_args=(--sort=name --owner=0 --group=0 --numeric-owner "${tar_args[@]}")
fi
tar "${tar_args[@]}" "$(basename "$bundle")"
