#!/bin/bash
set -euo pipefail

log() {
  echo "[sentry-dsym] $*"
}

is_release_archive_like=false
if [[ "${CONFIGURATION:-}" == "Release" ]]; then
  is_release_archive_like=true
fi
if [[ "${ACTION:-}" == "install" ]]; then
  is_release_archive_like=true
fi
if [[ -n "${ARCHIVE_PATH:-}" ]]; then
  is_release_archive_like=true
fi

if [[ "$is_release_archive_like" != true ]]; then
  log "Skip: not Release/archive action."
  exit 0
fi

find_first_file() {
  for candidate in "$@"; do
    [[ -n "$candidate" && -f "$candidate" ]] && { echo "$candidate"; return 0; }
  done
  return 1
}

sentry_binary="$(find_first_file \
  "${ARCHIVE_PRODUCTS_PATH:-}/Applications/${FULL_PRODUCT_NAME:-}/Frameworks/Sentry.framework/Sentry" \
  "${TARGET_BUILD_DIR:-}/${FRAMEWORKS_FOLDER_PATH:-}/Sentry.framework/Sentry" \
  "${BUILT_PRODUCTS_DIR:-}/PackageFrameworks/Sentry.framework/Sentry" \
  "${BUILT_PRODUCTS_DIR:-}/Sentry.framework/Sentry" \
  "${TARGET_BUILD_DIR:-}/Sentry.framework/Sentry" \
 || true)"
sentry_binary="${sentry_binary:-}"

if [[ -z "${sentry_binary:-}" ]]; then
  sentry_binary="$(find "${TARGET_BUILD_DIR:-}" "${BUILT_PRODUCTS_DIR:-}" "${ARCHIVE_PRODUCTS_PATH:-}" \
    -type f -path "*/Sentry.framework/Sentry" 2>/dev/null | head -n 1 || true)"
fi

if [[ -z "${sentry_binary:-}" || ! -f "$sentry_binary" ]]; then
  log "Sentry.framework binary not found, skip."
  exit 0
fi

required_uuids="$(dwarfdump --uuid "$sentry_binary" | awk '{print toupper($2)}' | tr '\n' ' ')"
if [[ -z "${required_uuids// }" ]]; then
  log "No UUIDs extracted from $sentry_binary, skip."
  exit 0
fi

search_roots=()
for root in \
  "${SOURCE_PACKAGES_DIR_PATH:-}" \
  "${ARCHIVE_PRODUCTS_PATH:-}" \
  "${BUILT_PRODUCTS_DIR:-}" \
  "${TARGET_BUILD_DIR:-}" \
  "${BUILD_DIR:-}"; do
  [[ -n "$root" && -d "$root" ]] && search_roots+=("$root")
done

if [[ -n "${BUILD_DIR:-}" ]]; then
  derived_data_root="${BUILD_DIR%%/Build/*}"
  if [[ -n "$derived_data_root" && -d "$derived_data_root/SourcePackages" ]]; then
    search_roots+=("$derived_data_root/SourcePackages")
  fi
fi

if [[ ${#search_roots[@]} -eq 0 ]]; then
  log "No valid search roots for Sentry dSYM."
  exit 0
fi

best_dsym=""
best_score=0
best_uuids=""

while IFS= read -r dsym; do
  dwarf_file="$dsym/Contents/Resources/DWARF/Sentry"
  [[ -f "$dwarf_file" ]] || continue

  dsym_uuids="$(dwarfdump --uuid "$dwarf_file" | awk '{print toupper($2)}')"
  [[ -n "$dsym_uuids" ]] || continue

  score=0
  for uuid in $required_uuids; do
    if echo "$dsym_uuids" | grep -q "$uuid"; then
      score=$((score + 1))
    fi
  done

  if (( score > best_score )); then
    best_score=$score
    best_dsym="$dsym"
    best_uuids="$dsym_uuids"
  fi
done < <(find "${search_roots[@]}" -type d -name "Sentry.framework.dSYM" 2>/dev/null)

if [[ -z "$best_dsym" || $best_score -eq 0 ]]; then
  log "No matching Sentry.framework.dSYM found for UUIDs: $required_uuids"
  exit 0
fi

log "Sentry binary: $sentry_binary"
log "Required UUIDs: $required_uuids"
log "Selected dSYM: $best_dsym"
log "Selected UUIDs: $best_uuids"

dest_dirs=()
[[ -n "${DWARF_DSYM_FOLDER_PATH:-}" ]] && dest_dirs+=("${DWARF_DSYM_FOLDER_PATH}")
[[ -n "${ARCHIVE_DSYMS_PATH:-}" ]] && dest_dirs+=("${ARCHIVE_DSYMS_PATH}")
[[ -n "${ARCHIVE_PATH:-}" ]] && dest_dirs+=("${ARCHIVE_PATH}/dSYMs")

if [[ ${#dest_dirs[@]} -eq 0 ]]; then
  log "No destination dSYM folders available."
  exit 0
fi

for dest_root in "${dest_dirs[@]}"; do
  if ! mkdir -p "$dest_root"; then
    log "Skip destination: cannot create $dest_root"
    continue
  fi
  target="$dest_root/$(basename "$best_dsym")"

  # In archive builds, DWARF_DSYM_FOLDER_PATH can already point to best_dsym.
  # Do not delete/copy when source and destination are identical.
  if [[ "$best_dsym" == "$target" ]]; then
    log "Skip copy: source already at destination ($target)"
    continue
  fi

  rm -rf "$target" || true
  if cp -R "$best_dsym" "$target"; then
    log "Copied dSYM -> $target"
  else
    log "Skip destination: copy failed -> $target"
  fi
done
