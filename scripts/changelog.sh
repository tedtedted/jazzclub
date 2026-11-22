#!/usr/bin/env bash
# Reads and updates CHANGELOG.md. Used by CI, the release workflow and scripts/release.sh.
#
#   changelog.sh check             the file has an Unreleased section, and every release heading
#                                  is a SemVer version with a date and a link
#   changelog.sh notes [--rehearsal] VERSION
#                                  prints VERSION's section, for the GitHub release. A pre-release
#                                  (1.2.0-rc.1) without a section of its own prints Unreleased.
#                                  --rehearsal, for a release workflow run by hand, prints a
#                                  placeholder instead of failing when there are no notes, as
#                                  between releases, when Unreleased is empty.
#   changelog.sh release VERSION   turns Unreleased into VERSION's section, dated today
set -euo pipefail

changelog="${CHANGELOG:-CHANGELOG.md}"
repo_url="https://github.com/tedtedted/jazzclub"
semver='[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.-]+)?'

usage() {
  echo "usage: $0 check | notes [--rehearsal] VERSION | release VERSION" >&2
  exit 64
}

die() {
  echo "$changelog: $*" >&2
  exit 1
}

[[ -f "$changelog" ]] || die "not found"

# The body of the section headed "## [$1]", without its heading, its link and blank edges
section() {
  awk -v name="$1" '
    /^## \[/ { inside = index($0, "## [" name "]") == 1; next }
    /^\[[^]]+\]: / { inside = 0 }
    inside { lines[++n] = $0 }
    END {
      first = 1; while (first <= n && lines[first] ~ /^[[:space:]]*$/) first++
      last = n; while (last >= first && lines[last] ~ /^[[:space:]]*$/) last--
      for (i = first; i <= last; i++) print lines[i]
    }
  ' "$changelog"
}

has_heading() {
  grep -qE "^## \[$(sed 's/[.]/\\./g' <<< "$1")\]( |$)" "$changelog"
}

check() {
  local status=0 heading version
  grep -qE '^## \[Unreleased\]$' "$changelog" || { echo "$changelog: no '## [Unreleased]' heading" >&2; status=1; }
  grep -qE '^\[Unreleased\]: ' "$changelog" || { echo "$changelog: no [Unreleased] link" >&2; status=1; }
  while IFS= read -r heading; do
    [[ "$heading" == '## [Unreleased]' ]] && continue
    if [[ ! "$heading" =~ ^##\ \[($semver)\]\ -\ [0-9]{4}-[0-9]{2}-[0-9]{2}$ ]]; then
      echo "$changelog: expected '## [MAJOR.MINOR.PATCH] - YYYY-MM-DD', found '$heading'" >&2
      status=1
      continue
    fi
    version="${BASH_REMATCH[1]}"
    grep -qF "[$version]: " "$changelog" || { echo "$changelog: no [$version] link" >&2; status=1; }
  done < <(grep -E '^## ' "$changelog")
  return "$status"
}

notes() {
  local rehearsal="$1" version="$2" body="" problem=""
  [[ "$version" =~ ^$semver$ ]] || die "not a SemVer version: $version"
  if has_heading "$version"; then
    body="$(section "$version")"
  elif [[ "$version" == *-* ]]; then
    body="$(section Unreleased)"
  else
    problem="no '## [$version]' section; prepare the release with scripts/release.sh prepare $version"
  fi
  if [[ -z "$problem" && -z "$body" ]]; then
    problem="the section for $version is empty"
  fi
  if [[ -n "$problem" ]]; then
    [[ "$rehearsal" == true ]] || die "$problem"
    echo "$changelog: $problem; a rehearsal carries on with placeholder notes" >&2
    body="_Rehearsal of $version: CHANGELOG.md has no notes for it yet._"
  fi
  printf '%s\n' "$body"
}

release() {
  local version="$1" today previous tmp
  [[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || die "not a MAJOR.MINOR.PATCH version: $version"
  has_heading "$version" && die "already has a section for $version"
  [[ -n "$(section Unreleased)" ]] || die "nothing under Unreleased to release"
  today="$(date +%Y-%m-%d)"
  # the newest release heading, for the compare link
  previous="$(grep -oE "^## \[$semver\]" "$changelog" | head -n 1 | sed -E 's/^## \[(.*)\]$/\1/')"

  tmp="$(mktemp)"
  awk -v version="$version" -v today="$today" -v previous="$previous" -v url="$repo_url" '
    $0 == "## [Unreleased]" { print; print ""; print "## [" version "] - " today; next }
    /^\[Unreleased\]: / {
      print "[Unreleased]: " url "/compare/v" version "...HEAD"
      if (previous != "") print "[" version "]: " url "/compare/v" previous "...v" version
      else print "[" version "]: " url "/releases/tag/v" version
      next
    }
    { print }
  ' "$changelog" > "$tmp"
  mv "$tmp" "$changelog"
}

case "${1:-}" in
  check) [[ $# -eq 1 ]] || usage; check ;;
  notes)
    if [[ $# -eq 3 && "$2" == --rehearsal ]]; then notes true "$3"
    elif [[ $# -eq 2 ]]; then notes false "$2"
    else usage
    fi ;;
  release) [[ $# -eq 2 ]] || usage; release "$2" ;;
  *) usage ;;
esac
