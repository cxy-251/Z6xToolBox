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

echo "=== 3. 注入并激活 Projectivy 无障碍服务 ==="
$ADB shell settings put secure enabled_accessibility_services \
    "com.xgimi.duertts/com.xgimi.duertts.MonitorService:com.spocky.projengmenu/com.spocky.projengmenu.services.ProjectivyAccessibilityService"
$ADB shell settings put secure accessibility_enabled 1

echo "=== 4. 恢复默认输入法为搜狗（保障遥控器零延迟） ==="
$ADB shell pm enable com.sohu.inputmethod.sogou.tv || true
$ADB shell ime enable com.sohu.inputmethod.sogou.tv/.SogouIME || true
$ADB shell ime set com.sohu.inputmethod.sogou.tv/.SogouIME || true

echo "=== 5. 发送 Home 键拉起 Projectivy 桌面 ==="
$ADB shell input keyevent 3

echo "=== 定制固化完成，当前前台焦点: ==="
$ADB shell "dumpsys window | grep -E 'mCurrentFocus|mFocusedApp'"
