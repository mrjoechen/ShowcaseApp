# iOS YuNet

The supported detection platforms are Android, desktop and iOS. JS/Wasm are
deliberately unsupported and do not load OpenCV or a model.

## Integration

`createFaceInspector()` on iOS runs the native bridge on `Dispatchers.Default`.
The pinned input bytes stay alive until the synchronous C call returns. A C++
mutex protects lazy initialization and reuse of `cv::FaceDetectorYN`; exceptions,
invalid encodings and unexpected output are mapped to `INDETERMINATE`. Coroutine
cancellation propagates before or after native execution.

`FaceDetectionBridge.cpp` uses the same `2026may` model, score `0.6`, NMS `0.3`,
top-K `5000`, 640-pixel maximum edge and visible-bounds filter as Android/desktop.
Input is decoded as BGR without applying EXIF rotation: the summary manager
already supplies a frozen, oriented, metadata-free image. The iOS tests include
Lena, a 320×320 blank PNG, a 1280×720 blank JPEG, malformed input, concurrency and
cancellation.

## Building on macOS

Requires Xcode with the iOS and iOS Simulator SDKs selected via `xcode-select`,
Python 3, and the parent project's JDK/Android SDK configuration. Supported native
targets are `iosArm64` and `iosSimulatorArm64` (Apple Silicon simulator).

Gradle IDE import (`prepareKotlinIdeaImport`) is also supported on Windows/Linux:
the native build tasks have an explicit macOS execution guard because IDE import
can schedule dependencies even when Kotlin has disabled the Apple C interop
tasks. Non-macOS import skips native compilation; it does not use `--prepare-only`
or download OpenCV. Building the iOS binaries still requires macOS/Xcode.
Verified on Windows with `gradlew.bat prepareKotlinIdeaImport`: both native build
tasks and both C interop tasks were skipped, and the whole-project import passed
(`build/face-privacy-sync-after.log`). Before the guard, the module import
reproduced the reported Darwin/Xcode error (`build/face-privacy-sync-before.log`).

The existing Xcode `Compile Kotlin Framework` phase already calls Gradle, so no
manual framework/resource registration or CocoaPods setup is needed. For each
requested target, Gradle:

1. Runs `scripts/build_ios.py` before C interop generation.
2. Downloads and verifies the pinned OpenCV XCFramework at build time. It selects
   the matching platform/architecture using the real `Info.plist`, not directory
   name assumptions. A cached ZIP avoids subsequent downloads.
3. Verifies the bundled ONNX size and SHA-256 and generates a C++ byte array.
4. Compiles the bridge with `xcrun clang++` and produces `libShowcaseFaceDetection.a`;
   extracts the arm64 static OpenCV archive as `libopencv2.a`.
5. Packages both archives into the C interop library. Native tests link the system
   libraries listed in `faceDetection.def`; the static `ComposeApp` consumer uses
   matching flags in `iosApp/Configuration/Config.xcconfig`.

There is no model/runtime download when the application runs. Build preparation
fails on missing/corrupt dependencies rather than shipping a dummy iOS detector.

From the repository root on macOS:

```sh
bash ./gradlew :local-face-detection:iosSimulatorArm64Test \
  :composeApp:linkDebugFrameworkIosSimulatorArm64 \
  :composeApp:linkReleaseFrameworkIosArm64
```

For a full application link, build the existing `iosApp/iosApp.xcodeproj` in Xcode
for the desired simulator/device. An unsigned simulator build can also run with:

```sh
xcodebuild -project iosApp/iosApp.xcodeproj -target iosApp \
  -configuration Debug -sdk iphonesimulator \
  ARCHS=arm64 ONLY_ACTIVE_ARCH=YES CODE_SIGNING_ALLOWED=NO build
```

The local GitHub workflow `.github/workflows/face-detection-ios.yml` adds simulator
tests, both framework link checks and the final unsigned simulator application
link when this change is included in a pull request or manually dispatched.
It was added as configuration; it has not been dispatched from this session.

## Reproducible dependencies

- OpenCV version: `4.12.0`, distributed by the
  [opencv-spm maintainer](https://github.com/yeatse/opencv-spm/tree/4.12.0).
- [Pinned package manifest](https://github.com/yeatse/opencv-spm/blob/4.12.0/Package.swift)
  supplies the binary URL, system linker dependencies and SHA-256:
  `13409e99956a74aaee1856c56889c211116fbc8be2bcbe373986ec9e10f536fc`.
- Cache: `build/ios-dependencies/opencv2-4.12.0.xcframework.zip`.
- Target outputs: `build/ios-native/iosArm64/` and
  `build/ios-native/iosSimulatorArm64/`.
- Model: the existing `src/jvmSharedMain/resources/com/alpha/facedetection/models/face_detection_yunet_2026may.onnx`,
  229,738 bytes, SHA-256 `ebafce4e3c118d6554634be5c27ab333b4c047a9a8c3faf1d7cf93101c22f0f0`.
- [Kotlin C interop documentation](https://kotlinlang.org/docs/native-c-interop.html)
  and [static library embedding](https://kotlinlang.org/docs/native-definition-file.html).

For offline builds, place the verified ZIP at the cache path before building.
The preparation stage can be inspected without Xcode:

```sh
python3 local-face-detection/scripts/build_ios.py --target iosArm64 \
  --output /absolute/path/to/local-face-detection/build/ios-native/iosArm64 --prepare-only
```

## Validation boundary

The implementation was developed on Windows. XcodeBuildMCP confirmed that
`xcrun` is unavailable. The 17 Python build-tool tests passed, and actual
`--prepare-only` runs for both targets verified the real archive checksum and
slice metadata/extraction. C++17 syntax against the actual OpenCV 4.12.0 headers
passed with NDK Clang; Gradle task configuration and test-fixture generation also
passed. This does **not** establish that the iOS binary links or runs.
The real simulator tests and device/simulator application builds must still run
on macOS/Xcode. No Apple compilation or runtime pass is claimed.

The existing Android/desktop tests and Web compilation remain valid regression
checks; they do not substitute for those Apple-host checks.
