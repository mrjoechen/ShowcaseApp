#!/usr/bin/env python3
"""Build pinned, CPU-only OpenCV Java bindings and one desktop native JAR.

Run explicitly on each target host, never as part of Gradle sync/application build.
Requires JDK 17, Python 3, CMake, Ninja and a native C++ compiler (MSVC on Windows).
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import shutil
import struct
import subprocess
import sys
import tempfile
import urllib.request
import zipfile

MODULE = Path(__file__).resolve().parents[1]
VERSION = "4.12.0"
JAVA_COMPILER = "javac 17.0.20.1"
GENERATOR_PATCH = "sorted-java-header-walk-v1"
ARTIFACT = "opencv-yunet-desktop-4.12.0-1"
SOURCE_URL = "https://codeload.github.com/opencv/opencv/zip/refs/tags/4.12.0"
SOURCE_SHA256 = "fa3faf7581f1fa943c9e670cf57dd6ba1c5b4178f363a188a2c8bff1eb28b7e4"
MODULES = "core,imgproc,imgcodecs,dnn,objdetect,java"
CLOSURE = {"core", "imgproc", "imgcodecs", "dnn", "objdetect", "java", "calib3d", "features2d", "flann"}
PLATFORMS = {
    "windows-x86_64": "opencv_java4120.dll",
    "macos-x86_64": "libopencv_java4120.dylib",
    "macos-aarch64": "libopencv_java4120.dylib",
    "linux-x86_64": "libopencv_java4120.so",
    "linux-aarch64": "libopencv_java4120.so",
}


def host_platform(system=None, machine=None):
    system = (system or platform.system()).lower()
    machine = (machine or platform.machine()).lower()
    os_name = {"windows": "windows", "darwin": "macos", "linux": "linux"}.get(system)
    arch = {"amd64": "x86_64", "x86_64": "x86_64", "arm64": "aarch64", "aarch64": "aarch64"}.get(machine)
    target = f"{os_name}-{arch}"
    if target not in PLATFORMS:
        raise ValueError(f"Unsupported desktop build host: {system}/{machine}")
    return target


def digest(path):
    value = hashlib.sha256()
    with Path(path).open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            value.update(block)
    return value.hexdigest()


def extract_source(archive_path, destination):
    if digest(archive_path) != SOURCE_SHA256:
        raise ValueError("OpenCV source SHA-256 mismatch")
    destination = Path(destination).resolve()
    with zipfile.ZipFile(archive_path) as archive:
        for entry in archive.infolist():
            path = destination / entry.filename
            if not path.resolve().is_relative_to(destination) or "\\" in entry.filename:
                raise ValueError("Unsafe source archive path")
            if entry.is_dir():
                continue
            data = archive.read(entry)
            path.parent.mkdir(parents=True, exist_ok=True)
            if not path.exists() or path.read_bytes() != data:
                path.write_bytes(data)
    return destination / f"opencv-{VERSION}"


def command(argv, env, log=None):
    argv = list(map(str, argv))
    argv[0] = shutil.which(argv[0], path=env.get("PATH")) or argv[0]
    if log:
        print(f"Running {argv[0]} (log: {log})", flush=True)
        with Path(log).open("w", encoding="utf-8") as stream:
            result = subprocess.run(list(map(str, argv)), env=env, stdout=stream, stderr=subprocess.STDOUT)
        if result.returncode:
            raise RuntimeError(Path(log).read_text(errors="replace")[-8000:])
        return ""
    return subprocess.check_output(list(map(str, argv)), env=env, text=True, stderr=subprocess.STDOUT)


def patch_java_generator(source):
    # Upstream os.walk order varies by filesystem, changing Objdetect method ordering.
    path = source / "modules/java/generator/gen_java.py"
    text = path.read_text(encoding="utf-8")
    anchor = "            for root, dirnames, filenames in os.walk(os.path.join(module_location, 'include')):\n"
    replacement = anchor + "               dirnames.sort()\n               filenames.sort()\n"
    if replacement in text:
        return
    if text.count(anchor) != 1:
        raise ValueError("Pinned Java generator header-walk anchor changed")
    path.write_text(text.replace(anchor, replacement), encoding="utf-8", newline="\n")


def compiler_environment():
    env = os.environ.copy()
    if platform.system() == "Windows" and not shutil.which("cl.exe"):
        vswhere = Path(os.environ.get("ProgramFiles(x86)", "C:/Program Files (x86)")) / "Microsoft Visual Studio/Installer/vswhere.exe"
        location = command([vswhere, "-latest", "-products", "*", "-requires", "Microsoft.VisualStudio.Component.VC.Tools.x86.x64", "-property", "installationPath"], env).strip()
        setup = Path(location) / "Common7/Tools/VsDevCmd.bat"
        if not location or not setup.is_file():
            raise RuntimeError("Install Visual Studio C++ Build Tools or run from its x64 developer shell")
        # The fixed command only obtains compiler environment variables; never echo them.
        raw = subprocess.check_output(
            f'cmd.exe /d /s /c ""{setup}" -no_logo -arch=amd64 -host_arch=amd64 >nul && set"',
            env=env, text=True, stderr=subprocess.STDOUT,
        )
        for line in raw.splitlines():
            key, sep, value = line.partition("=")
            if sep and key:
                env[key.upper()] = value
    return env


def cmake_options(jdk):
    options = {
        "CMAKE_BUILD_TYPE": "Release", "BUILD_LIST": MODULES,
        "BUILD_SHARED_LIBS": "OFF", "BUILD_FAT_JAVA_LIB": "ON",
        "BUILD_WITH_STATIC_CRT": "ON", "BUILD_TESTS": "OFF", "BUILD_PERF_TESTS": "OFF",
        "BUILD_EXAMPLES": "OFF", "BUILD_DOCS": "OFF", "BUILD_opencv_apps": "OFF",
        "BUILD_JAVA": "ON", "OPENCV_JAVA_SDK_BUILD_TYPE": "JAVA",
        "OPENCV_EXTRA_JAVA_COMPILE_FLAGS": "--release;11",
        "JAVA_HOME": jdk.as_posix(), "PYTHON_DEFAULT_EXECUTABLE": Path(sys.executable).as_posix(),
        "PYTHON3_EXECUTABLE": Path(sys.executable).as_posix(),
        "WITH_PROTOBUF": "ON", "BUILD_PROTOBUF": "ON",
        "WITH_JPEG": "ON", "BUILD_JPEG": "ON", "WITH_PNG": "ON", "BUILD_PNG": "ON", "BUILD_ZLIB": "ON",
        "CPU_DISPATCH": "", "ENABLE_LTO": "OFF",
    }
    for feature in ("OPENCL", "IPP", "ITT", "TBB", "QUIRC", "FLATBUFFERS", "KLEIDICV", "ADE", "CUDA", "CUDNN", "OPENVINO", "FFMPEG", "GSTREAMER", "GTK", "QT", "VTK", "OPENGL", "LAPACK", "EIGEN", "TIFF", "WEBP", "OPENJPEG", "JASPER", "OPENEXR", "AVIF", "1394", "V4L", "MSMF", "DSHOW", "GPHOTO2"):
        options[f"WITH_{feature}"] = "OFF"
    for feature in ("TFLITE", "OPENCL", "CUDA"):
        options[f"OPENCV_DNN_{feature}"] = "OFF"
    for codec in ("HDR", "SUNRASTER", "PXM", "PFM", "GIF"):
        options[f"WITH_IMGCODEC_{codec}"] = "OFF"
    return options


def validate_binary(data, target):
    expected_arm = target.endswith("aarch64")
    if target.startswith("windows"):
        if data[:2] != b"MZ":
            raise ValueError("Expected PE DLL")
        pe = struct.unpack_from("<I", data, 60)[0]
        if data[pe:pe+4] != b"PE\0\0" or struct.unpack_from("<H", data, pe+4)[0] != 0x8664:
            raise ValueError("Wrong Windows native architecture")
    elif target.startswith("linux"):
        if data[:6] != b"\x7fELF\x02\x01" or struct.unpack_from("<H", data, 18)[0] != (183 if expected_arm else 62):
            raise ValueError("Wrong Linux native architecture")
    else:
        if data[:4] != b"\xcf\xfa\xed\xfe" or struct.unpack_from("<I", data, 4)[0] != (0x100000C if expected_arm else 0x1000007):
            raise ValueError("Expected single-architecture macOS native library")
    built = re.search(rb"To be built:([^\r\n\x00]+)", data)
    if not built or set(built.group(1).decode().split()) != CLOSURE:
        raise ValueError("Unexpected compiled OpenCV module closure")
    for symbol in (b"Java_org_opencv_objdetect_FaceDetectorYN_create_10", b"Java_org_opencv_objdetect_FaceDetectorYN_detect_10", b"Java_org_opencv_imgcodecs_Imgcodecs_imdecode_10"):
        if symbol + b"\0" not in data:
            raise ValueError(f"Missing required JNI export: {symbol!r}")


def native_dependencies(binary, target, env):
    if target.startswith("windows"):
        output = command(["dumpbin.exe", "/dependents", binary], env)
        deps = re.findall(r"^\s+([\w.-]+\.dll)\s*$", output, re.I | re.M)
        allowed = {"kernel32.dll", "user32.dll", "advapi32.dll", "ole32.dll", "oleaut32.dll", "ws2_32.dll", "gdi32.dll", "comdlg32.dll", "shell32.dll", "bcrypt.dll", "ntdll.dll"}
        if not deps or any(dep.lower() not in allowed for dep in deps):
            raise ValueError(f"Unexpected DLL dependencies (static CRT required): {deps}")
    elif target.startswith("linux"):
        output = command(["readelf", "-d", binary], env)
        deps = re.findall(r"\(NEEDED\).*\[(.+?)\]", output)
        if any(not re.fullmatch(r"lib(stdc\+\+|gcc_s|m|c|pthread|dl|rt)\.so(\.[\d]+)*|ld-linux[^/]*\.so(\.[\d]+)*", dep) for dep in deps):
            raise ValueError(f"Unexpected shared dependencies: {deps}")
    else:
        output = command(["otool", "-L", binary], env)
        deps = [line.strip().split(" (")[0] for line in output.splitlines()[1:]]
        deps = [dep for dep in deps if Path(dep).name != binary.name]
        if any(not dep.startswith(("/usr/lib/", "/System/Library/")) for dep in deps):
            raise ValueError(f"Unexpected shared dependencies: {deps}")
    return sorted(deps)


def pack_zip(path, files):
    path.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(dir=path.parent, suffix=".tmp", delete=False) as tmp:
        pending = Path(tmp.name)
    try:
        with zipfile.ZipFile(pending, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
            for name, data in sorted(files.items()):
                entry = zipfile.ZipInfo(name, (2025, 7, 2, 0, 0, 0))
                entry.create_system = 3  # Canonical ZIP metadata, independent of the build host.
                entry.compress_type = zipfile.ZIP_DEFLATED
                entry.external_attr = 0o644 << 16
                archive.writestr(entry, data, compresslevel=9)
        os.replace(pending, path)
    finally:
        pending.unlink(missing_ok=True)


def package(source, build, output, target, env, options):
    binary = next(path for path in build.rglob(PLATFORMS[target]) if path.is_file())
    if target.startswith("linux"):
        command(["strip", "--strip-unneeded", binary], env)
    elif target.startswith("macos"):
        command(["strip", "-x", binary], env)
    data = binary.read_bytes()
    validate_binary(data, target)
    deps = native_dependencies(binary, target, env)
    licenses = {"META-INF/licenses/opencv/LICENSE": (source / "LICENSE").read_bytes()}
    for path in (source / "3rdparty").rglob("*"):
        if path.is_file() and any(word in path.name.lower() for word in ("license", "copying", "copyright", "notice")):
            licenses["META-INF/licenses/opencv/" + path.relative_to(source).as_posix()] = path.read_bytes()
    java_jar = build / "bin/opencv-4120.jar"
    with zipfile.ZipFile(java_jar) as archive:
        java_files = {e.filename: archive.read(e) for e in archive.infolist()
                      if e.filename.endswith(".class") and not e.filename.startswith("org/opencv/osgi/")}
    for required in ("objdetect/FaceDetectorYN", "core/Mat", "imgproc/Imgproc", "imgcodecs/Imgcodecs"):
        if f"org/opencv/{required}.class" not in java_files:
            raise ValueError(f"Missing Java binding: {required}")
    for entry in java_files:
        if entry.startswith("org/opencv/") and entry.split("/")[2] not in CLOSURE | {"utils"}:
            raise ValueError(f"Unwanted Java module: {entry}")
    common = {**java_files, **licenses}
    common_path = output / f"{ARTIFACT}.jar"
    # Bindings must be identical across all platform builds; never silently mix versions.
    if common_path.exists():
        with zipfile.ZipFile(common_path) as existing:
            existing_files = {e.filename: existing.read(e) for e in existing.infolist() if not e.is_dir()}
            existing_classes = {name: data for name, data in existing_files.items() if name.endswith(".class")}
        if existing_classes != java_files:
            raise ValueError("Generated Java bindings differ from the checked-in common JAR; rebuild all platforms with the same JDK/options")
        if existing_files != common:
            raise ValueError("Common JAR resources differ; rebuild the complete platform artifact set together")
        # Preserve its exact bytes, including compression, so other classifier sidecars stay valid.
    else:
        pack_zip(common_path, common)
    native_hash = hashlib.sha256(data).hexdigest()
    prefix = f"com/alpha/facedetection/native/{target}/"
    metadata = {"version": VERSION, "platform": target, "source_url": SOURCE_URL, "source_sha256": SOURCE_SHA256,
                "java_compiler": JAVA_COMPILER, "generator_patch": GENERATOR_PATCH,
                "modules": sorted(CLOSURE), "cmake_options": options, "dependencies": deps,
                "library": binary.name, "native_bytes": len(data), "native_sha256": native_hash}
    native_files = {**licenses, prefix + binary.name: data,
                    prefix + "native.properties": f"version={VERSION}\nplatform={target}\nlibrary={binary.name}\nsha256={native_hash}\n".encode(),
                    "META-INF/opencv-yunet-desktop-build.json": json.dumps(metadata, indent=2, sort_keys=True).encode()}
    native_path = output / f"{ARTIFACT}-{target}.jar"
    pack_zip(native_path, native_files)
    metadata.update(java_jar_sha256=digest(common_path), native_jar_sha256=digest(native_path), native_jar_bytes=native_path.stat().st_size)
    (output / f"{ARTIFACT}-{target}.json").write_text(json.dumps(metadata, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(f"Packaged {common_path} ({common_path.stat().st_size} bytes) and {native_path} ({native_path.stat().st_size} bytes)", flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-archive", type=Path)
    parser.add_argument("--work-dir", type=Path, default=MODULE / "build/desktop-opencv")
    parser.add_argument("--output", type=Path, default=MODULE / "desktop-runtime")
    parser.add_argument("--cmake", default="cmake")
    parser.add_argument("--ninja", default="ninja")
    parser.add_argument("--jdk", type=Path, default=os.environ.get("JAVA_HOME"))
    parser.add_argument("--jobs", type=int, default=4)
    args = parser.parse_args()
    if args.jobs < 1 or not args.jdk:
        parser.error("Provide a JDK 17 via JAVA_HOME/--jdk and positive --jobs")
    target = host_platform()
    env = compiler_environment()
    env["JAVA_HOME"] = args.jdk.resolve().as_posix()
    javac = args.jdk.resolve() / "bin" / ("javac.exe" if target.startswith("windows") else "javac")
    if command([javac, "-version"], env).strip() != JAVA_COMPILER:
        raise ValueError(f"Native package generation requires {JAVA_COMPILER}; set --jdk to Temurin 17.0.20.1+1 for identical common bindings")
    work = args.work_dir.resolve()
    work.mkdir(parents=True, exist_ok=True)
    archive = args.source_archive or work / "opencv-4.12.0.zip"
    if not archive.exists():
        if args.source_archive:
            raise FileNotFoundError(archive)
        with tempfile.NamedTemporaryFile(dir=work, delete=False) as tmp:
            pending = Path(tmp.name)
        try:
            urllib.request.urlretrieve(SOURCE_URL, pending)
            if digest(pending) != SOURCE_SHA256:
                raise ValueError("Downloaded source SHA-256 mismatch")
            os.replace(pending, archive)
        finally:
            pending.unlink(missing_ok=True)
    source = extract_source(archive, work / "source")
    patch_java_generator(source)
    build = work / target
    options = cmake_options(args.jdk.resolve())
    # FindJava otherwise retains a previous JDK from CMakeCache even after JAVA_HOME changes.
    for tool in ("java", "javac", "jar"):
        name = tool + (".exe" if target.startswith("windows") else "")
        options[f"Java_{tool.upper()}_EXECUTABLE"] = (args.jdk.resolve() / "bin" / name).as_posix()
    options["CMAKE_MAKE_PROGRAM"] = args.ninja
    if target.startswith("macos"):
        options["CMAKE_OSX_ARCHITECTURES"] = "arm64" if target.endswith("aarch64") else "x86_64"
        options["CMAKE_OSX_DEPLOYMENT_TARGET"] = "11.0"
    command([args.cmake, "-S", source, "-B", build, "-G", "Ninja", *[f"-D{k}={v}" for k, v in options.items()]], env, work / f"configure-{target}.log")
    cache = (build / "CMakeCache.txt").read_text(errors="replace")
    modules = re.search(r"^OPENCV_MODULES_BUILD:INTERNAL=(.+)$", cache, re.M)
    if not modules or {m.removeprefix("opencv_") for m in modules[1].split(";")} - {"java_bindings_generator"} != CLOSURE:
        raise ValueError("CMake did not configure the expected module closure; check configure log")
    command([args.cmake, "--build", build, "--target", "opencv_java", "--parallel", str(args.jobs)], env, work / f"build-{target}.log")
    package(source, build, args.output.resolve(), target, env, options)


if __name__ == "__main__":
    main()
