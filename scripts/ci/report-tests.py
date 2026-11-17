"""Summarize JUnit evidence and require the isolated MySQL tests to run."""
import os
from pathlib import Path
import sys
import xml.etree.ElementTree as ET


def main():
    reports = sorted(Path("build/test-results/test").glob("TEST-*.xml"))
    suites = [ET.parse(report).getroot() for report in reports]
    counts = {key: sum(int(suite.get(key, "0")) for suite in suites)
              for key in ("tests", "failures", "errors", "skipped")}
    mysql = [suite for suite in suites
             if suite.get("name", "").endswith((".MySqlBackendBTest", ".V7MySqlMigrationTest"))]
    mysql_classes = {suite.get("name", "").split(".")[-1] for suite in mysql}
    mysql_tests = sum(int(suite.get("tests", "0")) for suite in mysql)
    mysql_skipped = sum(int(suite.get("skipped", "0")) for suite in mysql)
    passed = counts["tests"] - counts["failures"] - counts["errors"] - counts["skipped"]
    summary = ("## Backend test results\n\n"
               "| Metric | Count |\n|---|---:|\n"
               f"| Test classes | {len(suites)} |\n"
               f"| Tests | {counts['tests']} |\n| Passed | {passed} |\n"
               f"| Failures | {counts['failures']} |\n| Errors | {counts['errors']} |\n"
               f"| Skipped | {counts['skipped']} |\n"
               f"| MySQL tests (included above) | {mysql_tests - mysql_skipped} |\n")
    print(summary)
    destination = os.environ.get("GITHUB_STEP_SUMMARY")
    if destination:
        with open(destination, "a", encoding="utf-8") as output:
            output.write(summary)
    if (not reports or counts["tests"] == 0 or counts["failures"] or counts["errors"]
            or counts["skipped"] or mysql_tests == 0 or mysql_skipped
            or mysql_classes != {"MySqlBackendBTest", "V7MySqlMigrationTest"}):
        print("::error::All tests, including MySQL integration tests, must execute without failures or skips.")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
