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

completion="${COMPLETION:-target/jazzclub_completion}"
if [[ ! -s "$completion" ]]; then
  echo "shell completion is missing: $completion (the Maven build generates it)" >&2
  exit 66
fi

# The built-in decoder's native library, installed next to the binary (see LavaplayerNatives). The
# native Maven build stages this platform's copy beside target/jazzclub.
library="${LIBCONNECTOR:-$(dirname "$binary")/libconnector.dylib}"
if [[ ! -s "$library" ]]; then
  echo "decoder library is missing: $library (the native Maven build stages it next to the binary)" >&2
  exit 66
fi

root="$(mktemp -d)"
trap 'rm -rf "$root"' EXIT

bundle="$root/jazzclub-${version}-macos-${arch}"
install -d "$bundle/bin" "$bundle/libexec/jazzclub" "$bundle/share/doc/jazzclub" \
  "$bundle/share/licenses/jazzclub" "$bundle/share/bash-completion/completions"
install -m 0755 "$binary" "$bundle/bin/jazzclub"
install -m 0644 "$library" "$bundle/libexec/jazzclub/libconnector.dylib"
install -m 0644 README.md "$bundle/share/doc/jazzclub/README.md"
install -m 0644 LICENSE "$bundle/share/licenses/jazzclub/LICENSE"
install -m 0644 NOTICE "$bundle/share/licenses/jazzclub/NOTICE"
install -m 0644 licenses/*.txt "$bundle/share/licenses/jazzclub/"
install -m 0644 "$completion" "$bundle/share/bash-completion/completions/jazzclub"

cat > "$bundle/INSTALL.md" <<INSTALL
# jazzclub ${version}

jazzclub decodes Pandora's audio itself. ffmpeg is optional: only \`decoder = ffmpeg\`, a
\`sample_rate\` other than 44100, or a machine where the built-in decoder cannot load need it
(\`brew install ffmpeg\`).

Copy the binary somewhere on your PATH, and its decoder library into
\`libexec/jazzclub\` beside that \`bin\` (jazzclub looks for it there):

\`\`\`sh
install -m 755 bin/jazzclub /usr/local/bin/jazzclub
install -d /usr/local/libexec/jazzclub
install -m 644 libexec/jazzclub/libconnector.dylib /usr/local/libexec/jazzclub/
\`\`\`

Downloaded files are quarantined by macOS. Clear that once:

\`\`\`sh
xattr -d com.apple.quarantine /usr/local/bin/jazzclub /usr/local/libexec/jazzclub/libconnector.dylib
\`\`\`

For tab completion in bash or zsh, add this to your shell profile:

\`\`\`sh
source /path/to/share/bash-completion/completions/jazzclub
\`\`\`
INSTALL

install -d "$out_dir"
tar -C "$root" -czf "$out_dir/jazzclub-${version}-macos-${arch}.tar.gz" "$(basename "$bundle")"
