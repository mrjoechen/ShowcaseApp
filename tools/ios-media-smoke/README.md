# iOS media integration checks

Run from the repository root on an Apple Silicon Mac with Xcode and an installed
arm64 iOS simulator runtime:

```sh
./gradlew -p tools/ios-media-smoke :iosSimulatorArm64Test
```

This small standalone Gradle build uses the root version catalog, the real
`local-face-detection` module and the media tests in `composeApp/src/iosTest`.
It exercises OpenCV and Skia JPEG decoding in the **same executable**, plus a
Compottie gradient render with pixel assertions and captured renderer errors.
Testing either native decoder alone cannot detect collisions between them.

The tests also belong to the normal `:composeApp:iosSimulatorArm64Test` suite.
The standalone entry point avoids unrelated application test dependencies when
isolating native linkage failures. Initial compilation/linking takes longer than
subsequent runs. Use a version override for a Compottie compatibility comparison:

```sh
./gradlew -p tools/ios-media-smoke :iosSimulatorArm64Test -PcompottieVersion=2.1.0
```

## Verified failure and fix (2026-09-07)

- Original dependencies/archive: both tests failed, with `Wrong JPEG library
  version: library is 70, caller expects 62` / `Failed to Image::makeFromEncoded`
  and the missing `Paint.shader(org.jetbrains.skia.Shader?)` accessor.
- Isolating the OpenCV archive alone: JPEG passed; the gradient still failed.
- Compottie 2.2.4 with Compose 1.11.1: both tests passed. The continuous drawing
  check recorded zero renderer errors; Compottie 2.1.0 recorded 121 (initial draw
  plus 120 frames).

The frame timing printed by the test is a small CPU/offscreen diagnostic, with
renderer errors collected rather than printed. It is not an application FPS
benchmark or a substitute for a device playback check.

The full application test task currently encounters a Kotlin 2.4
`Clock` type-alias binding failure while caching `okio-fakefilesystem` 3.17.0.
The isolated tests avoid that dependency; they do not imply the full suite passed.
