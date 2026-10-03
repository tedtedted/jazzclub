#!/usr/bin/env bash
# Fills in the Homebrew formula template for one release.
#
#   render-formula.sh VERSION TARBALL OUT [URL]
#
# TARBALL is the release's jazzclub-VERSION-macos-arm64.tar.gz; its checksum goes into the formula.
# URL defaults to where the release workflow publishes that file. The workflow's macOS job passes a
# file:// URL instead, to install the formula from the archive it just built.
set -euo pipefail

if [[ $# -lt 3 || $# -gt 4 ]]; then
  echo "usage: $0 VERSION TARBALL OUT [URL]" >&2
  exit 64
fi

version="$1"
tarball="$2"
out="$3"
url="${4:-https://github.com/tedtedted/jazzclub/releases/download/v${version}/$(basename "$tarball")}"
template="$(dirname "$0")/../packaging/homebrew/jazzclub.rb.in"

[[ -s "$tarball" ]] || { echo "release archive is missing: $tarball" >&2; exit 66; }
sha256="$(shasum -a 256 "$tarball" | awk '{print $1}')"

sed -e "s|@VERSION@|${version}|g" -e "s|@URL@|${url}|g" -e "s|@SHA256@|${sha256}|g" "$template" > "$out"
if grep -q '@[A-Z0-9]*@' "$out"; then
  echo "$out still has placeholders" >&2
  exit 1
fi
echo "$out: jazzclub $version, $url, sha256 $sha256"
