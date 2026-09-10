#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p build/classes
javac -encoding UTF-8 -d build/classes src/taskmanager/*.java
exec java -cp build/classes taskmanager.Main "$@"
