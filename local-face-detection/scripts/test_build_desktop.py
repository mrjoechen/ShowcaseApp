"""Offline contract tests; run with python -B -m unittest discover -s
local-face-detection/scripts -p test_build_desktop.py -v.

Binary fixtures contain only the headers/build information inspected by the
script. They are not loadable libraries and do not exercise a native build.
"""
import contextlib
import hashlib
import importlib.util
import io
import json
from pathlib import Path, PureWindowsPath
import struct
import sys
import tempfile
import unittest
from unittest import mock
import zipfile


_SPEC = importlib.util.spec_from_file_location(
    "build_desktop_under_test", Path(__file__).with_name("build_desktop.py")
)
desktop = importlib.util.module_from_spec(_SPEC)
_SPEC.loader.exec_module(desktop)

EXPECTED_MODULES = {
    "core", "imgproc", "imgcodecs", "dnn", "objdetect", "java",
    "calib3d", "features2d", "flann",
}
REQUIRED_JNI = (
    b"Java_org_opencv_objdetect_FaceDetectorYN_create_10",
    b"Java_org_opencv_objdetect_FaceDetectorYN_detect_10",
    b"Java_org_opencv_imgcodecs_Imgcodecs_imdecode_10",
)
REQUIRED_CLASSES = (
    "org/opencv/objdetect/FaceDetectorYN.class",
    "org/opencv/core/Mat.class",
    "org/opencv/imgproc/Imgproc.class",
    "org/opencv/imgcodecs/Imgcodecs.class",
)


def binary_fixture(target="windows-x86_64", modules=None, symbols=None):
    data = bytearray(128)
    if target.startswith("windows"):
        data[:2] = b"MZ"
        struct.pack_into("<I", data, 60, 64)
        data[64:68] = b"PE\0\0"
        struct.pack_into("<H", data, 68, 0x8664)
    elif target.startswith("linux"):
        data[:6] = b"\x7fELF\x02\x01"
        struct.pack_into("<H", data, 18, 183 if target.endswith("aarch64") else 62)
    else:
        data[:4] = b"\xcf\xfa\xed\xfe"
        struct.pack_into("<I", data, 4, 0x100000C if target.endswith("aarch64") else 0x1000007)
    modules = EXPECTED_MODULES if modules is None else modules
    symbols = REQUIRED_JNI if symbols is None else symbols
    data.extend(b"To be built: " + " ".join(sorted(modules)).encode() + b"\n\0")
    data.extend(b"".join(symbol + b"\0" for symbol in symbols))
    return bytes(data)


def write_zip(path, entries):
    path.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(path, "w") as archive:
        for name, content in entries.items():
            entry = zipfile.ZipInfo(name)
            # ZipInfo normalizes Windows separators; preserve malicious names
            # verbatim so extraction, rather than the fixture writer, sees them.
            entry.filename = name
            archive.writestr(entry, content)


class HostAndConfigurationTests(unittest.TestCase):
    def test_host_aliases(self):
        for system, os_name in (("Windows", "windows"), ("Darwin", "macos"), ("Linux", "linux")):
            aliases = {"AMD64": "x86_64", "x86_64": "x86_64"}
            if system != "Windows":
                aliases.update(arm64="aarch64", aarch64="aarch64")
            for machine, arch in aliases.items():
                with self.subTest(system=system, machine=machine):
                    self.assertEqual(desktop.host_platform(system, machine), f"{os_name}-{arch}")
                    self.assertEqual(desktop.host_platform(system.upper(), machine.upper()), f"{os_name}-{arch}")

    def test_default_host_uses_platform_detection(self):
        with mock.patch.object(desktop.platform, "system", return_value="Darwin"), mock.patch.object(
            desktop.platform, "machine", return_value="arm64"
        ):
            self.assertEqual(desktop.host_platform(), "macos-aarch64")

    def test_unsupported_hosts(self):
        for system, machine in (("FreeBSD", "amd64"), ("Android", "arm64"), ("Windows", "arm64"),
                                ("Windows", "aarch64"), ("Linux", "i686"), ("Linux", "riscv64"),
                                ("Darwin", "armv7")):
            with self.subTest(system=system, machine=machine):
                with self.assertRaisesRegex(ValueError, "Unsupported desktop build host"):
                    desktop.host_platform(system, machine)

    def test_exact_minimal_build_configuration(self):
        jdk = Path("fixture tools") / "JDK with spaces"
        expected = {
            "CMAKE_BUILD_TYPE": "Release",
            "BUILD_LIST": "core,imgproc,imgcodecs,dnn,objdetect,java",
            "BUILD_SHARED_LIBS": "OFF", "BUILD_FAT_JAVA_LIB": "ON",
            "BUILD_WITH_STATIC_CRT": "ON", "BUILD_TESTS": "OFF", "BUILD_PERF_TESTS": "OFF",
            "BUILD_EXAMPLES": "OFF", "BUILD_DOCS": "OFF", "BUILD_opencv_apps": "OFF",
            "BUILD_JAVA": "ON", "OPENCV_JAVA_SDK_BUILD_TYPE": "JAVA",
            "OPENCV_EXTRA_JAVA_COMPILE_FLAGS": "--release;11",
            "JAVA_HOME": jdk.as_posix(), "PYTHON_DEFAULT_EXECUTABLE": Path(sys.executable).as_posix(),
            "PYTHON3_EXECUTABLE": Path(sys.executable).as_posix(),
            "WITH_PROTOBUF": "ON", "BUILD_PROTOBUF": "ON",
            "WITH_JPEG": "ON", "BUILD_JPEG": "ON", "WITH_PNG": "ON", "BUILD_PNG": "ON",
            "BUILD_ZLIB": "ON", "CPU_DISPATCH": "", "ENABLE_LTO": "OFF",
        }
        disabled = (
            "OPENCL IPP ITT TBB QUIRC FLATBUFFERS KLEIDICV ADE CUDA CUDNN OPENVINO "
            "FFMPEG GSTREAMER GTK QT VTK OPENGL LAPACK EIGEN TIFF WEBP OPENJPEG "
            "JASPER OPENEXR AVIF 1394 V4L MSMF DSHOW GPHOTO2"
        ).split()
        expected.update((f"WITH_{feature}", "OFF") for feature in disabled)
        expected.update((f"OPENCV_DNN_{feature}", "OFF") for feature in ("TFLITE", "OPENCL", "CUDA"))
        expected.update((f"WITH_IMGCODEC_{codec}", "OFF") for codec in ("HDR", "SUNRASTER", "PXM", "PFM", "GIF"))
        self.assertEqual(desktop.CLOSURE, EXPECTED_MODULES)
        self.assertEqual(desktop.cmake_options(jdk), expected)

    def test_windows_cmake_java_home_uses_forward_slashes(self):
        # PureWindowsPath exercises Windows separators on every test host.
        jdk = PureWindowsPath(r"C:\Program Files\Java\jdk-17")
        options = desktop.cmake_options(jdk)
        self.assertEqual(options["JAVA_HOME"], "C:/Program Files/Java/jdk-17")


class BinaryValidationTests(unittest.TestCase):
    def test_supported_architectures(self):
        for target in ("windows-x86_64", "linux-x86_64", "linux-aarch64", "macos-x86_64", "macos-aarch64"):
            with self.subTest(target=target):
                desktop.validate_binary(binary_fixture(target), target)

    def test_wrong_architectures(self):
        for target, offset, fmt, machine in (
            ("windows-x86_64", 68, "<H", 0xAA64),
            ("windows-x86_64", 68, "<H", 0x14C),
            ("linux-x86_64", 18, "<H", 183),
            ("linux-aarch64", 18, "<H", 62),
            ("macos-x86_64", 4, "<I", 0x100000C),
            ("macos-aarch64", 4, "<I", 0x1000007),
        ):
            with self.subTest(target=target, machine=machine):
                data = bytearray(binary_fixture(target))
                struct.pack_into(fmt, data, offset, machine)
                with self.assertRaisesRegex(ValueError, "architecture"):
                    desktop.validate_binary(data, target)

    def test_wrong_binary_formats(self):
        for target in ("windows-x86_64", "linux-x86_64", "macos-aarch64"):
            with self.subTest(target=target):
                data = b"BAD!" + binary_fixture(target)[4:]
                with self.assertRaises(ValueError):
                    desktop.validate_binary(data, target)

    def test_module_closure_must_be_exact(self):
        for modules in (EXPECTED_MODULES - {"dnn"}, EXPECTED_MODULES | {"videoio"}, set()):
            with self.subTest(modules=sorted(modules)):
                with self.assertRaisesRegex(ValueError, "module closure"):
                    desktop.validate_binary(binary_fixture(modules=modules), "windows-x86_64")

    def test_missing_build_information(self):
        data = binary_fixture().replace(b"To be built:", b"Unavailable:")
        with self.assertRaisesRegex(ValueError, "module closure"):
            desktop.validate_binary(data, "windows-x86_64")

    def test_each_required_jni_export_is_required(self):
        for missing in REQUIRED_JNI:
            with self.subTest(missing=missing):
                data = binary_fixture(symbols=[symbol for symbol in REQUIRED_JNI if symbol != missing])
                with self.assertRaisesRegex(ValueError, "Missing required JNI export"):
                    desktop.validate_binary(data, "windows-x86_64")

    def test_jni_symbol_prefix_is_not_an_exact_match(self):
        data = binary_fixture().replace(REQUIRED_JNI[0] + b"\0", REQUIRED_JNI[0] + b"extra\0")
        with self.assertRaisesRegex(ValueError, "Missing required JNI export"):
            desktop.validate_binary(data, "windows-x86_64")


class TemporaryDirectoryTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix="test-build-desktop-")
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        # Any accidental build command or network access fails the test immediately.
        for name in ("run", "check_output"):
            self.enterContext(mock.patch.object(desktop.subprocess, name, side_effect=AssertionError("External commands forbidden")))
        self.enterContext(mock.patch.object(desktop.urllib.request, "urlretrieve", side_effect=AssertionError("Network forbidden")))


class SourceExtractionTests(TemporaryDirectoryTests):
    def extract_fixture(self, entries, destination=None):
        archive = self.root / "source.zip"
        write_zip(archive, entries)
        checksum = hashlib.sha256(archive.read_bytes()).hexdigest()
        with mock.patch.object(desktop, "SOURCE_SHA256", checksum):
            return desktop.extract_source(archive, destination or self.root / "extracted")

    def test_digest_matches_known_sha256(self):
        path = self.root / "data"
        path.write_bytes(b"abc")
        self.assertEqual(desktop.digest(path), "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")

    def test_valid_checksum_extracts_expected_source_tree(self):
        source = self.extract_fixture({"opencv-4.12.0/": b"", "opencv-4.12.0/LICENSE": b"license",
                                       "opencv-4.12.0/modules/core/file.cpp": b"source"})
        self.assertEqual(source, self.root / "extracted/opencv-4.12.0")
        self.assertEqual((source / "LICENSE").read_bytes(), b"license")
        self.assertEqual((source / "modules/core/file.cpp").read_bytes(), b"source")

    def test_reextract_preserves_identical_files_and_restores_changed_files(self):
        entries = {"opencv-4.12.0/LICENSE": b"license", "opencv-4.12.0/file.cpp": b"source"}
        source = self.extract_fixture(entries)
        unchanged = source / "LICENSE"
        changed = source / "file.cpp"
        # A fixed old timestamp makes an unwanted rewrite observable without sleeping.
        desktop.os.utime(unchanged, (1000000000, 1000000000))
        original_time = unchanged.stat().st_mtime_ns
        changed.write_bytes(b"modified")
        self.extract_fixture(entries)
        self.assertEqual(unchanged.stat().st_mtime_ns, original_time)
        self.assertEqual(changed.read_bytes(), b"source")

    def test_bad_checksum_is_rejected_before_opening_or_writing(self):
        archive = self.root / "source.zip"
        archive.write_bytes(b"not even a zip")
        destination = self.root / "extracted"
        with mock.patch.object(desktop, "SOURCE_SHA256", "0" * 64), mock.patch.object(
            desktop.zipfile, "ZipFile", side_effect=AssertionError("Must verify checksum first")
        ):
            with self.assertRaisesRegex(ValueError, "SHA-256 mismatch"):
                desktop.extract_source(archive, destination)
        self.assertFalse(destination.exists())

    def test_traversal_with_either_separator_is_rejected_with_valid_checksums(self):
        for entry in ("../escape.txt", "opencv-4.12.0/../../escape.txt", "../escape/",
                      "..\\escape.txt", "opencv-4.12.0\\..\\..\\escape.txt"):
            with self.subTest(entry=entry):
                with self.assertRaisesRegex(ValueError, "Unsafe source archive path"):
                    self.extract_fixture({entry: b"untrusted"})
                self.assertFalse((self.root / "escape.txt").exists())
                self.assertFalse((self.root / "extracted").exists())

    def test_absolute_archive_path_cannot_overwrite_existing_file(self):
        outside = self.root / "keep.txt"
        outside.write_bytes(b"original")
        with self.assertRaisesRegex(ValueError, "Unsafe source archive path"):
            self.extract_fixture({outside.as_posix(): b"overwrite"})
        self.assertEqual(outside.read_bytes(), b"original")


class ZipPackagingTests(TemporaryDirectoryTests):
    def test_identical_entries_have_identical_bytes_and_hashes_across_hosts(self):
        files = {"org/opencv/core/Mat.class": b"identical class bytes",
                 "META-INF/licenses/opencv/LICENSE": b"identical license bytes"}
        archives = []
        for host in ("win32", "linux", "darwin"):
            path = self.root / f"{host}.jar"
            # ZipInfo chooses its default creator from sys.platform. Exercise
            # the real host-dependent default without launching another OS.
            with mock.patch.object(zipfile.sys, "platform", host):
                desktop.pack_zip(path, files)
            with zipfile.ZipFile(path) as archive:
                self.assertEqual({entry.create_system for entry in archive.infolist()}, {3})
                self.assertEqual({name: archive.read(name) for name in archive.namelist()}, files)
            archives.append(path.read_bytes())
        self.assertEqual(archives[0], archives[1])
        self.assertEqual(archives[0], archives[2])
        self.assertEqual(len({hashlib.sha256(data).hexdigest() for data in archives}), 1)


class PackagingTests(TemporaryDirectoryTests):
    def setUp(self):
        super().setUp()
        self.source = self.root / "source"
        self.build = self.root / "build"
        self.output = self.root / "output"
        self.source.mkdir()
        (self.source / "LICENSE").write_bytes(b"OpenCV license")
        third_party = self.source / "3rdparty/codec"
        third_party.mkdir(parents=True)
        (third_party / "COPYING").write_bytes(b"codec license")
        (third_party / "code.c").write_bytes(b"not a license")
        self.binary = self.build / "lib/Release/opencv_java4120.dll"
        self.binary.parent.mkdir(parents=True)
        self.binary.write_bytes(binary_fixture())
        self.classes = {name: b"\xca\xfe\xba\xbe" + name.encode() for name in REQUIRED_CLASSES}
        self.classes["org/opencv/utils/Converters.class"] = b"helper class"
        self.java_jar = self.build / "bin/opencv-4120.jar"
        self.write_bindings()
        self.dependencies = self.enterContext(mock.patch.object(desktop, "native_dependencies", return_value=["KERNEL32.dll"]))
        self.options = desktop.cmake_options(Path("fixture-jdk"))
        self.common = self.output / f"{desktop.ARTIFACT}.jar"
        self.native = self.output / f"{desktop.ARTIFACT}-windows-x86_64.jar"
        self.report = self.output / f"{desktop.ARTIFACT}-windows-x86_64.json"

    def write_bindings(self):
        write_zip(self.java_jar, {**self.classes, "META-INF/MANIFEST.MF": b"ignored",
                                  "org/opencv/core/Mat.java": b"ignored source",
                                  "opencv_java4120.dll": b"must not leak to common jar"})

    def package(self):
        with contextlib.redirect_stdout(io.StringIO()):
            desktop.package(self.source, self.build, self.output, "windows-x86_64", {}, self.options)

    def test_separate_jars_licenses_and_metadata(self):
        self.package()
        self.dependencies.assert_called_once_with(self.binary, "windows-x86_64", {})
        licenses = {"META-INF/licenses/opencv/LICENSE": b"OpenCV license",
                    "META-INF/licenses/opencv/3rdparty/codec/COPYING": b"codec license"}
        prefix = "com/alpha/facedetection/native/windows-x86_64/"
        data = self.binary.read_bytes()
        native_hash = hashlib.sha256(data).hexdigest()
        with zipfile.ZipFile(self.common) as archive:
            self.assertEqual({name: archive.read(name) for name in archive.namelist()}, {**self.classes, **licenses})
        with zipfile.ZipFile(self.native) as archive:
            self.assertEqual(set(archive.namelist()), set(licenses) | {
                prefix + self.binary.name, prefix + "native.properties", "META-INF/opencv-yunet-desktop-build.json"})
            self.assertEqual(archive.read(prefix + self.binary.name), data)
            for name, content in licenses.items():
                self.assertEqual(archive.read(name), content)
            self.assertEqual(archive.read(prefix + "native.properties").decode(),
                             f"version=4.12.0\nplatform=windows-x86_64\nlibrary=opencv_java4120.dll\nsha256={native_hash}\n")
            metadata = json.loads(archive.read("META-INF/opencv-yunet-desktop-build.json"))
        self.assertEqual(metadata, {
            "java_compiler": desktop.JAVA_COMPILER, "generator_patch": desktop.GENERATOR_PATCH,
            "version": "4.12.0", "platform": "windows-x86_64", "source_url": desktop.SOURCE_URL,
            "source_sha256": desktop.SOURCE_SHA256, "modules": sorted(EXPECTED_MODULES),
            "cmake_options": self.options, "dependencies": ["KERNEL32.dll"],
            "library": self.binary.name, "native_bytes": len(data), "native_sha256": native_hash,
        })
        self.assertEqual(json.loads(self.report.read_text(encoding="utf-8")), {
            **metadata, "java_jar_sha256": hashlib.sha256(self.common.read_bytes()).hexdigest(),
            "native_jar_sha256": hashlib.sha256(self.native.read_bytes()).hexdigest(),
            "native_jar_bytes": self.native.stat().st_size,
        })

    def test_repackaging_is_byte_identical_despite_input_order_and_timestamp(self):
        self.package()
        before = {path.name: path.read_bytes() for path in self.output.iterdir()}
        self.classes = dict(reversed(list(self.classes.items())))
        self.write_bindings()
        desktop.os.utime(self.binary, (1000000000, 1000000000))
        self.package()
        self.assertEqual({path.name: path.read_bytes() for path in self.output.iterdir()}, before)
        for path in (self.common, self.native):
            with zipfile.ZipFile(path) as archive:
                self.assertEqual(archive.namelist(), sorted(archive.namelist()))
                self.assertEqual(len({entry.date_time for entry in archive.infolist()}), 1)
        self.assertFalse(list(self.output.glob("*.tmp")))

    def test_invalid_native_inputs_are_rejected_before_packaging(self):
        wrong_arch = bytearray(binary_fixture())
        struct.pack_into("<H", wrong_arch, 68, 0xAA64)
        for data, message in ((wrong_arch, "architecture"),
                              (binary_fixture(modules=EXPECTED_MODULES | {"videoio"}), "module closure"),
                              (binary_fixture(symbols=REQUIRED_JNI[1:]), "JNI export")):
            with self.subTest(message=message):
                self.binary.write_bytes(data)
                with self.assertRaisesRegex(ValueError, message):
                    self.package()
                self.assertFalse(self.output.exists())
        self.dependencies.assert_not_called()

    def test_existing_common_jar_bytes_and_hash_survive_a_different_zip_creator(self):
        entries = {**self.classes,
                   "META-INF/licenses/opencv/LICENSE": b"OpenCV license",
                   "META-INF/licenses/opencv/3rdparty/codec/COPYING": b"codec license"}
        # Model a previously published Windows JAR with legacy ZIP metadata
        # and stored entries, so rewriting it canonically would change its hash.
        with mock.patch.object(zipfile.sys, "platform", "win32"):
            write_zip(self.common, entries)
            with zipfile.ZipFile(self.common) as archive:
                self.assertEqual({entry.create_system for entry in archive.infolist()}, {0})
            original = self.common.read_bytes()
            original_hash = hashlib.sha256(original).hexdigest()
            self.package()
        self.assertEqual(self.common.read_bytes(), original)
        prior_report = json.loads(self.report.read_text(encoding="utf-8"))
        self.assertEqual(prior_report["java_jar_sha256"], original_hash)

        # Only simulate the ZIP creator here; the native fixture stays Windows.
        with mock.patch.object(zipfile.sys, "platform", "linux"):
            self.package()
        self.assertEqual(self.common.read_bytes(), original)
        self.assertEqual(desktop.digest(self.common), prior_report["java_jar_sha256"])
        self.assertEqual(json.loads(self.report.read_text(encoding="utf-8"))["java_jar_sha256"], original_hash)

    def test_missing_required_java_bindings_are_rejected(self):
        for missing in REQUIRED_CLASSES:
            with self.subTest(missing=missing):
                saved = self.classes.pop(missing)
                self.write_bindings()
                with self.assertRaisesRegex(ValueError, "Missing Java binding"):
                    self.package()
                self.assertFalse(self.output.exists())
                self.classes[missing] = saved

    def test_optional_osgi_classes_are_excluded_from_packaged_jars(self):
        expected_classes = self.classes.copy()
        osgi_classes = {
            "org/opencv/osgi/OpenCVInterface.class": b"optional interface",
            "org/opencv/osgi/OpenCVNativeLoader.class": b"optional loader",
        }
        self.classes.update(osgi_classes)
        self.write_bindings()
        self.package()
        with zipfile.ZipFile(self.common) as archive:
            self.assertEqual({name: archive.read(name) for name in archive.namelist()
                              if name.endswith(".class")}, expected_classes)
        for path in (self.common, self.native):
            with zipfile.ZipFile(path) as archive:
                self.assertFalse(any(name.startswith("org/opencv/osgi/") for name in archive.namelist()))

        # Optional classes must not affect the common-binding compatibility check
        # or artifact bytes across subsequent platform builds.
        before = {path.name: path.read_bytes() for path in self.output.iterdir()}
        for name in osgi_classes:
            self.classes[name] = b"changed optional class"
        self.write_bindings()
        self.package()
        self.assertEqual({path.name: path.read_bytes() for path in self.output.iterdir()}, before)

    def test_unwanted_java_module_is_rejected(self):
        self.classes["org/opencv/osgi/OpenCVInterface.class"] = b"optional interface"
        for unwanted in ("org/opencv/videoio/VideoCapture.class",
                         "org/opencv/highgui/HighGui.class",
                         "org/opencv/osgi_extra/Unexpected.class"):
            with self.subTest(unwanted=unwanted):
                self.classes[unwanted] = b"unwanted"
                self.write_bindings()
                with self.assertRaisesRegex(ValueError, "Unwanted Java module") as error:
                    self.package()
                self.assertIn(unwanted, str(error.exception))
                self.assertFalse(self.output.exists())
                del self.classes[unwanted]

    def test_changed_common_bindings_do_not_overwrite_existing_artifacts(self):
        self.package()
        before = {path.name: path.read_bytes() for path in self.output.iterdir()}
        self.classes[REQUIRED_CLASSES[0]] = b"different bindings"
        self.write_bindings()
        with self.assertRaisesRegex(ValueError, "Generated Java bindings differ"):
            self.package()
        self.assertEqual({path.name: path.read_bytes() for path in self.output.iterdir()}, before)

    def test_changed_added_or_removed_licenses_do_not_overwrite_existing_artifacts(self):
        self.package()
        before = {path.name: path.read_bytes() for path in self.output.iterdir()}
        for relative, replacement in (("LICENSE", b"changed OpenCV license"),
                                      ("3rdparty/codec/COPYING", b"changed codec license"),
                                      ("3rdparty/codec/NOTICE", b"added notice"),
                                      ("3rdparty/codec/COPYING", None)):
            with self.subTest(relative=relative, replacement=replacement):
                path = self.source / relative
                original = path.read_bytes() if path.exists() else None
                try:
                    if replacement is None:
                        path.unlink()
                    else:
                        path.write_bytes(replacement)
                    with self.assertRaisesRegex(ValueError, "Common JAR resources differ"):
                        self.package()
                    self.assertEqual({path.name: path.read_bytes() for path in self.output.iterdir()}, before)
                finally:
                    if original is None:
                        path.unlink(missing_ok=True)
                    else:
                        path.write_bytes(original)

    def test_rejected_dependencies_prevent_artifact_creation(self):
        self.dependencies.side_effect = ValueError("Unexpected DLL dependencies")
        with self.assertRaisesRegex(ValueError, "Unexpected DLL dependencies"):
            self.package()
        self.assertFalse(self.output.exists())


class GeneratorDeterminismTests(unittest.TestCase):
    def test_header_walk_is_sorted_and_patch_is_idempotent(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            generator = root / "modules/java/generator/gen_java.py"
            generator.parent.mkdir(parents=True)
            generator.write_text("            for root, dirnames, filenames in os.walk(os.path.join(module_location, 'include')):\n               consume(filenames)\n")
            desktop.patch_java_generator(root)
            patched = generator.read_bytes()
            self.assertIn(b"dirnames.sort()\n               filenames.sort()\n", patched)
            desktop.patch_java_generator(root)
            self.assertEqual(patched, generator.read_bytes())

    def test_unknown_generator_is_rejected_without_changes(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            generator = root / "modules/java/generator/gen_java.py"
            generator.parent.mkdir(parents=True)
            generator.write_text("unknown source")
            with self.assertRaisesRegex(ValueError, "anchor changed"):
                desktop.patch_java_generator(root)
            self.assertEqual("unknown source", generator.read_text())


if __name__ == "__main__":
    unittest.main()
