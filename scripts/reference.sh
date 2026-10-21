#!/usr/bin/env bash
# Manages the read-only reference libraries under reference/ (git submodules).
#
#   scripts/reference.sh init      fetch the pinned commits (after cloning Kexcel)
#   scripts/reference.sh update    move POI to the tag matching libs.versions.toml and
#                                  Dart excel to its latest main, then show what changed
#
# Meant to be run by a human. Commit the new pointers afterwards; until then the
# session guard (.claude/hooks/check-reference-untouched.sh) reports them to Claude.
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

poi_tag() {
  local version
  version="$(sed -n 's/^poi *= *"\(.*\)"/\1/p' gradle/libs.versions.toml)"
  echo "REL_${version//./_}"
}

case "${1:-}" in
  init)
    git submodule update --init --depth 1 -- reference
    ;;
  update)
    tag="$(poi_tag)"
    git -C reference/poi fetch --depth 1 origin tag "$tag"
    git -C reference/poi -c advice.detachedHead=false checkout -q "$tag"
    git submodule update --remote --depth 1 -- reference/dart-excel
    git add reference
    echo "POI is now at $tag. Staged pointer changes:"
    git diff --cached --submodule=log -- reference
    echo "Review, then: git commit -m 'chore: bump reference libraries'"
    ;;
  *)
    echo "usage: $0 init|update" >&2
    exit 64
    ;;
esac
