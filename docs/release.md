# Release model

jazzclub uses SemVer and ships only from immutable Git tags.

## Versions

- Feature work happens on short-lived branches or worktrees.
- `main` is always releasable: every merge must pass tests and the native smoke checks.
- Maven keeps a development version in `pom.xml` through the `revision` property.
- CI builds release artifacts with `-Drevision=<version>`, where `<version>` comes from the Git tag.
- Public releases use tags named `vMAJOR.MINOR.PATCH`, for example `v1.2.0`.
- Pre-releases use SemVer suffixes, for example `v1.2.0-rc.1`.

Use the SemVer meanings strictly:

- `MAJOR`: incompatible config, command line, package, or behavioral changes.
- `MINOR`: compatible features and substantial user-visible improvements.
- `PATCH`: compatible bug fixes, dependency updates, packaging fixes, and documentation corrections.

## Branches

Use branches for intent, not for version lines:

- `feature/<name>` or `fix/<name>` for normal work.
- `release/vX.Y` only when a release needs hardening while `main` continues moving.
- `hotfix/vX.Y.Z` only when patching an already released line.

Avoid long-lived `major`, `minor`, and `patch` branches. The tag decides what ships; pull requests
decide what lands.

## CI/CD gates

The expected flow is:

1. Open a pull request from a branch or worktree.
2. CI runs tests and coverage.
3. Merge to `main` only when CI is green.
4. Tag a release from `main`:

   ```sh
   git tag -a v0.1.0 -m "jazzclub 0.1.0"
   git push origin v0.1.0
   ```

5. The release workflow builds native binaries, smoke tests them, packages them, creates checksums,
   and publishes a GitHub release.

## Release artifacts

Each release publishes:

- Debian packages: `jazzclub_<version>_amd64.deb`, `jazzclub_<version>_arm64.deb`
- Arch packages: `jazzclub-<version>-1-x86_64.pkg.tar.zst`, `jazzclub-<version>-1-aarch64.pkg.tar.zst`
- macOS archive: `jazzclub-<version>-macos-<arch>.tar.gz`
- `SHA256SUMS` files for each package build

The packages install only jazzclub itself. `ffmpeg` remains a runtime dependency supplied by the
operating system package manager.

## Later hardening

Before a `1.0.0` release, add:

- signed Git tags and release artifact attestations
- macOS x86_64 builds if Intel Mac support is still desired
- Homebrew tap automation
- AUR `PKGBUILD` publishing in addition to the binary Arch package
- generated changelog sections from pull request labels
