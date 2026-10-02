#!/bin/bash
# 手机（Redmi Note 12 Turbo，HyperOS 3）精简脚本：停用不使用的系统预装。只作用于主空间（用户 0），不碰安全空间。
# 用法：./scripts/phone_debloat.sh <apply|restore|status> [ADB 地址，默认按 hub/devices/devices.txt 查找手机]
#
# 先「停用」（pm disable-user）。HyperOS 禁止 shell 停用系统应用（SecurityException: Cannot disable system packages，
# 2026-10-02 实测），此时改为「为主空间卸载」（pm uninstall -k --user 0）：安装包仍在系统分区，
# 用 cmd package install-existing 即可装回，数据保留（-k）。两种方式 restore 均可完全恢复。以下类别一律不动：
#   电话、短信、SIM 卡（含黄页的来电识别、AI 通话）；支付与银行（钱包、银联、NFC 卡包、指纹支付）；
#   手机管家本体 securitycenter（负责权限弹窗、自启动管理与安装，停用可能无法开机）；
#   系统更新、云备份、互传、投屏。
set -euo pipefail
cd "$(dirname "$0")/.."

PACKAGES=(
  # 浏览器、商店、文件管理（默认浏览器为 Edge；文件管理换为 Material Files）
  com.android.browser            # 小米浏览器
  com.xiaomi.market              # 应用商店
  com.android.fileexplorer       # 文件管理
  com.miui.hybrid                # 快应用
  com.android.quicksearchbox     # 系统搜索
  # 广告与数据上报
  com.miui.systemAdSolution      # 系统广告
  com.miui.analytics             # 统计
  com.miui.misightservice        # 使用数据采集
  com.miui.uireporter            # 界面数据上报
  com.miui.bugreport             # 问题反馈
  com.bsp.catchlog               # 日志抓取
  com.miui.thirdappassistant     # 第三方应用推荐助手
  # 小爱、AI、推荐内容
  com.miui.voiceassist           # 小爱同学
  com.miui.voicetrigger          # 语音唤醒
  com.xiaomi.mibrain.speech      # 小爱语音引擎
  com.xiaomi.aiasst.vision       # AI 识屏
  com.xiaomi.aireco              # 智能推荐
  com.miui.contentextension      # 传送门
  com.miui.newhome               # 内容中心
  com.miui.personalassistant     # 负一屏
  com.mfashiongallery.emag       # 锁屏画报
  com.xiaomi.minigame            # 小米小游戏
  # 手机管家附属（本体保留）
  com.miui.guardprovider         # 病毒扫描
  com.miui.cleanmaster           # 清理
  com.miui.greenguard            # 家长守护（未成年人模式）
  com.miui.carlink               # 车载互联
  # 用户确认不使用
  com.android.thememanager       # 主题壁纸
  com.xiaomi.scanner             # 扫一扫（AI 扫描）
)
# 相册：装好替代品（Fossify Gallery）后再停用，单独列出
GALLERY=(com.miui.gallery com.miui.mediaeditor)

ACTION="${1:-status}"
ADDR="${2:-}"
if [ -z "$ADDR" ]; then
  IP=$(awk '$1 == "phone" { print $2 }' hub/devices/devices.txt)
  ADDR=$(adb devices | awk -v h="$IP" '$2 == "device" && index($1, h ":") == 1 { print $1; exit }')
  [ -n "$ADDR" ] || { echo "手机未连接：先 adb connect"; exit 1; }
fi
ADB="adb -s $ADDR"

list() {
  case "${GROUP:-main}" in
    gallery) printf '%s\n' "${GALLERY[@]}" ;;
    *) printf '%s\n' "${PACKAGES[@]}" ;;
  esac
}

case "$ACTION" in
  apply)
    ok=0; fail=0
    for p in $(list); do
      if ! $ADB shell pm list packages -a "$p" | grep -qx "package:$p"; then echo "  跳过（本机没有）$p"; continue; fi
      if ! $ADB shell pm list packages --user 0 "$p" | grep -qx "package:$p"; then continue; fi  # 已为主空间卸载
      out=$($ADB shell pm disable-user --user 0 "$p" 2>&1 || true)
      if [[ "$out" == *disabled-user* ]]; then
        ok=$((ok + 1))
      elif [[ "$out" == *"Cannot disable system packages"* ]] && [ "$($ADB shell pm uninstall -k --user 0 "$p" 2>&1 | tail -1)" = "Success" ]; then
        echo "  为主空间卸载 $p"; ok=$((ok + 1))
      else
        echo "  ✗ $p：$(echo "$out" | grep -m1 -E "Exception|Error" || echo "$out" | head -1)"; fail=$((fail + 1))
      fi
    done
    echo "== 已停用 $ok 个，失败 $fail 个" ;;
  restore)
    for p in $(list); do
      if ! $ADB shell pm list packages --user 0 "$p" | grep -qx "package:$p"; then
        $ADB shell cmd package install-existing --user 0 "$p" >/dev/null 2>&1 && echo "  已装回 $p" || echo "  ✗ 装回失败 $p"
      fi
      $ADB shell pm enable --user 0 "$p" >/dev/null 2>&1 || true
    done
    echo "== 已恢复" ;;
  status)
    disabled=$($ADB shell pm list packages -d --user 0 | cut -d: -f2)
    present=$($ADB shell pm list packages --user 0 | cut -d: -f2)
    for p in $(list); do
      if ! grep -qx "$p" <<<"$present"; then echo "  已为主空间卸载 $p"
      elif grep -qx "$p" <<<"$disabled"; then echo "  已停用 $p"
      else echo "  启用中 $p"; fi
    done ;;
  *)
    echo "用法：$0 <apply|restore|status> [ADB 地址]；相册一组用 GROUP=gallery $0 apply"; exit 1 ;;
esac
