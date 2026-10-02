# z6x-tools 开发记录

规格与实测结果见工具箱「提案 → 规格 → 规格：z6x-tools」。

## 编译（Steam Deck）
- `./tools/build.sh test | release | dist | deploy <设备名>`
- 低优先级编译（nice 19、ionice idle），并行数 4；只依赖 libc。
- release 640KB、dist 532KB，静态 aarch64。

## 待测（需要用户在场）
- `z6x key home`（uinput 与 --via event 两种方式，系统是否接受）、`z6x key --bench`
- `z6x keymap --daemon`：长按、双击、遥控器重连
- `z6x iobench`（会大量读写存储，不在播放时测）

## 按键注入调查
- 遥控器节点 /dev/input/event13（XGIMI RC Consumer Control），程序按名称查找，不固定节点号。
- 按键表 /vendor/usr/keylayout/Vendor_000d_Product_3841.kl：28 确认、103/105/106/108 方向、1 返回、114/115 音量；主页（172）、菜单（139）未在表中查到。
