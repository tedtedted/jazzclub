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

# Configuration checks stay useful in the shipped native binary: name the bad setting and
# its constraint before login or audio setup. This also guards against losing validation
# when trimming dependencies from the application.
validation_dir=$(mktemp -d)
trap 'rm -rf "$validation_dir"' EXIT
while IFS='|' read -r config_key config_value expected_reason; do
    printf '%s = %s\n' "$config_key" "$config_value" > "$validation_dir/config"
    status=0
    output=$("$binary" --config "$validation_dir/config" < /dev/null 2>&1) || status=$?
    if [[ $status -ne 1 ]] || ! grep -Fq "$expected_reason" <<< "$output"; then
        echo "$output" >&2
        echo "expected exit 1 with '$expected_reason' for $config_key = $config_value" >&2
        exit 1
    fi
done <<'INVALID_SETTINGS'
gain_mul|-1|gain_mul must not be negative
gain_mul|NaN|gain_mul must be finite
gain_mul|Infinity|gain_mul must be finite
gain_mul|-Infinity|gain_mul must be finite
history|-1|history must not be negative
max_retry|0|max_retry must be at least 1
buffer_seconds|-1|buffer_seconds must be between 0 and 600
buffer_seconds|601|buffer_seconds must be between 0 and 600
sample_rate|7|sample_rate
rpc_host| |rpc_host must not be empty
rpc_tls_port|0|rpc_tls_port must be between 1 and 65535
rpc_tls_port|65536|rpc_tls_port must be between 1 and 65535
timeout|0|timeout must be positive
timeout|-1|timeout must be positive
INVALID_SETTINGS
echo "invalid configuration: 14 readable startup errors passed"

size_mb=$(( $(wc -c < "$binary") / 1048576 ))
echo "binary size: ${size_mb} MiB"
[[ $size_mb -lt 100 ]] || { echo "binary exceeds the 100 MiB budget" >&2; exit 1; }

# Informational, Linux only: the oldest glibc this binary runs on, and what the embedded Java Sound
# library needs at run time. Packagers want to know both.
if [[ "$(uname -s)" == Linux ]] && command -v objdump > /dev/null; then
    echo "requires glibc >= $(objdump -T "$binary" | grep -oE 'GLIBC_[0-9.]+' | sort -uV | tail -1 | cut -d_ -f2)"
    if [[ -n "${JAVA_HOME:-}" && -f "$JAVA_HOME/lib/libjsound.so" ]]; then
        echo "libjsound needs:"; ldd "$JAVA_HOME/lib/libjsound.so" | grep -E 'asound|not found' || true
    fi
fi
