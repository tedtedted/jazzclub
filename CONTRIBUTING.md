# Contributing to jazzclub

Bug reports, documentation fixes and code contributions are welcome. For a substantial feature,
open an issue first so we can agree on its behavior and scope.

## Local development

Use JDK 25 and the checked-in Maven wrapper. GraalVM Community 25.0.2 is needed only to build the
native executable; `.sdkmanrc` pins it for SDKMAN users. Install ffmpeg to exercise both decoders.
No Pandora or Last.fm account is needed for the automated tests.

```sh
./mvnw verify
JAZZCLUB_REQUIRE_FFMPEG=true ./mvnw verify
```

Verification runs the tests, generates completion and enforces at least 80% line coverage.
`target/site/jacoco/index.html` has the coverage report. Never use real credentials in tests or
fixtures. A local `.env` is optional and contains only the application's Last.fm API credentials;
see `.env.example` and the README.

To check the native executable, including playback without a sound card:

```sh
./mvnw -Pnative native:compile
scripts/smoke-test.sh target/jazzclub
python3 scripts/smoke-test-audio.py target/jazzclub
```

The audio smoke test needs Python 3 and openssl. It uses temporary local HTTP/HTTPS servers,
dummy account responses, synthetic AAC audio and an audio FIFO. It checks decoded PCM duration
and signal level and refuses an ffmpeg fallback. While developing the test itself, use
`python3 scripts/smoke-test-audio.py --jar target/jazzclub.jar` after `./mvnw package`.

Linux package installation checks use Docker; run `scripts/test-linux-packages.sh VERSION
DEB_ARCH ARCH_ARCH dist` after building the native Linux executable and packages. Debian 12 and
Ubuntu 22.04 are tested on both architectures. The official Arch Linux container supports x86_64;
Arch ARM still requires installation testing on a real aarch64 host.

## Pull requests

- Keep changes focused and explain the user-visible problem and resulting behavior.
- Add regression coverage for bugs, especially cancellation, shutdown and network failures.
- Keep network and operating-system adapters separate from playback and application behavior.
- Preserve the CLI, config keys, key bindings and event-script contract unless the change
  explicitly calls for an incompatibility. See `docs/release.md` for versioning.
- Document changed behavior and add a user-facing entry under `Unreleased` in `CHANGELOG.md`.
  Internal maintenance needs no changelog entry.
- Run `./mvnw verify` and `scripts/changelog.sh check` before requesting review. Native or packaging
  changes also need the appropriate native smoke checks.

CI checks README/config consistency as well as Java behavior. Dependency review rejects newly
introduced high or critical vulnerabilities. GitHub Actions are pinned to commit SHAs; Dependabot
proposes updates to those pins and Maven dependencies.

## Reporting problems

Use the issue templates with your version, OS, installation method, relevant config and steps to
reproduce. Remove passwords, session keys, auth tokens and signed audio URLs before posting logs.
Security reports belong in the private channel described in `SECURITY.md`.

Contributions are licensed under the project's MIT license. Release preparation, package layouts
and maintainer procedures are documented in `docs/release.md`.
