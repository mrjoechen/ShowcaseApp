#!/bin/sh
set -eu

case "$PLATFORM_NAME" in
  appletvos) kotlin_target=tvosArm64 ;;
  appletvsimulator) kotlin_target=tvosSimulatorArm64 ;;
  *) echo "error: Unsupported tvOS platform: $PLATFORM_NAME"; exit 1 ;;
esac

resources="$SRCROOT/../composeApp/build/generated/compose/resourceGenerator/assembledResources/${kotlin_target}Main/composeResources"
if [ ! -d "$resources" ]; then
  echo "error: Compose resources are missing: $resources"
  echo "error: Build the $kotlin_target Compose framework before packaging the tvOS app."
  exit 1
fi

# The tvOS resource reader expects both directory levels, including camelCase.
destination="${TARGET_BUILD_DIR:?}/${UNLOCALIZED_RESOURCES_FOLDER_PATH:?}/compose-resources/composeResources"
mkdir -p "$destination"
/usr/bin/rsync -a --delete "$resources/" "$destination/"
