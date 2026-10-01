#!/bin/bash
# Z6X Pro 一键深度定制与广告精简固化脚本
set -e

DEVICE="${1:-192.168.0.109:5555}"
ADB="adb -s $DEVICE"

echo "=== 正在连接设备: $DEVICE ==="
$ADB wait-for-device

echo "=== 1. 卸载官方桌面与流媒体推荐（当前主用户） ==="
$ADB shell pm uninstall -k --user 0 com.xgimi.home || true
$ADB shell pm uninstall -k --user 0 com.xgimi.stream.video || true

echo "=== 2. 批量停用广告、OTA、IoT与冗余伴生组件 ==="
DISABLED_PACKAGES=(
    "com.xgimi.adservice"
    "com.xgimi.datareporter"
    "com.xgimi.bugreportsender"
    "com.xgimi.doubanfm"
    "com.xgimi.agilewall"
    "com.xgimi.atmosphere"
    "com.xgimi.xgimihilink"
    "com.xgimi.xgimiiotserver"
    "com.xgimi.vcontrol"
    "com.xgimi.instruction30"
    "com.xgimi.bootwizard"
    "com.xgimi.payview"
    "com.xgimi.msgcenter"
    "com.xgimi.newappmarket"
    "com.xgimi.screensaver"
    "com.xgimi.skinmanager"
    "com.xgimi.skinconfig"
    "com.xgimi.skin.classicblue"
    "com.xgimi.skin.black"
    "com.xgimi.skin.lightblue"
    "com.xgimi.upgrade"
    "com.xgimi.ota.accessories"
    "com.xgimi.iot"
    "com.xgimi.smartconnect"
    "com.xgimi.mobilebridgeservice"
    "com.xgimi.smartaccessories"
    "com.xgimi.mateservice"
    "com.xgimi.user"
    "com.xgimi.soundermodeservice"
)

for pkg in "${DISABLED_PACKAGES[@]}"; do
    $ADB shell pm disable-user --user 0 "$pkg" >/dev/null 2>&1 || true
    echo "  [Disabled] $pkg"
done

# 3. （已取消）注入 Projectivy 无障碍服务
# 官方桌面 com.xgimi.home 已在第 1 步卸载，Projectivy 成为唯一桌面，Home 键自然回到它，不需要无障碍服务拦截。
# 2026-10-01 实测：enabled_accessibility_services 只有 com.xgimi.duertts/.MonitorService，Home 键正常。

echo "=== 4. 恢复默认输入法为搜狗（保障遥控器零延迟） ==="
$ADB shell pm enable com.sohu.inputmethod.sogou.tv || true
$ADB shell ime enable com.sohu.inputmethod.sogou.tv/.SogouIME || true
$ADB shell ime set com.sohu.inputmethod.sogou.tv/.SogouIME || true

echo "=== 5. 发送 Home 键拉起 Projectivy 桌面 ==="
$ADB shell input keyevent 3

echo "=== 定制固化完成，核对结果 ==="
echo "  已停用：$($ADB shell 'pm list packages -d | wc -l') 个（应为 29）"
echo "  已卸载（当前用户）："
$ADB shell 'pm list packages -u | grep -vxF "$(pm list packages)"'
$ADB shell "dumpsys window | grep -E 'mCurrentFocus|mFocusedApp'"
