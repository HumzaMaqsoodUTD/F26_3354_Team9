#!/usr/bin/env bash
# Compiles the app plus all tests (including the original AssignmentServiceTest) and runs them with JUnit.
set -euo pipefail
cd "$(dirname "$0")"
jar=../junit-tests/lib/junit-platform-console-standalone-1.11.4.jar
rm -rf build-test && mkdir -p build-test evidence
javac --release 17 -encoding UTF-8 -cp "$jar" -d build-test \
  $(find src ../junit-tests/src test ../junit-tests/test -name "*.java")
java -jar "$jar" execute --class-path build-test --scan-class-path --disable-ansi-colors \
  --details tree --reports-dir evidence/junit 2>&1 | tee evidence/junit-output.txt
