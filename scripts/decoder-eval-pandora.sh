#!/usr/bin/env bash
# Real-Pandora part of the decoder evaluation (F1 in issue #9). Decodes each track listed in a URL
# file with ffmpeg, LavaPlayer and JAAD, and compares the latter two with ffmpeg.
#
# 1. Play a few songs on the decoder-spike build with the URL log switched on:
#      JAZZCLUB_SPIKE_URL_LOG=target/decoder-eval/pandora/urls.txt java -jar target/jazzclub.jar
# 2. Soon afterwards (the URLs expire), run:
#      scripts/decoder-eval-pandora.sh target/decoder-eval/pandora/urls.txt
#
# The report, target/decoder-eval/pandora/report.md, names tracks by number only. The URL file
# grants access to the audio: keep it in target/ and delete it when done.
set -euo pipefail

urls=${1:?usage: decoder-eval-pandora.sh <url-file>}
graal=${GRAALVM_HOME:-$HOME/.sdkman/candidates/java/25.0.2-graalce}
out=target/decoder-eval/pandora
mkdir -p "$out"

JAVA_HOME=$graal ./mvnw -B -ntp -q -Pdecoder-eval test-compile
JAVA_HOME=$graal ./mvnw -B -ntp -q -Pdecoder-eval dependency:build-classpath -Dmdep.outputFile="$out/classpath.txt"
"$graal/bin/java" -Xmx4g --enable-native-access=ALL-UNNAMED \
    -cp "target/test-classes:target/classes:$(cat "$out/classpath.txt")" \
    com.tedredington.jazzclub.player.eval.PandoraComparison "$urls" "$out/report.md" 2>"$out/stderr.log" \
    | grep --line-buffered -v "://"
