# Decoder evaluation (spike for issue #9)

Compares three ways of turning Pandora's HE-AAC streams into PCM, each behind jazzclub's own
`Decoder` interface:

| Candidate | Adapter |
|---|---|
| ffmpeg subprocess (what jazzclub ships) | `FfmpegDecoder` from `src/main` |
| LavaPlayer 2.2.7 (fdk-aac over JNI) | `LavaplayerDecoder` |
| JAAD via vavi-sound-aac 0.8.13 (pure Java, Java Sound plugin) | `JaadDecoder` |

Everything here, including the two libraries, exists only in the `decoder-eval` Maven profile. The
normal build, CI and the native binary don't see it.

## Running

```bash
./mvnw -Pdecoder-eval test
```

The results are in `target/decoder-eval/report.md`. A failed check is a result, not a broken build,
so Maven still exits 0. ffmpeg must be on the PATH.

## Fixtures

`resources/audio` holds synthetic, licence-free signals made by `scripts/make-audio-fixtures.sh`.
It needs macOS, because Apple's `afconvert` is the HE-AAC encoder at hand. Real Pandora audio never
goes in the repo.

- `lc-1k.m4a`: AAC-LC, 1 kHz at -12 dBFS. A sanity check for level and length.
- `he-noise.m4a`, `.aac`, `-moov-last.m4a`: HE-AAC v1 white noise. The same stream is stored three
  ways: as M4A (explicit SBR), as ADTS (implicit SBR) and with the MP4 index at the end.
- `hev2-panned.m4a`: HE-AAC v2, 3 kHz on the left and 5 kHz on the right. Tests parametric stereo.
- `truncated.m4a`, `empty.m4a`, `garbage.m4a`: broken inputs.

Why noise and not a tone for SBR: SBR rebuilds 11-16 kHz from the low band's content. White noise
comes back flat if SBR is decoded and vanishes above ~11 kHz if it isn't. A lone high tone
disappears even in a correct decoder, because the low band holds nothing to rebuild it from.

## Harness notes

- `FixtureServer` serves fixtures over localhost HTTP and can throttle, drop the connection, die
  with 503s after a drop, or ignore range requests. It sends `Connection: close` because ffmpeg,
  with keep-alive, fails to open a small ADTS file against the JDK's server but not against others.
- The LavaPlayer adapter waits 250 ms after the end of a track for an error event. LavaPlayer
  reports `FINISHED` before the exception that ended the track.
- The JAAD adapter byte-swaps, because the plugin only writes big-endian PCM. It also makes network
  reads wait for 32 KiB, because the plugin detects the format from `in.available()` bytes only.

## Native image (D1/D2)

```bash
scripts/decoder-eval-native.sh [ffmpeg|lavaplayer|jaad ...]
```

Each candidate has its own entry point (`FfmpegDecodeTool`, `LavaplayerDecodeTool`,
`JaadDecodeTool`), and each of those can reach only its own decoder. The size difference between
the binaries is therefore each decoder's cost; the ffmpeg tool is the baseline.

For each candidate, the script:
1. Runs the tool on the JVM under GraalVM's tracing agent, which writes the metadata the candidate
   needs.
2. Builds a native binary with `-Os`, as jazzclub does.
3. Decodes the same fixtures with the binary. The measurements must match the JVM's exactly.

The results are in `target/decoder-eval/native/report.md`. The script only runs on macOS for now,
because it reads memory use from `/usr/bin/time -l`.
