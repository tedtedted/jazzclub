#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "usage: $0 VERSION ARCH BINARY OUT_DIR" >&2
  echo "  ARCH is a macOS architecture label, for example arm64 or x86_64" >&2
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

bundle="$root/jazzclub-${version}-macos-${arch}"
install -d "$bundle/bin" "$bundle/share/doc/jazzclub" "$bundle/share/licenses/jazzclub"
install -m 0755 "$binary" "$bundle/bin/jazzclub"
install -m 0644 README.md "$bundle/share/doc/jazzclub/README.md"
install -m 0644 LICENSE "$bundle/share/licenses/jazzclub/LICENSE"
install -m 0644 NOTICE "$bundle/share/licenses/jazzclub/NOTICE"

cat > "$bundle/INSTALL.md" <<INSTALL
# jazzclub ${version}

Install ffmpeg first:

\`\`\`sh
brew install ffmpeg
\`\`\`

Then copy the binary somewhere on your PATH:

\`\`\`sh
install -m 755 bin/jazzclub /usr/local/bin/jazzclub
\`\`\`
INSTALL

install -d "$out_dir"
tar -C "$root" -czf "$out_dir/jazzclub-${version}-macos-${arch}.tar.gz" "$(basename "$bundle")"
