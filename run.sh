#!/bin/bash
# 启动 Kotlin/Compose 版 Z6xToolBox。旧的 Avalonia 版本用 ./run-avalonia.sh
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export JAVA_HOME="$HOME/Applications/jdk"
export PATH="$JAVA_HOME/bin:$PATH"
cd "$SCRIPT_DIR"
if [ $# -gt 0 ]; then
    exec ./gradlew -q :composeApp:run --args="$*"
fi
exec ./gradlew -q :composeApp:run
