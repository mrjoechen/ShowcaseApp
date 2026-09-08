"""Offline tests: py -3 -m unittest discover -s scripts -p test_build_ios.py -v."""

import hashlib
import importlib.util
import io
from pathlib import Path
import plistlib
import stat
import subprocess
import sys
import tempfile
import unittest
from unittest import mock
import zipfile


SCRIPT = Path(__file__).with_name("build_ios.py")
spec = importlib.util.spec_from_file_location("build_ios", SCRIPT)
build_ios = importlib.util.module_from_spec(spec) if SCRIPT.exists() else None
if build_ios is not None:
    spec.loader.exec_module(build_ios)


def library(identifier, variant=None, platform="ios", architectures=None):
    result = {
        "LibraryIdentifier": identifier,
        "LibraryPath": "opencv2.framework",
        "SupportedPlatform": platform,
        "SupportedArchitectures": architectures or ["arm64"],
    }
    if variant is not None:
        result["SupportedPlatformVariant"] = variant
    return result


def static_archive():
    # One ordinary ar member, with a correctly sized 60-byte header.
    return b"!<arch>\n" + b"test.o/         0           0     0     644     4         `\n" + b"OBJ!"


class BuildIosTests(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(build_ios, "build_ios.py must implement the build contract")
        self.temp = tempfile.TemporaryDirectory(prefix="ios build tests ")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def archive(self, extras=(), versioned=False, libraries=None):
        path = self.root / "opencv.zip"
        libraries = libraries or [library("device-unusual-name"), library("sim-unusual-name", "simulator")]
        with zipfile.ZipFile(path, "w") as archive:
            archive.writestr("build/opencv2.xcframework/Info.plist", plistlib.dumps({"AvailableLibraries": libraries}))
            for item in libraries:
                prefix = "build/opencv2.xcframework/" + item["LibraryIdentifier"] + "/opencv2.framework/"
                if versioned:
                    archive.writestr(prefix + "Versions/A/Headers/core.hpp", b"// header")
                    archive.writestr(prefix + "Versions/A/opencv2", static_archive())
                    for name, target in [("Versions/Current", "A"), ("Headers", "Versions/Current/Headers"), ("opencv2", "Versions/Current/opencv2")]:
                        info = zipfile.ZipInfo(prefix + name)
                        info.create_system = 3
                        info.external_attr = (stat.S_IFLNK | 0o777) << 16
                        archive.writestr(info, target)
                else:
                    archive.writestr(prefix + "Headers/core.hpp", b"// header")
                    archive.writestr(prefix + "opencv2", static_archive())
            for name, data, link in extras:
                info = zipfile.ZipInfo(name)
                # ZipInfo normalizes Windows separators at construction time.
                # Preserve the malicious spelling in the ZIP central directory.
                info.filename = name
                if link:
                    info.create_system = 3
                    info.external_attr = (stat.S_IFLNK | 0o777) << 16
                archive.writestr(info, data)
        return path

    def extract(self, path, target="iosArm64"):
        return build_ios.extract_framework(path, target, self.root / "output", hashlib.sha256(path.read_bytes()).hexdigest())

    def test_existing_archive_is_verified_without_network(self):
        path = self.archive()
        with mock.patch.object(build_ios.urllib.request, "urlopen", side_effect=AssertionError("network must not be used")):
            build_ios.ensure_archive(path, hashlib.sha256(path.read_bytes()).hexdigest())
            with self.assertRaisesRegex(build_ios.BuildError, "SHA-256"):
                build_ios.ensure_archive(path, "0" * 64)

    def test_download_is_verified_before_cache_publication(self):
        path = self.root / "cache" / "opencv.zip"
        with mock.patch.object(build_ios.urllib.request, "urlopen", return_value=io.BytesIO(b"bad")):
            with self.assertRaisesRegex(build_ios.BuildError, "SHA-256"):
                build_ios.ensure_archive(path, "0" * 64)
        self.assertFalse(path.exists())
        with mock.patch.object(build_ios.urllib.request, "urlopen", return_value=io.BytesIO(b"good")):
            build_ios.ensure_archive(path, hashlib.sha256(b"good").hexdigest())
        self.assertEqual(path.read_bytes(), b"good")

    def test_bad_archive_digest_prevents_extraction(self):
        path = self.archive()
        output = self.root / "output"
        with self.assertRaisesRegex(build_ios.BuildError, "SHA-256"):
            build_ios.extract_framework(path, "iosArm64", output, "0" * 64)
        self.assertFalse(output.exists())

    def test_model_size_digest_and_deterministic_header(self):
        model = self.root / "model.onnx"
        model.write_bytes(b"\x00\x7f\xff")
        output = self.root / "generated" / "YuNetModelBytes.h"
        digest = hashlib.sha256(model.read_bytes()).hexdigest()
        build_ios.generate_model_header(model, output, digest, 3)
        first = output.read_bytes()
        self.assertIn(b"constexpr unsigned char kYuNetModelBytes[]", first)
        self.assertIn(b"0x00, 0x7f, 0xff", first)
        self.assertIn(b"constexpr size_t kYuNetModelSize = sizeof(kYuNetModelBytes);", first)
        build_ios.generate_model_header(model, output, digest, 3)
        self.assertEqual(output.read_bytes(), first)
        for expected_digest, expected_size in [("0" * 64, 3), (digest, 4)]:
            with self.subTest(digest=expected_digest, size=expected_size):
                with self.assertRaises(build_ios.BuildError):
                    build_ios.generate_model_header(model, output, expected_digest, expected_size)
                self.assertEqual(output.read_bytes(), first)

    def test_device_and_simulator_selected_by_metadata_and_only_one_extracted(self):
        path = self.archive(libraries=[library("device-unusual-name"), library("sim-unusual-name", "simulator"), library("catalyst", "maccatalyst"), library("mac", platform="macos")])
        for target, identifier in [("iosArm64", "device-unusual-name"), ("iosSimulatorArm64", "sim-unusual-name")]:
            with self.subTest(target=target):
                framework = self.extract(path, target)
                self.assertEqual(framework, self.root / "output" / "opencv2.xcframework" / identifier / "opencv2.framework")
                self.assertEqual((framework / "Headers/core.hpp").read_bytes(), b"// header")
                self.assertEqual(sorted(p.name for p in framework.parent.parent.iterdir()), sorted(["Info.plist", identifier]))

    def test_missing_and_ambiguous_arm64_slice_rejected(self):
        cases = [[library("intel", architectures=["x86_64"])], [library("one"), library("two")], [library("sim", "simulator")]]
        for libraries in cases:
            with self.subTest(libraries=libraries):
                with self.assertRaises(build_ios.BuildError):
                    self.extract(self.archive(libraries=libraries))

    def test_versioned_framework_symlinks_materialized_cross_platform(self):
        framework = self.extract(self.archive(versioned=True))
        self.assertEqual((framework / "Headers/core.hpp").read_bytes(), b"// header")
        self.assertEqual((framework / "opencv2").read_bytes(), static_archive())
        self.assertFalse((framework / "Headers").is_symlink())

    def test_archive_traversal_rejected(self):
        for name in ["../escape", "/escape", "C:/escape", "build/../../escape", "build\\escape", "build/NUL", "build/file:stream", "build/nul\x00suffix"]:
            with self.subTest(name=name):
                with self.assertRaises(build_ios.BuildError):
                    self.extract(self.archive([(name, b"evil", False)]))
        self.assertFalse((self.root / "escape").exists())

    def test_unsafe_broken_and_cyclic_symlinks_rejected(self):
        prefix = "build/opencv2.xcframework/device-unusual-name/opencv2.framework/"
        for target in ["../../../../escape", "/etc/passwd", "C:/escape", "missing", "loop", "."]:
            with self.subTest(target=target):
                with self.assertRaises(build_ios.BuildError):
                    self.extract(self.archive([(prefix + "loop", target, True)]))

    def test_plist_library_path_cannot_escape_slice(self):
        item = library("device")
        item["LibraryPath"] = "../../evil.framework"
        with self.assertRaises(build_ios.BuildError):
            self.extract(self.archive(libraries=[item]))

    def test_symlink_parent_and_duplicate_entries_rejected(self):
        prefix = "build/opencv2.xcframework/device-unusual-name/opencv2.framework/"
        cases = [
            [(prefix + "alias", "Headers", True), (prefix + "alias/extra.hpp", b"evil", False)],
            [(prefix + "HEADERS/core.hpp", b"collision", False)],
            [(prefix + "HEADERS/extra.hpp", b"implicit directory collision", False)],
        ]
        for extras in cases:
            with self.subTest(extras=extras), self.assertRaises(build_ios.BuildError):
                self.extract(self.archive(extras))

    def test_prepare_only_cli_needs_no_xcode_and_validates_inputs(self):
        path = self.archive()
        model = self.root / "model.onnx"
        model.write_bytes(b"model")
        output = self.root / "cli-output"
        with mock.patch.multiple(build_ios, DEFAULT_ARCHIVE=path, MODEL_PATH=model,
                                 MODEL_SIZE=5, MODEL_SHA256=hashlib.sha256(b"model").hexdigest(),
                                 OPENCV_SHA256=hashlib.sha256(path.read_bytes()).hexdigest()), \
                mock.patch.object(build_ios.platform, "system", return_value="Windows"), \
                mock.patch.object(build_ios.subprocess, "run", side_effect=AssertionError("must not invoke native tools")), \
                mock.patch("sys.stdout", new_callable=io.StringIO):
            self.assertEqual(build_ios.main(["--target", "iosArm64", "--output", str(output), "--prepare-only"]), 0)
            self.assertTrue((output / "generated/YuNetModelBytes.h").is_file())
            self.assertTrue((output / "opencv2.xcframework/device-unusual-name/opencv2.framework/opencv2").is_file())
            model.write_bytes(b"WRONG")
            self.assertEqual(build_ios.main(["--target", "iosArm64", "--output", str(output), "--prepare-only"]), 1)

    def test_cli_rejects_relative_output_without_downloading(self):
        result = subprocess.run([sys.executable, "-B", str(SCRIPT), "--target", "iosArm64", "--output", "relative", "--prepare-only"], capture_output=True, text=True)
        self.assertEqual(result.returncode, 2)
        self.assertIn("absolute", result.stderr)

    def test_static_archive_validation_rejects_dynamic_thin_and_truncated_files(self):
        path = self.root / "lib.a"
        path.write_bytes(static_archive())
        build_ios.validate_static_archive(path)
        for content in [b"\xcf\xfa\xed\xfe" + b"dynamic", b"!<thin>\n", b"!<arch>\n", static_archive()[:-1]]:
            with self.subTest(content=content):
                path.write_bytes(content)
                with self.assertRaises(build_ios.BuildError):
                    build_ios.validate_static_archive(path)

    def test_compile_and_archive_commands_for_both_targets(self):
        framework = self.extract(self.archive())
        module = self.root / "module with spaces"
        source = module / "src/iosMain/cpp/FaceDetectionBridge.cpp"
        source.parent.mkdir(parents=True)
        source.write_text("// bridge")
        header = module / "src/nativeInterop/cinterop/FaceDetectionBridge.h"
        header.parent.mkdir(parents=True)
        header.write_text("// bridge header")
        output = self.root / "output"
        for target, sdk, triple, archs, fat in [
            ("iosArm64", "iphoneos", "arm64-apple-ios13.0", "arm64", False),
            ("iosSimulatorArm64", "iphonesimulator", "arm64-apple-ios13.0-simulator", "x86_64 arm64", True),
            ("iosArm64", "iphoneos", "arm64-apple-ios13.0", "arm64", True),
        ]:
            calls = []
            (framework / "opencv2").write_bytes(b"\xca\xfe\xba\xbe" + b"fat fixture" if fat else static_archive())

            def run(argv, **kwargs):
                self.assertIsInstance(argv, list)
                self.assertNotIn("shell", kwargs)
                calls.append(argv)
                if "--show-sdk-path" in argv:
                    return subprocess.CompletedProcess(argv, 0, "/SDK with spaces\n", "")
                if "-archs" in argv:
                    return subprocess.CompletedProcess(argv, 0, archs + "\n", "")
                if "nm" in argv:
                    return subprocess.CompletedProcess(argv, 0, "00000000 T _showcase_face_inspect\n", "")
                if "-output" in argv:
                    Path(argv[argv.index("-output") + 1]).write_bytes(static_archive())
                elif "-o" in argv:
                    dest = Path(argv[argv.index("-o") + 1])
                    dest.write_bytes(static_archive() if "libtool" in argv else b"object")
                return subprocess.CompletedProcess(argv, 0, "", "")

            with self.subTest(target=target), mock.patch.object(build_ios.subprocess, "run", side_effect=run), mock.patch.object(build_ios.platform, "system", return_value="Darwin"):
                build_ios.build_native(module, target, output, framework)
            compile_argv = next(c for c in calls if "clang++" in c)
            self.assertEqual(compile_argv, ["xcrun", "--sdk", sdk, "clang++", "-std=c++17", "-O2", "-fvisibility=hidden", "-target", triple, "-isysroot", "/SDK with spaces", "-I", str(header.parent), "-I", str(output / "generated"), "-F", str(framework.parent), "-c", str(source), "-o", str(output / "bridge.o")])
            self.assertIn(["xcrun", "ld-classic", "-r", "-d", "-arch", "arm64", str(output / "bridge.o"), str(output / "libopencv2.a"), "-o", str(output / "bridge-combined.o")], calls)
            self.assertIn(["xcrun", "ld", "-r", "-arch", "arm64", "-exported_symbol", "_showcase_face_inspect", str(output / "bridge-combined.o"), "-o", str(output / "bridge-isolated.o")], calls)
            self.assertIn(["xcrun", "libtool", "-static", "-o", str(output / "libShowcaseFaceDetection.a"), str(output / "bridge-isolated.o")], calls)
            self.assertEqual(any("-thin" in c for c in calls), fat)
            if fat:
                self.assertIn(["xcrun", "lipo", str(framework / "opencv2"), "-thin", "arm64", "-output", str(output / "libopencv2.a")], calls)
            self.assertEqual((output / "libopencv2.a").read_bytes(), static_archive())
            build_ios.validate_static_archive(output / "libShowcaseFaceDetection.a")

    def test_non_darwin_build_fails_before_invoking_tools(self):
        with mock.patch.object(build_ios.platform, "system", return_value="Windows"), mock.patch.object(build_ios.subprocess, "run", side_effect=AssertionError("must not spawn")):
            with self.assertRaisesRegex(build_ios.BuildError, "Darwin|macOS"):
                build_ios.build_native(self.root, "iosArm64", self.root, self.root)

    def test_failed_native_command_is_reported(self):
        with mock.patch.object(build_ios.subprocess, "run", side_effect=subprocess.CalledProcessError(1, ["xcrun"], stderr="compiler failed")):
            with self.assertRaisesRegex(build_ios.BuildError, "compiler failed"):
                build_ios.run_command(["xcrun", "clang++"])


if __name__ == "__main__":
    unittest.main()
