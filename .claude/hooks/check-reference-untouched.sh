#!/usr/bin/env bash
# PostToolUse guard: the read-only reference checkouts under reference/ must match the
# commits this repo pins. Any edit, stray file, checkout or staged pointer bump made from
# inside a Claude session is reported back to Claude (exit 2) so it cannot go unnoticed.
#
# A human who bumps a reference on purpose (scripts/reference.sh update) commits the new
# pointer; once it is in HEAD this check passes again.
set -u
root="$(git -C "${CLAUDE_PROJECT_DIR:-.}" rev-parse --show-toplevel 2>/dev/null)" || exit 0
cd "$root" || exit 0
[ -d reference ] || exit 0

problems=()

# '+' = the checked-out commit is not the one recorded; 'U' = merge conflict.
while read -r line; do
  case "$line" in
    +*|U*) problems+=("checked out at a commit other than the pinned one: ${line#?}") ;;
  esac
done < <(git submodule status -- reference 2>/dev/null)

for dir in reference/*/; do
  dir="${dir%/}"
  [ -e "$dir/.git" ] || continue # not initialised in this checkout
  if [ -n "$(git -C "$dir" status --porcelain 2>/dev/null)" ]; then
    problems+=("$dir has local changes:\n$(git -C "$dir" status --porcelain | head -20)")
  fi
  # A staged or committed-but-unpushed pointer change against HEAD. Skipped for a
  # reference HEAD does not have yet (the commit that first adds it).
  if git ls-tree --name-only HEAD -- "$dir" | grep -q . \
    && ! git diff --quiet HEAD -- "$dir"; then
    problems+=("the pinned commit of $dir differs from HEAD")
  fi
done

if git ls-tree --name-only HEAD -- .gitmodules | grep -q . && ! git diff --quiet HEAD -- .gitmodules; then
  problems+=(".gitmodules differs from HEAD")
fi

[ ${#problems[@]} -eq 0 ] && exit 0

{
  echo "BLOCKED: the reference libraries under reference/ are read-only oracles and must not change."
  for p in "${problems[@]}"; do printf ' - %b\n' "$p"; done
  echo "Undo whatever caused this (e.g. 'git -C reference/<lib> checkout -- . && git -C reference/<lib> clean -fd',"
  echo "or 'git submodule update -- reference'), then continue. Do not edit reference code, tests' expected"
  echo "values or docs to make Kexcel pass; fix Kexcel. If a human bumped a reference on purpose, ask them to commit it."
} >&2
exit 2
