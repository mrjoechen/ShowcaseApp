import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("compare_traces", Path(__file__).parents[2] / "verification/confetti/compare_traces.py")
traces = importlib.util.module_from_spec(spec)
spec.loader.exec_module(traces)


class ConfettiParityTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.source = Path(self.directory.name) / "source.txt"
        self.reference = Path(self.directory.name) / "reference.txt"
        self.lines = [f"{preset}/{density}.0/{frame}={'a' * 64}\n" for preset in ("Celebration", "Success", "Burst") for density in (1, 2, 3) for frame in (8, 16, 17, 33)]
        self.reference.write_text("".join(self.lines))

    def test_equal_complete_traces_pass(self):
        self.source.write_text("".join(self.lines))
        traces.compare(self.source, self.reference)

    def test_a_changed_particle_trace_fails(self):
        self.source.write_text("".join(self.lines).replace("a" * 64, "b" * 64, 1))
        with self.assertRaisesRegex(ValueError, "behavior differs"):
            traces.compare(self.source, self.reference)

    def test_missing_duplicate_or_empty_cases_fail(self):
        for lines in (self.lines[:-1], self.lines + self.lines[:1], []):
            self.source.write_text("".join(lines))
            with self.assertRaises(ValueError):
                traces.compare(self.source, self.reference)
