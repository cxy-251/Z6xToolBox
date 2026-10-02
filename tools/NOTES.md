# z6x-tools 开发记录（实现暂停，先完成 z6x-hub）

## 已验证（2026-10-02）
- 交叉编译：`cargo build --release --target aarch64-unknown-linux-musl`，用 Rust 自带的 rust-lld 和 musl 自包含库链接，无需额外交叉编译器。最小程序 4.4 秒编译完成，400KB，在投影仪上可直接运行。
- 编译设置见 Cargo.toml（dev 关调试信息和增量编译；release 体积优先；dist 开 LTO，供 CI 使用）与 .cargo/config.toml（并行数 4）。

## 按键注入调查
- 遥控器节点 /dev/input/event13（XGIMI RC Consumer Control）支持方向、确认、返回、主页（KEY_HOMEPAGE）、菜单、音量、静音、电源、播放暂停等。
- 按键映射文件：/vendor/usr/keylayout/Vendor_000d_Product_3841.kl
  - key 28 DPAD_CENTER · 103 DPAD_UP · 105 DPAD_LEFT · 106 DPAD_RIGHT · 108 DPAD_DOWN
  - key 1 BACK · 114 VOLUME_DOWN · 115 VOLUME_UP · 113 KPPOWER
  - KEY_HOMEPAGE(172)、KEY_MENU(139) 的映射尚未在该文件中查到，实现时需再确认。
- 尚未验证：直接写 event13 或通过 /dev/uinput 创建虚拟键盘时，系统是否接受注入的按键。
