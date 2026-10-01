#!/bin/bash
# 启动 Kotlin/Compose 版 Z6xToolBox。旧的 Avalonia 版本用 ./run-avalonia.sh
# 先用 Gradle 编译并写出依赖清单，再直接用 java 启动，Gradle 守护进程编译完就空闲（15 分钟后自动退出）。
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export JAVA_HOME="$HOME/Applications/jdk"
export PATH="$JAVA_HOME/bin:$PATH"
cd "$SCRIPT_DIR"
./gradlew -q :composeApp:writeDesktopClasspath || exit 1
exec java -Xmx512m -cp "$(cat build/desktop.classpath)" z6x.MainKt "$@"
