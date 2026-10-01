#!/bin/bash
# Z6X Pro 一键恢复原厂预装与服务状态脚本
set -e

DEVICE="${1:-192.168.0.109:5555}"
ADB="adb -s $DEVICE"

echo "=== 正在连接设备: $DEVICE ==="
$ADB wait-for-device

echo "=== 1. 重新挂载官方桌面与流媒体包 ==="
$ADB shell cmd package install-existing com.xgimi.home || true
$ADB shell cmd package install-existing com.xgimi.stream.video || true

echo "=== 2. 批量重新启用全部系统组件 ==="
DISABLED_PACKAGES=(
    "com.xgimi.home"
    "com.xgimi.stream.video"
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
    "com.sohu.inputmethod.sogou.tv"
)

for pkg in "${DISABLED_PACKAGES[@]}"; do
    $ADB shell pm enable "$pkg" >/dev/null 2>&1 || true
    echo "  [Enabled] $pkg"
done

echo "=== 3. 恢复原生无障碍服务配置 ==="
$ADB shell settings put secure enabled_accessibility_services \
    "com.xgimi.duertts/com.xgimi.duertts.MonitorService"
$ADB shell settings put secure accessibility_enabled 1

echo "=== 4. 唤醒官方主桌面 ==="
# 官方桌面的入口是 HomeActivity（2026-10-01 用 dumpsys package com.xgimi.home 核对），原脚本写的 .MainActivity 不存在
$ADB shell am start -n com.xgimi.home/com.xgimi.module.cellview.home.ui.HomeActivity || true
$ADB shell input keyevent 3

echo "=== 恢复操作完成，当前前台焦点: ==="
$ADB shell "dumpsys window | grep -E 'mCurrentFocus|mFocusedApp'"
