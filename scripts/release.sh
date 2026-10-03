#!/usr/bin/env bash
# The two hands-on steps of a release; docs/release.md has the whole process.
#
#   release.sh prepare VERSION   on a new branch from origin/main: moves Unreleased in CHANGELOG.md
#                                to VERSION and sets pom.xml to the next minor -SNAPSHOT. Commits;
#                                you push and open the pull request.
#   release.sh tag VERSION       once that pull request is merged: tags origin/main as vVERSION and
#                                pushes the tag, which starts the release workflow.
set -euo pipefail

usage() {
  echo "usage: $0 prepare VERSION | tag VERSION" >&2
  echo "  VERSION is MAJOR.MINOR.PATCH, or MAJOR.MINOR.PATCH-rc.N for tag" >&2
  exit 64
}

die() {
  echo "$*" >&2
  exit 1
}

[[ $# -eq 2 ]] || usage
command="$1"
version="${2#v}"

cd "$(git rev-parse --show-toplevel)"
[[ -z "$(git status --porcelain)" ]] || die "the working tree has uncommitted changes"
git fetch --quiet --tags origin main
git rev-parse --quiet --verify "refs/tags/v$version" > /dev/null && die "v$version is already tagged"

case "$command" in
  prepare)
    [[ "$version" =~ ^([0-9]+)\.([0-9]+)\.[0-9]+$ ]] || die "prepare takes MAJOR.MINOR.PATCH, not $version"
    next="${BASH_REMATCH[1]}.$((BASH_REMATCH[2] + 1)).0-SNAPSHOT"
    branch="prepare-v$version"

    git switch --quiet -c "$branch" origin/main
    scripts/changelog.sh release "$version"
    scripts/changelog.sh check
    # only the project's own <revision>, which is the one property of that name
    sed -i.bak -E "s|<revision>[^<]*</revision>|<revision>$next</revision>|" pom.xml && rm pom.xml.bak
    grep -q "<revision>$next</revision>" pom.xml || die "could not set <revision> in pom.xml"

    git commit --quiet -m "Prepare jazzclub $version" -- CHANGELOG.md pom.xml
    echo "On $branch: CHANGELOG.md has a $version section, pom.xml is now $next."
    echo "Review the notes, then:"
    echo "  git push -u origin $branch && gh pr create --fill"
    echo "Once it is merged:"
    echo "  scripts/release.sh tag $version"
    ;;
  tag)
    [[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.-]+)?$ ]] || die "not a SemVer version: $version"
    # the release workflow makes the same check; failing here saves a round trip
    main_changelog="$(mktemp)"
    trap 'rm -f "$main_changelog"' EXIT
    git show origin/main:CHANGELOG.md > "$main_changelog"
    CHANGELOG="$main_changelog" scripts/changelog.sh notes "$version" > /dev/null 2>&1 \
      || die "origin/main is not ready for $version; merge its prepare pull request first"

    git tag -a "v$version" -m "jazzclub $version" origin/main
    git push origin "v$version"
    echo "Tagged $(git rev-parse --short "v$version^{commit}") as v$version. The release workflow takes it from here:"
    echo "  gh run watch \$(gh run list --workflow release.yml --limit 1 --json databaseId --jq '.[0].databaseId')"
    ;;
  *) usage ;;
esac
