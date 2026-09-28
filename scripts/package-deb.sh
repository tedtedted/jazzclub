#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "usage: $0 VERSION ARCH BINARY OUT_DIR" >&2
  echo "  ARCH is a Debian architecture, for example amd64 or arm64" >&2
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

pkg="$root/jazzclub_${version}_${arch}"
install -d "$pkg/DEBIAN" "$pkg/usr/bin" "$pkg/usr/lib/jazzclub" "$pkg/usr/share/doc/jazzclub" "$pkg/usr/share/licenses/jazzclub" \
  "$pkg/usr/share/bash-completion/completions"
install -m 0755 "$binary" "$pkg/usr/bin/jazzclub"
install -m 0644 "$library" "$pkg/usr/lib/jazzclub/libconnector.so"
install -m 0644 README.md "$pkg/usr/share/doc/jazzclub/README.md"
install -m 0644 LICENSE "$pkg/usr/share/licenses/jazzclub/LICENSE"
install -m 0644 NOTICE "$pkg/usr/share/licenses/jazzclub/NOTICE"
install -m 0644 licenses/*.txt "$pkg/usr/share/licenses/jazzclub/"
install -m 0644 "$completion" "$pkg/usr/share/bash-completion/completions/jazzclub"

# libasound2: the Java Sound library inside the binary loads ALSA at run time. Debian 13 renamed the
# package to libasound2t64, which still Provides libasound2. libstdc++6: libconnector needs it.
# ffmpeg is only needed for decoder = ffmpeg, resampling, or when the built-in decoder cannot load.
installed_size_kb="$(du -sk "$pkg" | awk '{print $1}')"
cat > "$pkg/DEBIAN/control" <<CONTROL
Package: jazzclub
Version: ${version}
Section: sound
Priority: optional
Architecture: ${arch}
Maintainer: Ted Redington <ted@tedredington.com>
Depends: libasound2, libstdc++6
Suggests: ffmpeg
Installed-Size: ${installed_size_kb}
Homepage: https://github.com/tedtedted/jazzclub
Description: Console client for Pandora internet radio
 jazzclub is a native console client for Pandora, inspired by pianobar.
CONTROL

install -d "$out_dir"
dpkg-deb --build --root-owner-group "$pkg" "$out_dir/jazzclub_${version}_${arch}.deb"
