#!/usr/bin/env bash

# Check and optionally update the local repositories under references/.
# References are local, read-only copies and are intentionally ignored by the
# main repository. This script never changes the parent repository.

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
REFERENCES_DIR="$ROOT_DIR/references"
MODE="prompt"

usage() {
  cat <<'USAGE'
Usage: ./scripts/update-references.sh [--check|--pull]

Options:
  --check  Inspect local status without contacting remotes.
  --pull   Fetch and fast-forward every reference that has upstream changes.
           A non-fast-forward update is left untouched.
  -h, --help
USAGE
}

fail() {
  echo "error: $*" >&2
  exit 1
}

case "${1:-}" in
  "") ;;
  --check) MODE="check" ;;
  --pull) MODE="pull" ;;
  -h|--help) usage; exit 0 ;;
  *) echo "Unknown option: $1" >&2; usage >&2; exit 2 ;;
esac

if [[ ! -d "$REFERENCES_DIR" ]]; then
  echo "references/ does not exist; nothing to update."
  exit 0
fi

found=0
for repository in "$REFERENCES_DIR"/*; do
  [[ -e "$repository/.git" ]] || continue
  found=1
  name="${repository#"$REFERENCES_DIR"/}"
  branch="$(git -C "$repository" branch --show-current)"
  remote="$(git -C "$repository" remote get-url origin 2>/dev/null || true)"

  echo "=== $name ==="
  if [[ -z "$branch" ]]; then
    echo "  detached HEAD; skipped"
    continue
  fi
  echo "  branch: $branch"

  if [[ -z "$remote" ]]; then
    fail "$name has no origin remote"
  fi
  echo "  origin: $remote"

  if [[ "$MODE" == "check" ]]; then
    echo "  local: $(git -C "$repository" log -1 --format='%h %s')"
    continue
  fi

  git -C "$repository" fetch --prune origin
  upstream="$(git -C "$repository" rev-parse --abbrev-ref --symbolic-full-name '@{upstream}' 2>/dev/null || true)"
  if [[ -z "$upstream" ]]; then
    candidate="origin/$branch"
    if git -C "$repository" show-ref --verify --quiet "refs/remotes/$candidate"; then
      upstream="$candidate"
    else
      fail "$name has no upstream branch or origin/$branch remote-tracking branch"
    fi
  fi

  behind="$(git -C "$repository" rev-list --count "HEAD..$upstream")"
  ahead="$(git -C "$repository" rev-list --count "$upstream..HEAD")"
  echo "  ahead/behind: $ahead/$behind"

  if (( behind == 0 )); then
    echo "  up to date"
    continue
  fi

  if (( ahead != 0 )); then
    echo "  local commits diverge from upstream; skipped"
    continue
  fi

  should_pull=0
  if [[ "$MODE" == "pull" ]]; then
    should_pull=1
  elif [[ -t 0 ]]; then
    read -r -p "  Fast-forward $name by $behind commit(s)? [y/N] " answer
    [[ "$answer" =~ ^[Yy]$ ]] && should_pull=1
  fi

  if (( should_pull == 1 )); then
    git -C "$repository" merge --ff-only "$upstream"
    echo "  updated: $(git -C "$repository" log -1 --format='%h %s')"
  else
    echo "  update available; skipped"
  fi
done

if (( found == 0 )); then
  echo "No Git repositories found under references/."
fi
