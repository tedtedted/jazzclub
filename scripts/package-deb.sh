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

root="$(mktemp -d)"
trap 'rm -rf "$root"' EXIT

pkg="$root/jazzclub_${version}_${arch}"
install -d "$pkg/DEBIAN" "$pkg/usr/bin" "$pkg/usr/share/doc/jazzclub" "$pkg/usr/share/licenses/jazzclub"
install -m 0755 "$binary" "$pkg/usr/bin/jazzclub"
install -m 0644 README.md "$pkg/usr/share/doc/jazzclub/README.md"
install -m 0644 LICENSE "$pkg/usr/share/licenses/jazzclub/LICENSE"
install -m 0644 NOTICE "$pkg/usr/share/licenses/jazzclub/NOTICE"

installed_size_kb="$(du -sk "$pkg" | awk '{print $1}')"
cat > "$pkg/DEBIAN/control" <<CONTROL
Package: jazzclub
Version: ${version}
Section: sound
Priority: optional
Architecture: ${arch}
Maintainer: Ted Redington <ted@tedredington.com>
Depends: ffmpeg
Installed-Size: ${installed_size_kb}
Homepage: https://github.com/tedtedted/jazzclub
Description: Console client for Pandora internet radio
 jazzclub is a native console client for Pandora, inspired by pianobar.
CONTROL

install -d "$out_dir"
dpkg-deb --build --root-owner-group "$pkg" "$out_dir/jazzclub_${version}_${arch}.deb"
