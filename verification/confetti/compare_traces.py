"""Fail closed on missing cases, duplicate cases, or any particle trace difference."""
from pathlib import Path
import re
import sys


def read_trace(file):
    expected = {f"{preset}/{density}.0/{frame}" for preset in ("Celebration", "Success", "Burst") for density in (1, 2, 3) for frame in (8, 16, 17, 33)}
    traces = {}
    for line in Path(file).read_text().splitlines():
        key, digest = line.split("=", 1)
        if key in traces or not re.fullmatch(r"[0-9a-f]{64}", digest):
            raise ValueError(f"Invalid trace in {file}: {line}")
        traces[key] = digest
    if traces.keys() != expected:
        raise ValueError(f"Expected all 36 animation cases in {file}; missing={expected - traces.keys()}, unexpected={traces.keys() - expected}")
    return traces


def compare(source, reference):
    actual, expected = read_trace(source), read_trace(reference)
    differences = [key for key in expected if actual[key] != expected[key]]
    if differences:
        raise ValueError(f"ConfettiKit source/binary behavior differs: {', '.join(differences)}")


if __name__ == "__main__":
    compare(*sys.argv[1:])
    print("PASS: all 36 source/binary animation traces match")
