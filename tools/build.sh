#!/bin/bash
# 在 Steam Deck 上编译 z6x-tools。
# 用法：./tools/build.sh [test | release | dist | deploy <设备名>]
#   test     在 Deck 上运行单元测试（x86_64）
#   release  交叉编译 aarch64 静态程序（默认）：体积优先、不开链接时优化，编译快、占内存少
#   dist     正式发布：开启链接时优化（更小、更慢、更占内存，CI 使用）
#   deploy   编译 release 后推送到设备的 /data/local/tmp/z6x-tools/z6x（设备名见 hub/devices/devices.txt）
#
# 为 Deck 做的设置：
#   - 以最低优先级编译（nice 19、ionice idle），编译期间 Deck 仍能流畅使用；
#   - 并行数由 .cargo/config.toml 限制为 4（Deck 为 4 核 8 线程，留一半给系统）；
#   - 只依赖 libc，没有需要下载或编译的大型依赖；开发编译不生成调试信息、不做增量编译，target 目录很小。
set -euo pipefail
cd "$(dirname "$0")"
export PATH="$HOME/.cargo/bin:$PATH"
LOW="nice -n 19 ionice -c 3"
TARGET=aarch64-unknown-linux-musl
MODE="${1:-release}"

case "$MODE" in
  test)
    $LOW cargo test --quiet ;;
  release|dist|deploy)
    PROFILE=release; [ "$MODE" = dist ] && PROFILE=dist
    T0=$(date +%s)
    $LOW cargo build --quiet --profile "$PROFILE" --target $TARGET
    BIN=target/$TARGET/$PROFILE/z6x
    echo "== 编译完成（$(( $(date +%s) - T0 )) 秒）：$BIN，$(du -h "$BIN" | cut -f1)"
    file "$BIN" | grep -q "statically linked" && echo "== 静态链接 ✓" || { echo "!! 不是静态链接"; exit 1; }
    if [ "$MODE" = deploy ]; then
      DEV="${2:?缺少设备名，例如 ./tools/build.sh deploy projector}"
      cd ../hub && source ./lib.sh && resolve_device "$DEV" && cd ../tools
      [ "$MODE" = deploy ] && [ "${MODE_OVERRIDE:-}" = "" ] && [ "$(awk -v n="$DEV" '$1==n{print $3}' ../hub/devices/devices.txt)" = termux ] && {
        echo "z6x-tools 需要 shell 身份（输入设备、/proc），只部署到以 ADB 方式运行的设备"; exit 1; }
      adb -s "$ADDR" shell "mkdir -p /data/local/tmp/z6x-tools"
      adb -s "$ADDR" push "$BIN" /data/local/tmp/z6x-tools/z6x >/dev/null
      # 改键守护进程的开关脚本，供 hub 的「后台任务」调用
      adb -s "$ADDR" push keymapd.sh /data/local/tmp/z6x-tools/keymapd.sh >/dev/null
      adb -s "$ADDR" shell "chmod 755 /data/local/tmp/z6x-tools/z6x /data/local/tmp/z6x-tools/keymapd.sh && /data/local/tmp/z6x-tools/z6x version"
    fi ;;
  *)
    sed -n 2,7p "$0"; exit 2 ;;
esac
