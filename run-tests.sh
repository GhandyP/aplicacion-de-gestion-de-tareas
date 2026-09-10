#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p build/classes build/test-classes
javac -encoding UTF-8 -d build/classes src/taskmanager/*.java
javac -encoding UTF-8 -cp build/classes -d build/test-classes test/taskmanager/TaskManagerTests.java
java -ea -cp build/classes:build/test-classes taskmanager.TaskManagerTests
