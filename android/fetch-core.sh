#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd "$(dirname "$0")/.." && pwd)"
source "$project_dir/android/version.properties"
core_dir="${1:-$project_dir/magpie-core}"
if [[ -f "$core_dir/main.go" ]]; then exit 0; fi
if [[ -e "$core_dir" ]] && [[ -n "$(ls -A "$core_dir")" ]]; then
  echo 'Core directory is not empty and has no main.go; choose CORE_SOURCE_DIR.' >&2
  exit 1
fi
git clone --depth 1 --filter=blob:none --branch "v$CORE_VERSION" https://github.com/yetone/magpie.git "$core_dir"
actual="$(git -C "$core_dir" rev-parse HEAD)"
if [[ "$actual" != "$CORE_COMMIT" ]]; then echo 'Core commit does not match the pinned version.' >&2; exit 1; fi
