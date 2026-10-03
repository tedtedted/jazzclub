# Release model

jazzclub uses [Semantic Versioning](https://semver.org/), records every user-visible change in
[CHANGELOG.md](../CHANGELOG.md), and ships only from immutable Git tags on `main`.

## Schedule

Pull requests land on `main` whenever they are ready. Releases are cut separately, when there is
something worth shipping. There is no fixed calendar.

- **Minor release** (`0.3.0`): once one or more user-visible features or improvements have landed.
  Expect one every four to eight weeks while features are still arriving.
- **Patch release** (`0.2.1`): within a few days of a fix for a bug users hit. A change on Pandora's
  side that stops playback is the most urgent case; ship that fix as soon as it is merged.
- **Pre-release** (`0.3.0-rc.1`): only when a change is risky enough to want testers first, such as a
  new decoder or a packaging overhaul. It is published as a GitHub pre-release; the package managers
  never see it.
- **No release** for changes users cannot notice, such as tests, CI or refactoring. They go out with
  the next release that has a reason to exist.

## Version numbers

The public interface that the numbers protect is what a user touches: config file keys and values,
command line options, key bindings, the event script and remote control interfaces, package names
and install locations.

- `MAJOR`: an incompatible change to that interface. Before `1.0.0` these go in a minor release,
  as SemVer allows, and the changelog says so under **Changed** or **Removed**.
- `MINOR`: compatible features and noticeable improvements.
- `PATCH`: compatible bug fixes, dependency updates and packaging fixes.

`1.0.0` comes once the config keys and command line options are settled. From then on, an
incompatible change needs a new major version.

`pom.xml` holds the next planned version as a `-SNAPSHOT` in its `revision` property, so a local
build reports `0.3.0-SNAPSHOT` rather than a released version. Release builds replace it with
`-Drevision=<version>` taken from the tag.

## Changelog

[CHANGELOG.md](../CHANGELOG.md) follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). It
is written by hand, for users: what changed for them, not which classes moved.

- A pull request that changes something a user would notice adds a line under `## [Unreleased]`, in
  **Added**, **Changed**, **Deprecated**, **Removed**, **Fixed** or **Security**.
- Internal changes add nothing.
- Preparing a release (below) renames `Unreleased` to the version and date. The release workflow
  publishes that section as the GitHub release notes, and stops before building anything if the
  section is missing.

CI runs `scripts/changelog.sh check` to keep the headings and links in the shape the release relies
on.

## Cutting a release

1. **Prepare.** From a clean checkout:

   ```sh
   scripts/release.sh prepare 0.2.0
   ```

   On a new branch `prepare-v0.2.0` from `origin/main`, this dates the `Unreleased` section as
   `0.2.0` and sets `pom.xml` to the next minor `-SNAPSHOT`. Read the notes as a user would and
   tidy them up, then push the branch and open the pull request it suggests.

2. **Merge** that pull request once CI is green.

3. **Tag.**

   ```sh
   scripts/release.sh tag 0.2.0
   ```

   This checks that `origin/main` has the `0.2.0` section, tags it `v0.2.0` and pushes the tag.

4. **The release workflow** refuses a tag whose commit is not on `main`, checks the changelog, runs
   the tests, builds native binaries with the tag's version baked in, smoke tests them with
   `scripts/smoke-test.sh` (the same script CI uses), packages them, writes one `SHA256SUMS` and
   publishes the GitHub release with the changelog section as its notes.

For a pre-release, skip step 1 and tag directly: `scripts/release.sh tag 0.3.0-rc.1`. Its notes are
the current `Unreleased` section.

To rehearse the whole workflow without publishing, run it by hand. Only the final step, creating
the release, is skipped; the packages, checksums and notes are uploaded as a workflow artifact to
inspect:

```sh
gh workflow run release.yml --ref <branch> -f version=0.0.0-test
```

### When a release goes wrong

Never move or reuse a tag that has been pushed; someone may have downloaded what it built. Fix
forward with the next patch version. If a published release is broken, mark it as a pre-release on
GitHub so that "latest" points at the previous one, then ship the fix.

## Branches

Use branches for intent, not for version lines:

- `feature/<name>` or `fix/<name>` for normal work, `prepare-vX.Y.Z` for a release.
- `release/vX.Y` only when a release needs hardening while `main` keeps moving.
- `hotfix/vX.Y.Z` only when patching a release line that `main` has moved past.

Avoid long-lived `major`, `minor` and `patch` branches. The tag decides what ships; pull requests
decide what lands.

## Release artifacts

Each release publishes:

- Debian packages: `jazzclub_<version>_amd64.deb`, `jazzclub_<version>_arm64.deb`
- Arch packages: `jazzclub-<version>-1-x86_64.pkg.tar.zst`, `jazzclub-<version>-1-aarch64.pkg.tar.zst`
- macOS archive: `jazzclub-<version>-macos-<arch>.tar.gz`
- `SHA256SUMS`, covering all of the above

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

## Still to come

- A Homebrew tap (`brew install tedtedted/tap/jazzclub`), updated by the release workflow
- `jazzclub-bin` on the AUR, from the PKGBUILD in tedtedted/jazzclub#3, also updated by the workflow
- Plain Linux tarballs (also in #3), for Homebrew on Linux and other distributions
- A tag ruleset so only maintainers can push `v*` tags, immutable releases, and build provenance
  attestations
- macOS signing and notarization, if direct downloads should open without the quarantine step
