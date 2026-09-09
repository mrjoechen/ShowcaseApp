# Local face detection

A self-contained Kotlin Multiplatform port of the OpenCV YuNet core from
`D:/github/showcase/local-face-detection`. The public contract has no Android,
OpenCV, Compose, network, or filesystem types:

```kotlin
package com.alpha.facedetection

enum class FaceInspectionResult { NO_FACE, FACE_DETECTED, INDETERMINATE }
fun interface FaceInspector {
    suspend fun inspect(encodedImage: ByteArray): FaceInspectionResult
}
expect fun createFaceInspector(): FaceInspector
```

```kotlin
val result = createFaceInspector().inspect(encodedImage)
```

`NO_FACE` means successful inference produced no qualifying visible face.
`INDETERMINATE` means unsupported platform, invalid/unsupported image encoding,
missing/corrupt model, runtime loading failure, or inference/preprocessing failure.
Coroutine cancellation propagates. Callers must decide how to handle uncertainty;
it must not be interpreted as a successful no-face result. Do not mutate the input
array until inspection returns. The detector performs local inference only.

## Platforms

| Target | Implementation |
| --- | --- |
| Android | Copied trimmed OpenCV `4.12.0-r28` AAR; Bitmap decode/filtered resize, RGBA to BGR |
| Desktop JVM | Trimmed OpenCV `4.12.0` Java bindings plus a single native JAR selected for the host OS/architecture |
| `iosArm64`, `iosSimulatorArm64` | Kotlin/Native C interop to OpenCV `4.12.0` C++ YuNet; compiled-in model |
| JS, Wasm JS | Explicitly unsupported; always `INDETERMINATE` |

Supported product platforms are Android, desktop, and iOS. Web is not supported;
JS/Wasm contain only the fail-closed interface implementation needed for shared
KMP compilation and do not package a model or native runtime. There is no remote fallback.
Desktop verifies and loads its bundled native with `System.load`; Android uses
`System.loadLibrary("opencv_java4")`. See [desktop build and platform availability](DESKTOP.md).

## Behavior and ownership

- Same `2026may` ONNX model as the source: 229,738 bytes, SHA-256
  `ebafce4e3c118d6554634be5c27ab333b4c047a9a8c3faf1d7cf93101c22f0f0`.
- Maximum inference edge 640; aspect ratio preserved, dimensions rounded, no
  upscaling. Score threshold `0.6`, NMS threshold `0.3`, top-K `5000`, initial
  detector input size `320 × 320`. Input size is updated for every image.
- Retains score filtering and clipped, nonempty face-bounds validation. YuNet
  performs NMS internally. The old benchmark/catalog/landmark APIs are omitted.
- A single factory-owned inspector serializes calls on `Dispatchers.Default`,
  using a coroutine mutex on JVM and a C++ mutex on iOS, including initialization,
  image decoding and inference.
- Runtime, verified model bytes, and the detector initialize lazily. The public
  `FaceDetectorYN.create("onnx", modelBuffer, configBuffer, ...)` overload loads
  directly from memory. No application context, model cache directory, temporary
  model file, or Compose resources dependency is required.
- The native detector is intentionally retained for process lifetime and reused,
  matching the source adapter's cached-detector behavior. The public API has no
  close method. Every model-input buffer, per-image Mat and owned Android Bitmap
  is released in `finally`/scoped helpers; no reflection or manual finalization is
  used. The native runtime itself remains loaded for process lifetime.
- Native inference is synchronous and cannot be interrupted midway. Cancellation is
  checked before work and after inference; cleanup completes before returning to
  the caller's dispatcher. Failed lazy initialization can be retried later.

Android and desktop compile the same internal Kotlin implementation from
`src/jvmSharedMain/kotlin` against their respective OpenCV bindings. A single
classpath model copy in `src/jvmSharedMain/resources` is packaged in the Android
Java resources and desktop JAR, along with license texts. Unsupported targets do
not package the model or OpenCV. Input decoding differs by platform: Android uses
BitmapFactory because the trimmed AAR has no imgcodecs; desktop uses OpenCV
imgcodecs with BGR output. Encoded pixel orientation is used without EXIF rotation.
Callers needing rotation must supply appropriately oriented encoded images.
Decoding occurs before the 640-edge resize, so large source images can require
substantial transient memory. No bit-for-bit cross-platform decoder equivalence
is claimed.

On iOS, the native bridge uses OpenCV `imdecode` with the same BGR/encoded-orientation
policy as desktop. The build verifies the model and embeds its bytes in a C++
translation unit. OpenCV, bridge, and model are included in the Kotlin/Native
library through `staticLibraries`, with no dependency on an app bundle lookup.
The Xcode app links the associated system frameworks via `Config.xcconfig`.
See [iOS build and validation](IOS.md) for the pinned framework, automatic Gradle
integration, supported architectures, and outstanding Apple-host validation.

## Parent integration

The parent owns these changes. In root `settings.gradle.kts`:

```kotlin
include(":local-face-detection")
```

Inside `dependencyResolutionManagement.repositories`, alongside Maven Central:

```kotlin
maven(rootDir.resolve("local-face-detection/opencv-repo")) {
    content { includeGroup("com.alpha.thirdparty") }
}
```

Inside the consumer's `kotlin.sourceSets.commonMain.dependencies`:

```kotlin
implementation(project(":local-face-detection"))
```

Uses existing `libs.plugins.kotlinMultiplatform`, `libs.plugins.androidLibrary`,
coroutines and test aliases. Current project versions are Kotlin 2.4.0, AGP 8.11.2,
JDK 17 (JVM bytecode 11), Android compile SDK 36/min SDK 24. No root or consumer
files are part of this module's migration changes. The AAR includes four Android
ABIs and its provenance JSON; avoid adding a second conflicting OpenCV runtime to
the same application.

## Verification

Run from the included parent project:

```powershell
.\gradlew.bat :local-face-detection:desktopTest :local-face-detection:testAndroidHostTest
.\gradlew.bat :local-face-detection:compileDebugAndroidTestKotlinAndroid :local-face-detection:compileKotlinJs :local-face-detection:compileKotlinWasmJs
.\gradlew.bat :local-face-detection:connectedAndroidDeviceTest
```

Verified on Windows/JDK 17:

- **22 desktop tests passed:** native version/module verification, loader validation,
  cleanup/retry/concurrency, real Lena positive, blank square/landscape/portrait
  images, repeated size changes, 12 concurrent alternating positive/negative
  inspections, invalid input recovery, cancellation, geometry, and model integrity.
- **7 Android JVM unit tests passed:** contract, geometry, bundled resource
  integrity and rejection of truncation/same-size corruption. These unit tests do
  not validate Android native loading; the instrumentation test does.
- Android production and instrumentation Kotlin compilation passed. JS and Wasm
  production Kotlin compilation passed.
- Copied AAR, ONNX and Lena hashes match the source records.
- Static inspection of the assembled Android test APK confirmed the model at
  `com/alpha/facedetection/models/face_detection_yunet_2026may.onnx`, with the
  expected size/SHA-256, plus the module license texts. Its four
  `libopencv_java4.so` files match the copied AAR byte-for-byte. `javap` confirms
  the public buffer overload invokes native `create_8`; NDK `llvm-readelf
  --dyn-syms --wide` confirms its JNI export
  `Java_org_opencv_objdetect_FaceDetectorYN_create_18` is defined as a global
  function in all four ABIs (`_1` is JNI's encoding of the underscore).

The instrumentation test exercises the same public context-free factory using
Lena, blank images at changing sizes, and invalid input on the actual trimmed AAR.
The parent's device run on `24129PN74C` was blocked before test execution by
`INSTALL_FAILED_USER_RESTRICTED: Install canceled by user`; its result XML reports
zero tests. Installation was not retried or bypassed. Android native execution is
therefore unverified. The iOS implementation, build preparation and tests are now
present; iOS requires a macOS build host and was not compiled, linked or executed
here. JS/Wasm browser execution was not performed, and detection remains unsupported.

The Lena fixture is in desktop test resources and mapped to Android test assets;
it is not part of the production resources. See
[third-party notices](THIRD_PARTY_NOTICES.md) for source and license records.
