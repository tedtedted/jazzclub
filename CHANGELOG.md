# Changelog

What changed in each jazzclub release, written for the people who use it. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/); [docs/release.md](docs/release.md) explains both.

Add a line under `Unreleased` in every pull request that changes something a user would notice.

## [Unreleased]

## [0.2.1] - 2026-10-06

### Fixed

- Pausing near the end of a song no longer leaves the audio prefetch thread consuming a CPU core.
- Event scripts that do not read their input are timed out even when the event payload fills the pipe,
  so later events can still be delivered.
- A changed native sound library is refreshed on upgrade even when its file size is unchanged.
- Command-line help correctly describes ffmpeg as optional.
- Arch Linux packages now place their metadata where pacman expects it, fixing installation failures.
- Audio pipe output now drains the feeder before closing it, preserving the end of each track.

### Changed

- Release validation now exercises built-in AAC playback through the native executable and tests
  installed packages on Debian 12, Ubuntu 22.04 and Arch Linux x86_64.
- Release packages include GitHub build provenance attestations for download verification.

## [0.2.0] - 2026-10-02

### Added

- A built-in AAC decoder, now the default: Pandora's streams play without ffmpeg.
  `decoder = ffmpeg` switches back, and jazzclub falls back to ffmpeg by itself, and says so, when
  the built-in decoder cannot load.
- Last.fm scrobbling, set up in the config file with `lastfm_user` and `lastfm_password_command`.
  Last.fm shows each song as it starts and scrobbles it by Last.fm's own rules when it ends.
- Liking a song (`+`) also loves it on Last.fm; banning it (`-`) removes the love. Songs liked
  earlier, anywhere, are loved on Last.fm the next time they play.
- A Homebrew tap: `brew install tedtedted/tap/jazzclub` on Apple Silicon Macs.

### Changed

- ffmpeg is optional in the Debian and Arch packages. Only `decoder = ffmpeg`, a `sample_rate`
  other than 44100, or the fallback need it.
- The packages ship the licences of the decoder's native library.
- A server that has stopped answering is given up on after 5 seconds instead of 2 minutes.

### Fixed

- Songs no longer stop about 100 seconds in with `Decoding failed: Socket is not connected`. The
  built-in decoder downloads each song in full as it starts, so a dropped connection no longer
  ends it.
- With `decoder = ffmpeg`, a song cut off by the server is reported as failed instead of counted as
  played.

## [0.1.0] - 2026-09-20

Tagged, but its release build failed and it was never published.

### Added

- Pandora playback with pianobar's keys, config file format, format strings and event scripts.
- Station management (create, edit, QuickMix, bookmarks) and account settings (`!`).
- Song history, a remote control fifo, and volume and station remembered between runs.
- `proxy`, `control_proxy`, `bind_to`, `ca_bundle`, `buffer_seconds`, `sample_rate` and
  `audio_pipe` settings.
- Packages for Debian 12 and Ubuntu 22.04 or newer (amd64, arm64), Arch Linux (x86_64, aarch64)
  and macOS (Apple Silicon), with bash completion.

[Unreleased]: https://github.com/tedtedted/jazzclub/compare/v0.2.1...HEAD
[0.2.1]: https://github.com/tedtedted/jazzclub/compare/v0.2.0...v0.2.1
[0.2.0]: https://github.com/tedtedted/jazzclub/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/tedtedted/jazzclub/releases/tag/v0.1.0
