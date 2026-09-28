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

5. The release workflow refuses a tag whose commit is not on `main`, runs the tests, builds native
   binaries with the tag's version baked in, smoke tests them with `scripts/smoke-test.sh` (the same
   script CI uses), packages them, creates checksums, and publishes a GitHub release.

To rehearse all of that without publishing, run the workflow by hand; the publish job is skipped:

```sh
gh workflow run release.yml --ref <branch> -f version=0.0.0-test
```

## Release artifacts

Each release publishes:

- Debian packages: `jazzclub_<version>_amd64.deb`, `jazzclub_<version>_arm64.deb`
- Arch packages: `jazzclub-<version>-1-x86_64.pkg.tar.zst`, `jazzclub-<version>-1-aarch64.pkg.tar.zst`
- macOS archive: `jazzclub-<version>-macos-<arch>.tar.gz`
- `SHA256SUMS` files for each package build

The packages install jazzclub, the built-in decoder's native library `libconnector` (to
`/usr/lib/jazzclub/`, or `libexec/jazzclub/` in the macOS archive), its documentation, bash completion,
and the licence texts from `licenses/`. The native build stages `libconnector` beside `target/jazzclub`;
a packaging script stops if it is missing. Runtime dependencies are left to the operating system's
package manager and declared in the packages: on Linux the ALSA library (`libasound2` on Debian,
`alsa-lib` on Arch), which the Java Sound code inside the binary loads, and libstdc++ (`libstdc++6`,
`gcc-libs`), which `libconnector` needs. `ffmpeg` is only suggested (Debian) or optional (Arch): the
built-in decoder does not need it.

The Linux binaries are built on Ubuntu 22.04 on purpose. A binary only starts on a glibc at least as
new as the one it was linked against, and 22.04's glibc 2.35 is older than Debian 12's 2.36. Moving the
build to a newer runner silently drops Debian stable; the smoke test prints the glibc each binary needs.

## Later hardening

Before a `1.0.0` release, add:

- signed Git tags and release artifact attestations
- macOS x86_64 builds if Intel Mac support is still desired
- Homebrew tap automation
- AUR `PKGBUILD` publishing in addition to the binary Arch package
- generated changelog sections from pull request labels
