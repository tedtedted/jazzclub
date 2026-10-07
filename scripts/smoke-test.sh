#!/usr/bin/env bash
# Checks what can be checked without a sound card or a Pandora account: the binary starts, parses its
# command line, reads a config and fails cleanly without credentials. Used by CI and by the release.
#
#   smoke-test.sh <binary> [expected-version]
set -euo pipefail

binary=${1:?usage: smoke-test.sh <binary> [expected-version]}
expected_version=${2:-}

ls -lh "$binary"

version_output=$("$binary" --version)
echo "$version_output"
if [[ -n "$expected_version" && "$version_output" != "jazzclub $expected_version" ]]; then
    echo "expected 'jazzclub $expected_version'" >&2
    exit 1
fi

"$binary" --help | grep -q -- '--verbose'

status=0
"$binary" --no-such-option > /dev/null 2>&1 || status=$?
[[ $status -eq 2 ]] || { echo "expected exit 2 for a bad option, got $status" >&2; exit 1; }

status=0
output=$("$binary" --config /nonexistent/config < /dev/null 2>&1) || status=$?
echo "$output"
[[ $status -eq 1 ]] || { echo "expected exit 1 without credentials, got $status" >&2; exit 1; }
grep -q 'No Pandora account configured' <<< "$output"

size_mb=$(( $(wc -c < "$binary") / 1048576 ))
echo "binary size: ${size_mb} MB"
[[ $size_mb -lt 100 ]] || { echo "binary exceeds the 100 MB budget" >&2; exit 1; }

# Informational, Linux only: the oldest glibc this binary runs on, and what the embedded Java Sound
# library needs at run time. Packagers want to know both.
if [[ "$(uname -s)" == Linux ]] && command -v objdump > /dev/null; then
    echo "requires glibc >= $(objdump -T "$binary" | grep -oE 'GLIBC_[0-9.]+' | sort -uV | tail -1 | cut -d_ -f2)"
    if [[ -n "${JAVA_HOME:-}" && -f "$JAVA_HOME/lib/libjsound.so" ]]; then
        echo "libjsound needs:"; ldd "$JAVA_HOME/lib/libjsound.so" | grep -E 'asound|not found' || true
    fi
fi
