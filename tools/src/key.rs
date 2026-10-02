//! z6x key：注入按键。
//!
//! 两种方式：
//!   - uinput（默认）：通过 /dev/uinput 创建一个虚拟键盘。厂商号、产品号为 0，安卓用通用按键表
//!     Generic.kl 解释按键，主页、返回等键的含义确定，不依赖遥控器的设备节点号。
//!     创建设备后安卓需要一点时间识别它，因此单次调用有固定的等待（--settle，默认 150ms）。
//!   - event：直接写入遥控器的设备节点（按设备名查找，默认包含 "XGIMI RC"）。不需要等待，
//!     但按键按遥控器自己的按键表（Vendor_000d_Product_3841.kl）解释，表中没有的键（如主页）可能无效。
//! 系统是否接受这两种方式注入的按键，需在实机上验证（见规格「z6x-tools」）。

use crate::cli::{parse_duration_ms, Args, Fail, Json, Result};
use std::fs::{File, OpenOptions};
use std::io::Write;
use std::os::fd::AsRawFd;
use std::thread::sleep;
use std::time::{Duration, Instant};

pub const HELP: &str = "z6x key <键名>… [--repeat N] [--interval 50ms] [--via uinput|event] [--device 名称] [--settle 150ms] [--json]
  依次注入一个或多个按键，例如 z6x key home、z6x key down down ok、z6x key volup --repeat 5。
  --via uinput（默认）创建虚拟键盘；--via event 直接写入遥控器设备节点（按名称查找，默认 XGIMI RC）。
  --list 列出全部键名；--bench 测量一次注入的耗时（不实际按键时请勿使用：它会按下 F24）。";

/// 键名 → Linux 按键码（input-event-codes.h）。通过 uinput 注入时，安卓按 Generic.kl 解释。
pub const KEYS: &[(&str, u16, &str)] = &[
    ("up", 103, "方向上"),
    ("down", 108, "方向下"),
    ("left", 105, "方向左"),
    ("right", 106, "方向右"),
    ("ok", 232, "确认（DPAD_CENTER）"),
    ("enter", 28, "回车"),
    ("back", 158, "返回"),
    ("home", 172, "主页"),
    ("menu", 139, "菜单"),
    ("volup", 115, "音量加"),
    ("voldown", 114, "音量减"),
    ("mute", 113, "静音"),
    ("power", 116, "电源"),
    ("playpause", 164, "播放 / 暂停"),
    ("next", 163, "下一曲"),
    ("prev", 165, "上一曲"),
    ("stop", 166, "停止"),
    ("rewind", 168, "快退"),
    ("forward", 208, "快进"),
    ("search", 217, "搜索"),
    ("f24", 194, "F24（无实际功能，供 --bench 使用）"),
];

pub fn code_of(name: &str) -> Option<u16> {
    if let Ok(n) = name.parse::<u16>() {
        return Some(n); // 也接受数字按键码
    }
    KEYS.iter().find(|(n, _, _)| *n == name).map(|(_, c, _)| *c)
}

const EV_SYN: u16 = 0;
const EV_KEY: u16 = 1;

/// struct input_event（64 位）：timeval（16 字节）+ type + code + value。
fn event(typ: u16, code: u16, value: i32) -> [u8; 24] {
    let mut b = [0u8; 24];
    b[16..18].copy_from_slice(&typ.to_ne_bytes());
    b[18..20].copy_from_slice(&code.to_ne_bytes());
    b[20..24].copy_from_slice(&value.to_ne_bytes());
    b
}

// ioctl 编号：_IOW('U', nr, size) = (1 << 30) | (size << 16) | ('U' << 8) | nr。
// 请求号参数在 glibc 中为 unsigned long、在 musl 中为 int，调用处用 `as _` 适配两者。
const UI_SET_EVBIT: u32 = 0x4004_5564;
const UI_SET_KEYBIT: u32 = 0x4004_5565;
const UI_DEV_SETUP: u32 = 0x405c_5503; // struct uinput_setup，92 字节
const UI_DEV_CREATE: u32 = 0x5501;
const UI_DEV_DESTROY: u32 = 0x5502;

/// 注入器：持有一个打开的设备（虚拟键盘或遥控器节点）。keymap 守护进程会长期持有一个。
pub struct Injector {
    file: File,
    uinput: bool,
}

impl Injector {
    /// 创建虚拟键盘，并等待 settle 让安卓识别新设备。
    pub fn uinput(settle: Duration) -> Result<Injector> {
        let file = OpenOptions::new().write(true).open("/dev/uinput").map_err(|e| Fail::io("打开 /dev/uinput", e))?;
        let fd = file.as_raw_fd();
        unsafe {
            if libc::ioctl(fd, UI_SET_EVBIT as _, EV_KEY as libc::c_int) < 0 {
                return Err(Fail::error(format!("UI_SET_EVBIT 失败：{}", std::io::Error::last_os_error())));
            }
            for (_, code, _) in KEYS {
                libc::ioctl(fd, UI_SET_KEYBIT as _, *code as libc::c_int);
            }
            let mut setup = [0u8; 92];
            // struct uinput_setup：input_id（bustype、vendor、product、version 各 2 字节）+ name[80] + ff_effects_max。
            // 厂商号、产品号为 0，安卓找不到专用按键表，使用 Generic.kl。
            setup[0..2].copy_from_slice(&3u16.to_ne_bytes()); // BUS_USB
            let name = b"z6x virtual keyboard";
            setup[8..8 + name.len()].copy_from_slice(name);
            if libc::ioctl(fd, UI_DEV_SETUP as _, setup.as_ptr()) < 0 || libc::ioctl(fd, UI_DEV_CREATE as _) < 0 {
                return Err(Fail::error(format!("创建虚拟键盘失败：{}", std::io::Error::last_os_error())));
            }
        }
        sleep(settle);
        Ok(Injector { file, uinput: true })
    }

    /// 按名称查找输入设备节点（读取 /proc/bus/input/devices），打开以写入。
    pub fn event(name_part: &str) -> Result<Injector> {
        let path = find_device(name_part).ok_or_else(|| Fail::error(format!("找不到名称包含「{name_part}」的输入设备")))?;
        let file = OpenOptions::new().write(true).open(&path).map_err(|e| Fail::io(&format!("打开 {path}"), e))?;
        Ok(Injector { file, uinput: false })
    }

    /// 按下并抬起一个键。
    pub fn tap(&mut self, code: u16) -> Result<()> {
        let mut buf = Vec::with_capacity(96);
        buf.extend_from_slice(&event(EV_KEY, code, 1));
        buf.extend_from_slice(&event(EV_SYN, 0, 0));
        buf.extend_from_slice(&event(EV_KEY, code, 0));
        buf.extend_from_slice(&event(EV_SYN, 0, 0));
        self.file.write_all(&buf).map_err(|e| Fail::io("写入按键事件", e))
    }
}

impl Drop for Injector {
    fn drop(&mut self) {
        if self.uinput {
            // 销毁前稍等，确保最后的抬起事件已被读取
            sleep(Duration::from_millis(30));
            unsafe { libc::ioctl(self.file.as_raw_fd(), UI_DEV_DESTROY as _) };
        }
    }
}

/// 在 /proc/bus/input/devices 中查找名称包含 name_part 的设备，返回 /dev/input/eventN。
pub fn find_device(name_part: &str) -> Option<String> {
    let text = std::fs::read_to_string("/proc/bus/input/devices").ok()?;
    for block in text.split("\n\n") {
        let name = block.lines().find_map(|l| l.strip_prefix("N: Name=")).unwrap_or("");
        if !name.contains(name_part) {
            continue;
        }
        let handlers = block.lines().find_map(|l| l.strip_prefix("H: Handlers=")).unwrap_or("");
        if let Some(ev) = handlers.split_whitespace().find(|h| h.starts_with("event")) {
            return Some(format!("/dev/input/{ev}"));
        }
    }
    None
}

pub fn main(raw: &[String]) -> Result<()> {
    let a = Args::parse(raw, &["repeat", "interval", "via", "device", "settle"])?;
    a.reject_unknown(&["json", "list", "bench"])?;
    if a.flag("list") {
        for (n, c, d) in KEYS {
            println!("{n:<10} {c:>4}  {d}");
        }
        return Ok(());
    }
    let mut codes = vec![];
    if a.flag("bench") {
        codes.push(code_of("f24").unwrap());
    }
    for n in &a.positional {
        codes.push(code_of(n).ok_or_else(|| Fail::usage(format!("未知键名：{n}（z6x key --list 列出全部键名）")))?);
    }
    if codes.is_empty() {
        return Err(Fail::usage("需要至少一个键名，例如 z6x key home"));
    }
    let repeat: u32 = a.num("repeat", 1)?;
    let interval = parse_duration_ms(a.opt("interval").unwrap_or("50ms"))?;
    let settle = parse_duration_ms(a.opt("settle").unwrap_or("150ms"))?;
    let via = a.opt("via").unwrap_or("uinput");
    let start = Instant::now();
    let mut inj = match via {
        "uinput" => Injector::uinput(Duration::from_millis(settle))?,
        "event" => Injector::event(a.opt("device").unwrap_or("XGIMI RC"))?,
        other => return Err(Fail::usage(format!("--via 只能是 uinput 或 event：{other}"))),
    };
    let ready = start.elapsed();
    let mut n = 0;
    for r in 0..repeat {
        for (i, c) in codes.iter().enumerate() {
            if r > 0 || i > 0 {
                sleep(Duration::from_millis(interval));
            }
            inj.tap(*c)?;
            n += 1;
        }
    }
    drop(inj);
    let total = start.elapsed();
    if a.flag("json") {
        println!("{}", Json::obj().str("via", via).num("keys", n).f2("ready_ms", ready.as_secs_f64() * 1000.0).f2("total_ms", total.as_secs_f64() * 1000.0).end());
    } else if a.flag("bench") {
        println!("方式 {via}：准备 {:.1} ms，共 {:.1} ms（{n} 次按键）", ready.as_secs_f64() * 1000.0, total.as_secs_f64() * 1000.0);
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn key_table() {
        assert_eq!(code_of("home"), Some(172));
        assert_eq!(code_of("115"), Some(115));
        assert_eq!(code_of("nope"), None);
        let e = event(EV_KEY, 172, 1);
        assert_eq!(&e[16..18], &1u16.to_ne_bytes());
        assert_eq!(&e[18..20], &172u16.to_ne_bytes());
    }
}
