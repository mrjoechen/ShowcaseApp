#!/bin/sh
set -eu

# Inputs: shared Gradle version, tvOS template, existing Apple build number.
version_name=$(
  sed -nE \
    's/^[[:space:]]*showcase\.versionName[[:space:]]*=[[:space:]]*([^[:space:]]+)[[:space:]]*$/\1/p' \
    "$SCRIPT_INPUT_FILE_0"
)

if ! printf '%s\n' "$version_name" | /usr/bin/grep -Eq '^[0-9]+\.[0-9]+\.[0-9]+$'; then
  echo "error: showcase.versionName must be x.y.z, got: $version_name"
  exit 1
fi

build_number=$(/usr/libexec/PlistBuddy -c 'Print :CFBundleVersion' "$SCRIPT_INPUT_FILE_2")
if ! printf '%s\n' "$build_number" | /usr/bin/grep -Eq '^[0-9]+(\.[0-9]+){0,2}$'; then
  echo "error: CFBundleVersion must be a numeric build number, got: $build_number"
  exit 1
fi

mkdir -p "$(dirname "$SCRIPT_OUTPUT_FILE_0")"
cp "$SCRIPT_INPUT_FILE_1" "$SCRIPT_OUTPUT_FILE_0"
/usr/libexec/PlistBuddy -c "Set :CFBundleShortVersionString $version_name" "$SCRIPT_OUTPUT_FILE_0"
/usr/libexec/PlistBuddy -c "Set :CFBundleVersion $build_number" "$SCRIPT_OUTPUT_FILE_0"
echo "tvOS version: $version_name ($build_number)"
