# Trimmed desktop OpenCV

Desktop uses OpenCV 4.12.0 built from the same pinned source as the Android
runtime. OpenPnP 4.9.0 is no longer a dependency. The JNI/Java API and YuNet model
remain the same API used by the shared detector.

## What is trimmed

- Requested modules: `core,imgproc,imgcodecs,dnn,objdetect,java`.
- Required dependency closure adds `calib3d,features2d,flann`. These remain because
  upstream `objdetect` depends on `calib3d`. Inference C++ source is unchanged;
  the Java generator's header traversal is sorted to make output stable across filesystems.
- Image codecs: JPEG and PNG, matching the encoded images supplied to the AI
  summary inspector. Other encodings may return `INDETERMINATE` and are never
  treated as a successful no-face result.
- CPU-only inference; no video capture, GUI, CUDA, OpenCL, OpenVINO, IPP or TBB.
  Protobuf, JPEG, PNG and zlib are built from bundled sources and linked into JNI.
- Windows uses the static MSVC CRT; native dependencies are checked after linking.
- Java classes contain only the generated bindings for the selected module closure.

`desktop-runtime/opencv-yunet-desktop-4.12.0-1.jar` contains Java bindings and
licenses, with no native libraries. Each platform JAR contains exactly one native
library, its checksum, build provenance and licenses. Gradle selects only the JAR
matching its JVM's `os.name` and `os.arch`, consistent with desktop packaging for
the current host. The runtime loader independently checks the actual JVM platform
and verifies the extracted library's SHA-256 before loading it.

| Platform | Native JAR suffix | Native library |
| --- | --- | --- |
| Windows x64 | `windows-x86_64` | `opencv_java4120.dll` |
| macOS Intel | `macos-x86_64` | `libopencv_java4120.dylib` |
| macOS Apple Silicon | `macos-aarch64` | `libopencv_java4120.dylib` |
| Linux x64 | `linux-x86_64` | `libopencv_java4120.so` |
| Linux ARM64 | `linux-aarch64` | `libopencv_java4120.so` |

Use a JVM matching the intended package architecture. A Rosetta x64 JVM selects
the macOS x64 library. Windows ARM64 and 32-bit desktop JVMs are not included in
this build matrix. Android and iOS dependencies are unchanged.

## Explicit native build

Requires Python 3.10+, Temurin **17.0.20.1+1** for package generation, CMake 3.22–3.31,
Ninja and the target host's
C++ toolchain. Windows needs Visual Studio C++ Build Tools. macOS needs Xcode
command-line tools. Linux needs GCC/G++, binutils and development system headers.
Build each platform on its own host; this script does not cross-compile.

```sh
python local-face-detection/scripts/build_desktop.py --jobs 4
python local-face-detection/scripts/verify_desktop.py
```

Optional arguments: `--jdk`, `--cmake`, `--ninja`, `--source-archive`, `--work-dir`
and `--output`. A locally cached source ZIP is accepted only with the pinned hash:

```powershell
py -3 local-face-detection/scripts/build_desktop.py `
  --source-archive D:/github/showcase/build/opencv-yunet/opencv-4.12.0.zip `
  --cmake D:/Android/sdk/cmake/3.22.1/bin/cmake.exe `
  --ninja D:/Android/sdk/cmake/3.22.1/bin/ninja.exe --jobs 6
```

The script verifies source identity, configured module closure, native machine
type, JNI exports and shared dependencies before publishing deterministic ZIP
entries with canonical ZIP creator metadata to `desktop-runtime/`. The adjacent JSON records hashes, sizes and build
configuration. Common Java classes must match the existing common JAR; use the
same pinned JDK when preparing the platform set: different javac 17 patch versions
can generate different string-concatenation bytecode. The checked generator patch
`sorted-java-header-walk-v1` sorts directory/file names without changing explicit
header lists or inference code. Matching common
JARs are preserved byte-for-byte so rebuilding one classifier cannot invalidate
another classifier's recorded common-JAR hash.

Normal Gradle Sync and app builds never run this compiler script or download a
native runtime. On a host whose native JAR has not been prepared yet, IDE import
can complete, while desktop testing/packaging reports the missing platform with
the build command. Install that platform's verified CI artifact or run the script
before packaging. There is no fallback to a full OpenPnP or system OpenCV library.

## CI and verification

The current Windows x64 build produced a 260,284-byte Java JAR and a
6,300,270-byte native JAR (6.26 MiB combined). The DLL is 16,837,632 bytes
(16.06 MiB) and imports only `KERNEL32.dll`. The former OpenPnP all-platform JAR
was 104.54 MiB; its Windows x64 DLL alone was 49.61 MiB uncompressed. This reduces
the OpenCV dependency package by about 94%; it is not a measurement of the whole
application installer.

Linux x64 is also built: 8,114,501-byte native JAR, 20,539,136-byte `.so`, and
7.99 MiB combined with the same Java JAR. It was compiled and tested in an Ubuntu
22.04 container with GCC 11.4, CMake 3.22.1 and Temurin 17.0.20.1+1. Older Linux
distributions and Linux ARM64 have not been validated. macOS Intel/Apple Silicon
and Linux ARM64 packages still require their corresponding CI builds.

The retained common JAR SHA-256 is
`a70ed83d930b7a35f50b87f08bc1576c8d3318d34f8b266c4ee12ae606608f65`.
Windows and Linux builds independently generated identical class contents and
both platform sidecars reference this exact artifact.

Validation completed locally:

- Windows: 22 module tests, 7 Android JVM tests and 56 AI regression tests passed.
- Linux: the same 22 compiled JVM module tests passed with the Linux native JAR
  and no Windows JAR on the classpath (`build/face-privacy-desktop-linux-junit.log`).
- Both native packages passed standalone actual YuNet/codec/JNI smoke tests.
- 31 desktop builder tests, 17 iOS builder tests and 8 package-verifier tests passed.
- Whole-project IDE import passed, including a run with the native package
  temporarily absent; the explicit build check rejected that missing package.
- Windows release distribution built, and its ProGuard-processed classes/native
  library detected Lena while rejecting blank PNG/JPEG images.

`.github/workflows/face-detection-desktop.yml` builds each platform independently,
runs script tests and checks real YuNet inference against the newly built JNI
library. Download the corresponding CI artifact into `desktop-runtime/`, preserving
the original names. This workflow is configuration only until it is run; creating
it does not produce the macOS/Linux native packages.

The standalone smoke test needs only the JDK. Module integration tests are:

```sh
./gradlew :local-face-detection:desktopTest :local-face-detection:verifyDesktopOpenCv
./gradlew prepareKotlinIdeaImport
```

`verifyDesktopOpenCv` checks native package selection before desktop Kotlin
compilation, the module JAR or tests. The detector tests exercise faces, blank images, changing
sizes, invalid bytes, concurrency and cancellation. Desktop ProGuard rules retain
JNI entry points and native-to-Java lookups.

## Source and licenses

- [OpenCV 4.12.0 source](https://github.com/opencv/opencv/tree/4.12.0).
- Source ZIP SHA-256: `fa3faf7581f1fa943c9e670cf57dd6ba1c5b4178f363a188a2c8bff1eb28b7e4`.
- [Official build options](https://docs.opencv.org/4.12.0/db/d05/tutorial_config_reference.html).
- Both Java and native JARs preserve OpenCV and bundled third-party license files
  under `META-INF/licenses/opencv/`. See `THIRD_PARTY_NOTICES.md`.
