#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
mkdir -p build/core-tests
kotlinc app/src/main/java/hu/atelierpad/core/*.kt tests/CoreTests.kt -include-runtime -d build/core-tests/tests.jar
java -Xmx512m -jar build/core-tests/tests.jar
