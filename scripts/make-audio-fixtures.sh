#!/usr/bin/env bash
# Generates the synthetic AAC fixtures for the decoder evaluation (issue #9). Every signal is made
# here, so the files are licence-free and small enough to commit. Real Pandora audio never goes in
# the repo.
#
# Needs macOS (afconvert is Apple's encoder, the only one at hand that writes HE-AAC v1 and v2) and
# ffmpeg (test signals and remuxing only).
#
#   make-audio-fixtures.sh [output-dir]
set -euo pipefail

out=${1:-src/decoder-eval/resources/audio}
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
mkdir -p "$out"

signal() { # name, lavfi filter graph producing 44.1 kHz stereo
    ffmpeg -nostdin -loglevel error -y -f lavfi -i "$2" -ar 44100 -ac 2 -c:a pcm_s16le "$work/$1.wav"
}

# Every signal is written per channel ("left|right"): a mono signal upmixed to stereo would come out
# 3 dB quieter. Tones are -12 dBFS (amplitude 0.25), so no encoder ever clips.
signal tone-1k "aevalsrc=0.25*sin(2*PI*1000*t)|0.25*sin(2*PI*1000*t):s=44100:d=3"
# 3 kHz only on the left, 5 kHz only on the right: parametric stereo must keep them apart.
signal panned  "aevalsrc=0.25*sin(2*PI*3000*t)|0.25*sin(2*PI*5000*t):s=44100:d=3"
# White noise, 8 s (both channels come out identical: ffmpeg seeds random(0) and random(1) the same). SBR rebuilds the high band from the low band's
# content, so broadband noise is the signal that shows whether a decoder applies SBR: without it,
# nothing is left above ~11 kHz (HE-AAC at 44.1 kHz codes its core at 22.05 kHz). A lone tone up
# there would not work, because SBR has no low-band content to rebuild it from.
signal noise   "aevalsrc=0.25*(2*random(0)-1)|0.25*(2*random(1)-1):s=44100:d=8"

afconvert -f m4af -d aac  -b 128000 "$work/tone-1k.wav" "$out/lc-1k.m4a"
afconvert -f m4af -d aach -b 64000  "$work/noise.wav"   "$out/he-noise.m4a"
afconvert -f m4af -d aacp -b 32000  "$work/panned.wav"  "$out/hev2-panned.m4a"

# The same HE-AAC stream as ADTS: its headers say AAC-LC and the decoder has to find SBR on its own
# ("implicit signalling", open upstream bugs in JAAD).
ffmpeg -nostdin -loglevel error -y -i "$out/he-noise.m4a" -c copy -f adts "$out/he-noise.aac"
# The same again with the MP4 index (moov) after the audio, as a plain remux writes it.
ffmpeg -nostdin -loglevel error -y -i "$out/he-noise.m4a" -c copy -movflags -faststart \
    "$out/he-noise-moov-last.m4a"

# Broken inputs
head -c 16000 "$out/he-noise.m4a" > "$out/truncated.m4a"
: > "$out/empty.m4a"
head -c 4000 /dev/urandom > "$out/garbage.m4a"

ls -l "$out"
