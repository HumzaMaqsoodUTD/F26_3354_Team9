#!/usr/bin/env bash
# Compiles the app and starts the server at http://127.0.0.1:8000
set -euo pipefail
cd "$(dirname "$0")"
rm -rf build && mkdir -p build
javac --release 17 -encoding UTF-8 -d build $(find src ../junit-tests/src -name "*.java")
exec java -cp build occupan.App
