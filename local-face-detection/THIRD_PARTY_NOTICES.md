# Third-party notices and source record

## YuNet model

- File: `src/jvmSharedMain/resources/com/alpha/facedetection/models/face_detection_yunet_2026may.onnx`.
- Copied unchanged from the reference module's `src/main/assets/face_detection/`.
- Model version: `2026may`; 229,738 bytes.
- SHA-256: `ebafce4e3c118d6554634be5c27ab333b4c047a9a8c3faf1d7cf93101c22f0f0`.
- Pinned source commit: `47534e27c9851bb1128ccc0102f1145e27f23f98`.
- [Model source](https://github.com/opencv/opencv_zoo/blob/47534e27c9851bb1128ccc0102f1145e27f23f98/models/face_detection_yunet/face_detection_yunet_2026may.onnx).
- [MIT license](https://github.com/opencv/opencv_zoo/blob/47534e27c9851bb1128ccc0102f1145e27f23f98/models/face_detection_yunet/LICENSE), copyright 2020 Shiqi Yu.
- Full license is bundled in `src/jvmSharedMain/resources/META-INF/local-face-detection/YUNET-LICENSE.txt`.

The reference module records WIDER FACE and RetinaFace reannotations as training
data, and notes the WIDER FACE dataset's noncommercial terms. This migration
preserves the source record and model license; it makes no new determination
about training-data rights. Reference:
[WIDER FACE dataset](https://shuoyang1213.me/WIDERFACE/index.html).

## Trimmed Android runtime

- Local Maven coordinate: `com.alpha.thirdparty:opencv-yunet:4.12.0-r28`.
- The AAR, POM and JSON provenance were copied unchanged into `opencv-repo/`.
- AAR SHA-256: `6273503e83d8caff05126d6188dda645af9b6e15285678a1c76de81df39b8f54`.
- [OpenCV 4.12.0 source](https://github.com/opencv/opencv/tree/4.12.0).
- Source archive SHA-256: `fa3faf7581f1fa943c9e670cf57dd6ba1c5b4178f363a188a2c8bff1eb28b7e4`.
- [OpenCV Apache-2.0 license](https://github.com/opencv/opencv/blob/4.12.0/LICENSE).
- Built using NDK `28.2.13676358`, CMake `3.22.1`, API 21 native minimum.
  The consuming module uses the parent project's API 24 minimum.
- Requested modules: core, imgproc, dnn, objdetect, java. Dependencies add calib3d,
  features2d and flann. The AAR excludes imgcodecs, camera and unused Android
  resource helpers. OpenCV C++ source is unchanged.
- The AAR contains `META-INF/licenses/` with OpenCV, Protobuf, NDK and other
  third-party notices and `META-INF/opencv-yunet-build.json` with build details.
  The adjacent JSON records ABI library hashes, sizes and ELF page alignment.
- The source rebuild instructions/script remain in the read-only reference
  project (`local-face-detection/OPENCV_BUILD.md` and
  `scripts/build-opencv-yunet.py`); normal consumers need only the bundled artifact.

## Desktop runtime

- Rebuilt from [OpenCV 4.12.0](https://github.com/opencv/opencv/tree/4.12.0) with
  `scripts/build_desktop.py`, source SHA-256 identical to the Android record above.
- Uses upstream generated Java bindings and a project-owned native loader.
  OpenPnP binaries and loader are no longer included.
- Retains `core,imgproc,imgcodecs,dnn,objdetect,java` and the required
  `calib3d,features2d,flann` dependencies. Bundled Protobuf, PNG, JPEG and zlib
  are statically linked; only the selected platform's JNI library is packaged.
- Both JARs carry OpenCV and bundled third-party source notices under
  `META-INF/licenses/opencv/`. The existing shared OpenCV Apache-2.0 text remains.
- Each platform's adjacent JSON records source, options, native dependencies,
  hashes and sizes. See [desktop build instructions](DESKTOP.md).
- The Java generator's header walk is patched to sort directories and filenames
  (`sorted-java-header-walk-v1`); the inference C++ source is unchanged. Package
  generation pins Temurin 17.0.20.1+1 to avoid compiler-dependent bytecode drift.

## iOS runtime

- OpenCV `4.12.0` XCFramework from the [opencv-spm release](https://github.com/yeatse/opencv-spm/releases/tag/4.12.0).
- The [pinned manifest](https://github.com/yeatse/opencv-spm/blob/4.12.0/Package.swift) records SHA-256
  `13409e99956a74aaee1856c56889c211116fbc8be2bcbe373986ec9e10f536fc`.
- The ZIP is downloaded into ignored build output, verified before extraction,
  and is not added to source control. Selected iOS arm64 libraries are statically linked.
- OpenCV uses [Apache-2.0](https://github.com/opencv/opencv/blob/4.12.0/LICENSE).
  Retain the existing bundled OpenCV/YuNet license texts and the package's
  [license](https://github.com/yeatse/opencv-spm/blob/4.12.0/LICENSE) when distributing.
- iOS embeds the same verified ONNX bytes as Android and desktop. There is no
  additional model or network inference service.

## Test fixture

- `src/desktopTest/resources/opencv/lena.jpg`, copied unchanged from the reference
  instrumentation assets.
- [OpenCV 4.12.0 fixture source](https://github.com/opencv/opencv/blob/4.12.0/samples/data/lena.jpg).
- SHA-256: `7de7ed51a1594fff247f4cae2301eceacf5313d6011e37b4a4c8733f7bb72c07`.
- Used only in desktop tests, Android instrumentation assets and generated iOS
  test fixtures. It is not shipped
  in production module resources. Upstream notice is included beside the fixture.
