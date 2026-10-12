import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


class GradlePlatformTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory(prefix="showcase profile ")
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        (self.root / "scripts").mkdir()
        shutil.copy(Path(__file__).parents[1] / "gradle-platform.sh", self.root / "scripts")
        (self.root / "gradlew").write_text('#!/bin/sh\nprintf "%s\\n" "$PWD" "$@"\nexit "${FAKE_GRADLE_EXIT:-0}"\n')
        (self.root / "gradlew").chmod(0o755)

    def run_profile(self, *args, **env):
        return subprocess.run(
            ["sh", str(self.root / "scripts/gradle-platform.sh"), *args],
            cwd="/tmp", env={**os.environ, **env}, text=True, capture_output=True,
        )

    def test_standard_pins_false_and_preserves_arguments(self):
        result = self.run_profile("standard", ":composeApp:compileKotlinDesktop", "-Pexample=has spaces")
        self.assertEqual(result.returncode, 0, result.stderr)
        lines = result.stdout.splitlines()
        self.assertEqual(Path(lines[0]).resolve(), self.root.resolve())
        self.assertEqual(lines[1:], ["-Pshowcase.tvos=false", ":composeApp:compileKotlinDesktop", "-Pexample=has spaces"])

    def test_tvos_accepts_native_and_xcode_tasks(self):
        for task in (":composeApp:compileKotlinTvosArm64", ":composeApp:compileTestKotlinTvosSimulatorArm64", ":composeApp:embedAndSignAppleFrameworkForXcode"):
            result = self.run_profile("tvos", task, SDK_NAME="appletvsimulator18.2")
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn("-Pshowcase.tvos=true", result.stdout.splitlines())

    def test_rejects_cross_profile_builds(self):
        cases = [("standard", ":composeApp:compileKotlinTvosArm64"), ("tvos", ":composeApp:compileKotlinDesktop"), ("tvos", "build"), ("tvos", "assemble"), ("tvos", "check"), ("tvos", ":androidApp:assembleDebug")]
        for args in cases:
            result = self.run_profile(*args)
            self.assertNotEqual(result.returncode, 0, args)
            self.assertEqual(result.stdout, "", args)

    def test_rejects_profile_override_in_all_gradle_forms(self):
        for args in [("-Pshowcase.tvos=true",), ("-P", "showcase.tvos=true"), ("--project-prop=showcase.tvos=true",), ("--project-prop", "showcase.tvos=true"), ("-Dorg.gradle.project.showcase.tvos=true",)]:
            result = self.run_profile("standard", "help", *args)
            self.assertNotEqual(result.returncode, 0, args)
            self.assertEqual(result.stdout, "", args)

    def test_rejects_wrong_xcode_sdk(self):
        for mode, sdk in [("tvos", "iphonesimulator18.2"), ("standard", "appletvos18.2")]:
            result = self.run_profile(mode, ":composeApp:embedAndSignAppleFrameworkForXcode", SDK_NAME=sdk)
            self.assertNotEqual(result.returncode, 0)

    def test_propagates_failure_and_accepts_gradle_option_values(self):
        result = self.run_profile("tvos", ":composeApp:compileKotlinTvosArm64", "--max-workers", "2", FAKE_GRADLE_EXIT="7")
        self.assertEqual(result.returncode, 7, result.stderr)

    def test_invalid_or_empty_invocations_show_usage(self):
        for args in [(), ("other", "help"), ("tvos",), ("standard", "--no-daemon")]:
            result = self.run_profile(*args)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("Usage:", result.stderr)


if __name__ == "__main__":
    unittest.main()
