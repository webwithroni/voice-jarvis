#!/usr/bin/env python3
"""Summarise JUnit XML unit-test results for CI output.

Reads app/build/test-results/testDebugUnitTest/*.xml and prints a short
Markdown summary. It never hides failures: Gradle's testDebugUnitTest task
already fails the workflow on any test failure; this only reports counts.
"""
import glob
import sys
import xml.etree.ElementTree as ET

RESULTS_GLOB = "app/build/test-results/testDebugUnitTest/*.xml"


def main() -> int:
    files = glob.glob(RESULTS_GLOB)
    print("## Unit test results")
    if not files:
        print("- No JUnit XML found (tests may not have run).")
        return 0

    tests = failures = errors = skipped = 0
    failed_cases = []
    for path in files:
        root = ET.parse(path).getroot()
        tests += int(root.get("tests", 0))
        failures += int(root.get("failures", 0))
        errors += int(root.get("errors", 0))
        skipped += int(root.get("skipped", 0))
        for case in root.iter("testcase"):
            if case.find("failure") is not None or case.find("error") is not None:
                failed_cases.append(f"{case.get('classname')}.{case.get('name')}")

    print(f"- Suites: {len(files)}")
    print(f"- Tests: {tests}")
    print(f"- Failures: {failures}")
    print(f"- Errors: {errors}")
    print(f"- Skipped: {skipped}")
    if failed_cases:
        print("\n### Failed cases")
        for name in failed_cases:
            print(f"- {name}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
