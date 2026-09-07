#!/usr/bin/env python3
"""Verify a trimmed desktop OpenCV package and run a dependency-free JDK 17 smoke.

Use --self-test for offline verifier regression tests (no JDK or native build).
"""

import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import stat
import struct
import subprocess
import sys
import tempfile
import unittest
import zipfile

import build_desktop as build


BUILD_INFO = "META-INF/opencv-yunet-desktop-build.json"
REQUIRED_CLASSES = {
    f"org/opencv/{name}.class" for name in (
        "core/Core", "core/Mat", "core/MatOfByte", "core/Size",
        "dnn/Dnn", "imgproc/Imgproc", "imgcodecs/Imgcodecs", "objdetect/FaceDetectorYN",
    )
}
MODEL = build.MODULE / "src/jvmSharedMain/resources/com/alpha/facedetection/models/face_detection_yunet_2026may.onnx"
MODEL_SHA256 = "ebafce4e3c118d6554634be5c27ab333b4c047a9a8c3faf1d7cf93101c22f0f0"


def require(condition, message):
    if not condition:
        raise ValueError(message)


def read_jar(path):
    """Read regular entries only; never extract archive-controlled paths."""
    files, seen = {}, set()
    with zipfile.ZipFile(path) as archive:
        for entry in archive.infolist():
            require(entry.orig_filename == entry.filename, "Non-canonical JAR entry")
            name = entry.filename
            clean = name.removesuffix("/") if entry.is_dir() else name
            parts = clean.split("/")
            require(clean and not PurePosixPath(name).is_absolute()
                    and not any(part in ("", ".", "..") for part in parts)
                    and not any(char in name for char in "\\:\x00"), f"Unsafe JAR entry: {name}")
            require(name.casefold() not in seen, f"Duplicate JAR entry: {name}")
            seen.add(name.casefold())
            mode = stat.S_IFMT(entry.external_attr >> 16)
            require(mode in (0, stat.S_IFREG, stat.S_IFDIR), f"Non-regular JAR entry: {name}")
            if not entry.is_dir():
                files[name] = archive.read(entry)
    return files


def properties(data):
    result = {}
    for line in data.decode("ascii").splitlines():
        if not line or line.startswith(("#", "!")):
            continue
        key, sep, value = line.partition("=")
        require(sep and key not in result, "Invalid or duplicate native.properties key")
        result[key] = value
    return result


def is_license(name):
    return name.startswith("META-INF/licenses/opencv/") and not re.search(
        r"\.(dll|dylib|so(?:\.[0-9]+)*|class|jar|a|lib)$", name, re.I
    )


def verify_packages(output, target):
    """Validate both JARs and sidecar; return the single verified host library."""
    require(target == build.host_platform(), "Runtime smoke must target the current host")
    common_path = output / f"{build.ARTIFACT}.jar"
    native_path = output / f"{build.ARTIFACT}-{target}.jar"
    common, native = read_jar(common_path), read_jar(native_path)
    require(REQUIRED_CLASSES <= common.keys(), "Missing required Java bindings")
    for name, data in common.items():
        if name.endswith(".class"):
            parts = name.split("/")
            require(len(parts) >= 4 and parts[:2] == ["org", "opencv"]
                    and parts[2] in build.CLOSURE | {"utils"}, f"Unwanted Java module: {name}")
            require(len(data) >= 8 and data[:4] == b"\xca\xfe\xba\xbe"
                    and 45 <= int.from_bytes(data[6:8], "big") <= 61,
                    f"Invalid or non-JDK-17-compatible class: {name}")
        else:
            require(is_license(name), f"Unexpected common JAR resource: {name}")

    library = build.PLATFORMS[target]
    prefix = f"com/alpha/facedetection/native/{target}/"
    required = {prefix + library, prefix + "native.properties", BUILD_INFO}
    require(required <= native.keys(), "Missing native library, properties or build provenance")
    for name in native:
        require(name in required or is_license(name), f"Unexpected native JAR resource: {name}")
    for files in (common, native):
        require(bool(files.get("META-INF/licenses/opencv/LICENSE")), "Missing OpenCV license")
    data = native[prefix + library]
    native_hash = hashlib.sha256(data).hexdigest()
    expected_props = {"version": build.VERSION, "platform": target, "library": library, "sha256": native_hash}
    require(properties(native[prefix + "native.properties"]) == expected_props,
            "Native properties/version/platform/checksum mismatch")
    # Reuse the builder's header, architecture, module-closure and JNI-export checks.
    # This does not run any compiler or dependency inspection tools.
    build.validate_binary(data, target)
    metadata = json.loads(native[BUILD_INFO])
    require(isinstance(metadata, dict), "Invalid native build provenance")
    expected = {
        "version": build.VERSION, "platform": target, "library": library,
        "source_url": build.SOURCE_URL, "source_sha256": build.SOURCE_SHA256,
        "modules": sorted(build.CLOSURE), "native_sha256": native_hash, "native_bytes": len(data),
        "java_compiler": build.JAVA_COMPILER, "generator_patch": build.GENERATOR_PATCH,
    }
    for key, value in expected.items():
        require(metadata.get(key) == value, f"Native provenance mismatch: {key}")
    options = metadata.get("cmake_options")
    require(isinstance(options, dict), "Missing CMake provenance")
    variable_paths = {"JAVA_HOME", "PYTHON_DEFAULT_EXECUTABLE", "PYTHON3_EXECUTABLE"}
    for key, value in build.cmake_options(Path("jdk")).items():
        if key not in variable_paths:
            require(options.get(key) == value, f"Unexpected CMake option: {key}")
    require(isinstance(metadata.get("dependencies"), list)
            and all(isinstance(dep, str) for dep in metadata["dependencies"]), "Missing dependency provenance")
    sidecar = json.loads(native_path.with_suffix(".json").read_text(encoding="utf-8"))
    expected_sidecar = dict(metadata, java_jar_sha256=build.digest(common_path),
                            native_jar_sha256=build.digest(native_path), native_jar_bytes=native_path.stat().st_size)
    require(sidecar == expected_sidecar, "Sidecar provenance or JAR checksum mismatch")
    return data


def run_smoke(output, target, jdk):
    data = verify_packages(output, target)
    require(MODEL.stat().st_size == 229738 and build.digest(MODEL) == MODEL_SHA256,
            "Bundled YuNet model size/checksum mismatch")
    fixtures = [build.MODULE / "src/desktopTest/resources/opencv/lena.jpg",
                build.MODULE / "src/iosTest/resources/blank.png",
                build.MODULE / "src/iosTest/resources/blank.jpg"]
    for path in fixtures:
        require(path.is_file() and path.stat().st_size > 0, f"Missing image fixture: {path}")
    extension = ".exe" if target.startswith("windows") else ""
    java, javac = [jdk / "bin" / (tool + extension) for tool in ("java", "javac")]
    for tool in (java, javac):
        version = subprocess.check_output([str(tool), "-version"], stderr=subprocess.STDOUT, text=True)
        require(re.search(r'(?:version\s+"|javac\s+)17(?:[.\s"+]|$)', version), f"JDK 17 required: {version}")
        print(version.strip(), flush=True)
    # The native JAR never enters the classpath, and only this verified library is
    # materialized. A fresh JVM loads it directly; no Gradle or loader dependency.
    with tempfile.TemporaryDirectory(prefix="opencv desktop smoke ") as temporary:
        root = Path(temporary)
        library = root / build.PLATFORMS[target]
        library.write_bytes(data)
        classes = root / "classes"
        classes.mkdir()
        common = output / f"{build.ARTIFACT}.jar"
        subprocess.run([str(javac), "--release", "17", "-encoding", "UTF-8", "-proc:none",
                        "-classpath", str(common), "-d", str(classes),
                        str(Path(__file__).with_name("DesktopRuntimeSmoke.java"))], check=True, timeout=120)
        subprocess.run([str(java), "-Djava.awt.headless=true", "-classpath", os.pathsep.join(map(str, (classes, common))),
                        "DesktopRuntimeSmoke", str(library), str(MODEL), *map(str, fixtures)],
                       cwd=root, check=True, timeout=180)
    print(f"Verified {target}: package provenance, SHA-256, Java bindings and real YuNet runtime", flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=build.MODULE / "desktop-runtime")
    parser.add_argument("--platform", choices=sorted(build.PLATFORMS), help="Assert the CI matrix matches this host")
    parser.add_argument("--jdk", type=Path, default=os.environ.get("JAVA_HOME"))
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    if args.self_test:
        suite = unittest.defaultTestLoader.loadTestsFromTestCase(VerificationTests)
        return 0 if unittest.TextTestRunner(verbosity=2).run(suite).wasSuccessful() else 1
    if not args.jdk:
        parser.error("Provide JDK 17 via JAVA_HOME or --jdk")
    try:
        target = build.host_platform()
        require(args.platform in (None, target), f"Expected {args.platform}, running on {target}")
        run_smoke(args.output.resolve(), target, args.jdk.resolve())
        return 0
    except (ValueError, OSError, KeyError, struct.error, zipfile.BadZipFile, subprocess.SubprocessError) as error:
        print(f"Desktop verification failed: {error}", file=sys.stderr)
        return 1


class VerificationTests(unittest.TestCase):
    """Malformed packages must fail before extraction or starting Java."""

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="desktop verifier tests ")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.target = build.host_platform()
        self.prefix = f"com/alpha/facedetection/native/{self.target}/"
        self.library = build.PLATFORMS[self.target]
        data = bytearray(128)
        if self.target.startswith("windows"):
            data[:2] = b"MZ"
            struct.pack_into("<I", data, 60, 64)
            data[64:68] = b"PE\0\0"
            struct.pack_into("<H", data, 68, 0x8664)
        elif self.target.startswith("linux"):
            data[:6] = b"\x7fELF\x02\x01"
            struct.pack_into("<H", data, 18, 183 if self.target.endswith("aarch64") else 62)
        else:
            data[:4] = b"\xcf\xfa\xed\xfe"
            struct.pack_into("<I", data, 4, 0x100000C if self.target.endswith("aarch64") else 0x1000007)
        self.binary = bytes(data) + (
            b"To be built: calib3d core dnn features2d flann imgcodecs imgproc java objdetect\n"
            b"Java_org_opencv_objdetect_FaceDetectorYN_create_10\0"
            b"Java_org_opencv_objdetect_FaceDetectorYN_detect_10\0"
            b"Java_org_opencv_imgcodecs_Imgcodecs_imdecode_10\0"
        )
        native_hash = hashlib.sha256(self.binary).hexdigest()
        self.metadata = {
            "version": build.VERSION, "platform": self.target,
            "source_url": build.SOURCE_URL, "source_sha256": build.SOURCE_SHA256,
            "modules": sorted(build.CLOSURE), "library": self.library,
            "java_compiler": build.JAVA_COMPILER, "generator_patch": build.GENERATOR_PATCH,
            "native_bytes": len(self.binary), "native_sha256": native_hash,
            "dependencies": [], "cmake_options": build.cmake_options(self.root / "jdk"),
        }
        self.common = {name: b"\xca\xfe\xba\xbe\x00\x00\x00\x37" for name in REQUIRED_CLASSES}
        self.common["META-INF/licenses/opencv/LICENSE"] = b"test license"
        self.native = {
            self.prefix + self.library: self.binary,
            self.prefix + "native.properties": (
                f"version={build.VERSION}\nplatform={self.target}\nlibrary={self.library}\nsha256={native_hash}\n"
            ).encode(),
            "META-INF/licenses/opencv/LICENSE": b"test license",
        }

    def package(self):
        self.native[BUILD_INFO] = json.dumps(self.metadata).encode()
        common = self.root / f"{build.ARTIFACT}.jar"
        native = self.root / f"{build.ARTIFACT}-{self.target}.jar"
        build.pack_zip(common, self.common)
        build.pack_zip(native, self.native)
        sidecar = dict(self.metadata, java_jar_sha256=build.digest(common),
                       native_jar_sha256=build.digest(native), native_jar_bytes=native.stat().st_size)
        native.with_suffix(".json").write_text(json.dumps(sidecar), encoding="utf-8")
        return native

    def verify(self):
        return verify_packages(self.root, self.target)

    def test_valid_package_returns_only_host_library(self):
        self.package()
        self.assertEqual(self.verify(), self.binary)

    def test_rejects_other_platform_or_extra_native(self):
        for name in ["com/alpha/facedetection/native/foreign/lib.so", "extra.dll",
                     "META-INF/licenses/opencv/hidden.dylib", self.prefix + "second.so"]:
            with self.subTest(name=name):
                self.native[name] = b"foreign native"
                self.package()
                with self.assertRaises(ValueError):
                    self.verify()
                del self.native[name]

    def test_rejects_native_in_common_or_classes_in_classifier(self):
        self.common[self.prefix + self.library] = self.binary
        self.package()
        with self.assertRaises(ValueError):
            self.verify()
        del self.common[self.prefix + self.library]
        self.native["org/opencv/core/Mat.class"] = self.common["org/opencv/core/Mat.class"]
        self.package()
        with self.assertRaises(ValueError):
            self.verify()

    def test_rejects_corruption_even_with_updated_jar_hash(self):
        self.native[self.prefix + self.library] += b"tampered"
        self.package()
        with self.assertRaisesRegex(ValueError, "native|Native"):
            self.verify()

    def test_rejects_wrong_provenance_and_build_options(self):
        for key, bad in [("source_sha256", "0" * 64), ("platform", "linux-foreign"),
                         ("version", "4.11.0"), ("modules", ["core"]),
                         ("cmake_options", {"WITH_CUDA": "ON"})]:
            with self.subTest(key=key):
                original = self.metadata[key]
                self.metadata[key] = bad
                self.package()
                with self.assertRaises(ValueError):
                    self.verify()
                self.metadata[key] = original

    def test_rejects_sidecar_mismatch(self):
        native = self.package()
        path = native.with_suffix(".json")
        original = json.loads(path.read_text(encoding="utf-8"))
        for key, bad in [("java_jar_sha256", "0" * 64), ("native_jar_sha256", "0" * 64),
                         ("native_jar_bytes", 0), ("source_url", "https://invalid.example")]:
            with self.subTest(key=key):
                path.write_text(json.dumps(dict(original, **{key: bad})), encoding="utf-8")
                with self.assertRaises(ValueError):
                    self.verify()

    def test_rejects_missing_unwanted_or_newer_java_classes(self):
        for name, data in [("org/opencv/videoio/VideoCapture.class", b"class"),
                           ("org/opencv/core/Mat.class", b"\xca\xfe\xba\xbe\x00\x00\x00\x41")]:
            with self.subTest(name=name):
                original = self.common.get(name)
                self.common[name] = data
                self.package()
                with self.assertRaises(ValueError):
                    self.verify()
                if original is None:
                    del self.common[name]
                else:
                    self.common[name] = original
        del self.common["org/opencv/objdetect/FaceDetectorYN.class"]
        self.package()
        with self.assertRaises(ValueError):
            self.verify()

    def test_rejects_duplicate_properties_and_archive_paths(self):
        self.native[self.prefix + "native.properties"] += b"platform=foreign\n"
        self.package()
        with self.assertRaises(ValueError):
            self.verify()
        path = self.root / "unsafe.jar"
        for name in ["../escape", "/absolute", "C:/escape", "a\\b", "a/../b"]:
            with self.subTest(name=name):
                with zipfile.ZipFile(path, "w") as archive:
                    entry = zipfile.ZipInfo(name)
                    entry.filename = name  # Preserve hostile separators on Windows.
                    archive.writestr(entry, b"bad")
                with self.assertRaises(ValueError):
                    read_jar(path)
        with zipfile.ZipFile(path, "w") as archive:
            archive.writestr("File", b"first")
            archive.writestr("file", b"second")
        with self.assertRaises(ValueError):
            read_jar(path)


if __name__ == "__main__":
    sys.exit(main())
