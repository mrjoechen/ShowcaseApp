"""Check packaged runtime inputs after the Android-KMP build, using only stdlib."""

import re
import io
import json
from pathlib import Path
import zipfile


ROOT = Path(__file__).resolve().parents[1]
ABIS = {"armeabi-v7a", "arm64-v8a", "x86", "x86_64"}
MODEL = "com/alpha/facedetection/models/face_detection_yunet_2026may.onnx"
MODEL_BYTES = (ROOT / "local-face-detection/src/jvmSharedMain/resources" / MODEL).read_bytes()


def require(condition, message):
    if not condition:
        raise SystemExit(message)


def verify_application(variant):
    output_dir = ROOT / "androidApp/build/outputs/apk" / variant
    metadata = json.loads((output_dir / "output-metadata.json").read_text(encoding="utf-8"))
    outputs = list(output_dir.glob("*.apk"))
    require(len(outputs) == len(metadata["elements"]) == 5, f"{variant}: expected five APKs")
    expected_id = "com.alpha.showcase" + (".android.dev" if variant == "debug" else "")
    require(metadata["applicationId"] == expected_id, f"{variant}: application ID changed")
    seen_abis = set()
    timestamps = set()
    for artifact in metadata["elements"]:
        abi = next((f["value"] for f in artifact["filters"] if f["filterType"] == "ABI"), "universal")
        require(abi not in seen_abis, f"{variant}: duplicate ABI {abi}")
        seen_abis.add(abi)
        apk_path = output_dir / artifact["outputFile"]
        require(apk_path in outputs, f"{variant}: metadata must reference an APK in the original output directory")
        pattern = rf"showcase-android\.{re.escape(artifact['versionName'])}_{artifact['versionCode']}-(\d{{12}})-{re.escape(abi)}-{variant}\.apk"
        match = re.fullmatch(pattern, apk_path.name)
        require(match is not None, f"{variant}: APK filename no longer matches the original rule: {apk_path.name}")
        timestamps.add(match.group(1))
        with zipfile.ZipFile(apk_path) as apk:
            packaged_abis = {n.split("/")[1] for n in apk.namelist() if n.startswith("lib/") and n.endswith(".so")}
            require(packaged_abis == (ABIS if abi == "universal" else {abi}), f"{variant}/{abi}: incorrect native libraries")
            require(apk.read(MODEL) == MODEL_BYTES, f"{variant}/{abi}: missing or changed YuNet model")
            if abi == "universal":
                dex = b"".join(apk.read(n) for n in apk.namelist() if n.endswith(".dex"))
                classes = ["com/alpha/showcase/android/App", "com/alpha/showcase/android/MainActivity"]
                if variant == "debug":
                    classes.append("com/alpha/showcase/common/cache/SourceCacheDatabase_Impl")
                for name in classes:
                    require(f"L{name};".encode() in dex, f"{variant}: missing class {name}")
    require(seen_abis == ABIS | {"universal"}, f"{variant}: incorrect split set")
    require(len(timestamps) == 1, f"{variant}: split APK timestamps differ")
    profiles = metadata.get("baselineProfiles", [])
    require(variant != "release" or profiles, "release: baseline profile metadata missing")
    for group in profiles:
        profile_paths = [output_dir / name for name in group["baselineProfiles"]]
        require({p.stem for p in profile_paths} == {p.stem for p in outputs},
                f"{variant}: baseline profile filenames must match all APKs")
        for profile in profile_paths:
            require(profile.resolve().is_relative_to(output_dir.resolve()) and profile.is_file(),
                    f"{variant}: baseline profile must exist inside the output directory: {profile}")
            require(profile.suffix == ".dm", f"{variant}: unexpected baseline profile extension")
    print(f"{variant}: original output directory/names, five APKs, metadata, entry classes, model and ABIs OK")


def verify_face_detection():
    with zipfile.ZipFile(ROOT / "local-face-detection/build/outputs/aar/local-face-detection.aar") as aar:
        rules = (ROOT / "local-face-detection/consumer-rules.pro").read_bytes().replace(b"\r\n", b"\n").strip()
        require(rules in aar.read("proguard.txt").replace(b"\r\n", b"\n"), "AAR: consumer rules missing")
        with zipfile.ZipFile(io.BytesIO(aar.read("classes.jar"))) as classes:
            require(classes.read(MODEL) == MODEL_BYTES, "AAR: YuNet model missing or changed")
    with zipfile.ZipFile(ROOT / "local-face-detection/build/outputs/apk/androidTest/local-face-detection-androidTest.apk") as apk:
        fixture = (ROOT / "local-face-detection/src/desktopTest/resources/opencv/lena.jpg").read_bytes()
        require(apk.read("assets/opencv/lena.jpg") == fixture, "Device test: image fixture missing or changed")
        dex = b"".join(apk.read(n) for n in apk.namelist() if n.endswith(".dex"))
        require(b"Lcom/alpha/facedetection/AndroidFaceInspectorTest;" in dex, "Device test: test class missing")
    print("Face detection: AAR rules/model and device-test class/assets OK")


if __name__ == "__main__":
    verify_application("debug")
    verify_application("release")
    verify_face_detection()
