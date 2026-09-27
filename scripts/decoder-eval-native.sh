#!/usr/bin/env bash
# Native-image part of the decoder evaluation (D1/D2 in issue #9). For each candidate:
#   1. run its DecodeTool on the JVM under GraalVM's tracing agent, which writes the reachability
#      metadata the candidate needs (JNI, reflection, resources): the integration cost;
#   2. build a native binary from it with jazzclub's size settings;
#   3. decode the same fixtures with the binary; the measurements must match the JVM's exactly.
# Writes target/decoder-eval/native/report.md. macOS only for now (/usr/bin/time -l).
#
#   decoder-eval-native.sh [candidate...]     candidates: ffmpeg lavaplayer jaad (default: all)
set -euo pipefail

graal=${GRAALVM_HOME:-$HOME/.sdkman/candidates/java/25.0.2-graalce}
out=target/decoder-eval/native
candidates=("${@:-ffmpeg lavaplayer jaad}")
read -r -a candidates <<< "${candidates[*]}"
mkdir -p "$out"

JAVA_HOME=$graal ./mvnw -B -ntp -q -Pdecoder-eval test-compile
JAVA_HOME=$graal ./mvnw -B -ntp -q -Pdecoder-eval dependency:build-classpath -Dmdep.outputFile="$out/classpath.txt"
cp="target/test-classes:target/classes:$(cat "$out/classpath.txt")"

port=$((20000 + RANDOM % 20000))
python3 -m http.server "$port" --bind 127.0.0.1 --directory src/decoder-eval/resources/audio \
    > /dev/null 2>&1 &
server=$!
trap 'kill $server 2>/dev/null' EXIT
sleep 1
urls=()
for f in lc-1k.m4a he-noise.m4a he-noise.aac hev2-panned.m4a; do
    urls+=("http://127.0.0.1:$port/$f")
done

# the measurement lines, without the timings (which differ from run to run) or log output
measurements() { grep -E "^$2 " "$1" | sed -E 's/ firstMs=[0-9-]+ totalMs=[0-9-]+//'; }

report=$out/report.md
{
    echo "| Candidate | Binary | Build | Metadata (JNI / reflection / resources) | Max RSS | Same as JVM |"
    echo "|---|---|---|---|---|---|"
} > "$report"

for c in "${candidates[@]}"; do
    main=com.tedredington.jazzclub.player.eval.$(tr '[:lower:]' '[:upper:]' <<< "${c:0:1}")${c:1}DecodeTool
    config=$out/$c-config
    rm -rf "$config"
    echo "== $c: JVM run with tracing agent"
    "$graal/bin/java" -agentlib:native-image-agent=config-output-dir="$config" -cp "$cp" "$main" "${urls[@]}" \
        | tee "$out/$c-jvm.txt"

    echo "== $c: native-image"
    start=$(date +%s)
    if ! "$graal/bin/native-image" -Os --no-fallback --enable-native-access=ALL-UNNAMED \
            -H:+UnlockExperimentalVMOptions -H:ConfigurationFileDirectories="$config" \
            -cp "$cp" -o "$out/$c" "$main" > "$out/$c-build.log" 2>&1; then
        tail -20 "$out/$c-build.log"
        echo "| $c | build failed, see $c-build.log | | | | |" >> "$report"
        continue
    fi
    seconds=$(( $(date +%s) - start ))

    echo "== $c: native run"
    /usr/bin/time -l "$out/$c" "${urls[@]}" > "$out/$c-native.txt" 2> "$out/$c-time.txt" || true
    cat "$out/$c-native.txt"
    size=$(ls -l "$out/$c" | awk '{printf "%.1f MB", $5 / 1048576}')
    rss=$(awk '/maximum resident set size/ {printf "%.0f MB", $1 / 1048576}' "$out/$c-time.txt")
    metadata=$(python3 - "$config/reachability-metadata.json" <<'EOF'
import json, sys
m = json.load(open(sys.argv[1]))
refl = m.get("reflection", [])
jni = [e for e in refl if e.get("jniAccessible")] + m.get("jni", [])
res = m.get("resources", [])
res = res.get("includes", []) if isinstance(res, dict) else res
print(f"{len(jni)} / {len(refl) - len([e for e in refl if e.get('jniAccessible')])} / {len(res)}")
EOF
)
    if [[ $c == ffmpeg ]]; then
        # /usr/bin/time counts the binary, not the ffmpeg child it starts: measure that separately
        /usr/bin/time -l ffmpeg -nostdin -v error -i "${urls[1]}" -f s16le -y /dev/null 2> "$out/ffmpeg-child-time.txt"
        rss="$rss + $(awk '/maximum resident set size/ {printf "%.0f MB", $1 / 1048576}' "$out/ffmpeg-child-time.txt") ffmpeg"
    fi
    if [[ $(measurements "$out/$c-native.txt" "$c" | wc -l) -gt 0 ]] \
            && diff <(measurements "$out/$c-jvm.txt" "$c") <(measurements "$out/$c-native.txt" "$c") > "$out/$c-diff.txt"; then
        same=yes
    else
        same="no, see $c-diff.txt"
    fi
    echo "| $c | $size | ${seconds} s | $metadata | $rss | $same |" >> "$report"
done

echo
cat "$report"
