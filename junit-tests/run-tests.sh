#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p build evidence
jar=lib/junit-platform-console-standalone-1.11.4.jar
javac --release 17 -cp "$jar" -d build src/AssignmentService.java test/AssignmentServiceTest.java
java -jar "$jar" execute --class-path build --scan-class-path --disable-ansi-colors --details tree --reports-dir evidence/junit 2>&1 | tee evidence/junit-output.txt
