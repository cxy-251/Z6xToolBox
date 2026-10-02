//! z6x keymap --daemon：遥控器按键重映射。
//!
//! 读取遥控器设备节点的按键事件，**不独占设备**（不调用 EVIOCGRAB），遥控器原有功能不受影响；
//! 识别长按与双击，执行配置中的动作。遥控器断开重连导致节点变化时，每 2 秒重新查找并绑定。
//!
//! 注意：因为不独占，触发长按的那个键在松开时仍会产生它原本的效果（例如长按返回，松开时仍会返回一次）。

use crate::cli::{parse_duration_ms, Args, Fail, Result};
use crate::key::{find_device, Injector, KEYS};
use std::fs::File;
use std::io::Read;
use std::os::fd::AsRawFd;
use std::time::{Duration, Instant};

pub const HELP: &str = "z6x keymap --daemon --config <文件> [--device 名称] [--long 600ms] [--double 300ms]
z6x keymap --check --config <文件>     只检查配置
  配置文件每行一条：<触发> = <动作>，# 开头为注释。
    触发：long:<键名>（长按）、double:<键名>（双击）
    动作：key: <键名> [<键名>…]（注入按键）、sh: <命令>（用 /system/bin/sh 执行，不等待结束）
  示例：
    long:back   = sh: am start -n de.szalkowski.activitylauncher/.MainActivity
    double:home = key: menu
  键名同 z6x key --list；遥控器上返回、确认、主页的原始按键码与通用键盘不同，这里两种都能匹配。";

/// 遥控器原始按键码与通用键名的对应（Vendor_000d_Product_3841.kl：1 为返回、28 为确认）。
const REMOTE_ALIASES: &[(&str, u16)] = &[("back", 1), ("ok", 28), ("home", 102)];

fn codes_for(name: &str) -> Vec<u16> {
    let mut v: Vec<u16> = KEYS.iter().filter(|(n, _, _)| *n == name).map(|(_, c, _)| *c).collect();
    v.extend(REMOTE_ALIASES.iter().filter(|(n, _)| *n == name).map(|(_, c)| *c));
    if let Ok(n) = name.parse::<u16>() {
        v.push(n);
    }
    v
}

#[derive(Debug, Clone, PartialEq)]
pub enum Action {
    Keys(Vec<u16>),
    Shell(String),
}

#[derive(Debug, PartialEq)]
pub struct Rule {
    pub long: bool, // true 为长按，false 为双击
    pub codes: Vec<u16>,
    pub key_name: String,
    pub action: Action,
}

pub fn parse_config(text: &str) -> Result<Vec<Rule>> {
    let mut rules = vec![];
    for (no, line) in text.lines().enumerate() {
        let line = line.trim();
        if line.is_empty() || line.starts_with('#') {
            continue;
        }
        let bad = |m: &str| Fail::usage(format!("配置第 {} 行：{m}：{line}", no + 1));
        let (trig, act) = line.split_once('=').ok_or_else(|| bad("缺少 ="))?;
        let (kind, key) = trig.trim().split_once(':').ok_or_else(|| bad("触发应为 long:<键名> 或 double:<键名>"))?;
        let long = match kind {
            "long" => true,
            "double" => false,
            _ => return Err(bad("触发只能是 long 或 double")),
        };
        let codes = codes_for(key.trim());
        if codes.is_empty() {
            return Err(bad("未知键名"));
        }
        let (ak, av) = act.trim().split_once(':').ok_or_else(|| bad("动作应为 key: … 或 sh: …"))?;
        let action = match ak.trim() {
            "key" => {
                let mut ks = vec![];
                for n in av.split_whitespace() {
                    ks.push(crate::key::code_of(n).ok_or_else(|| bad("动作中有未知键名"))?);
                }
                if ks.is_empty() {
                    return Err(bad("key: 后需要键名"));
                }
                Action::Keys(ks)
            }
            "sh" if !av.trim().is_empty() => Action::Shell(av.trim().to_string()),
            _ => return Err(bad("动作应为 key: … 或 sh: …")),
        };
        rules.push(Rule { long, codes, key_name: key.trim().to_string(), action });
    }
    Ok(rules)
}

fn log(msg: &str) {
    let t = std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).map(|d| d.as_secs()).unwrap_or(0);
    eprintln!("[{t}] {msg}");
}

struct KeyState {
    down_at: Option<Instant>,
    long_fired: bool,
    last_tap: Option<Instant>,
}

pub fn main(raw: &[String]) -> Result<()> {
    let a = Args::parse(raw, &["config", "device", "long", "double"])?;
    a.reject_unknown(&["daemon", "check"])?;
    let path = a.opt("config").ok_or_else(|| Fail::usage("需要 --config <文件>"))?;
    let text = std::fs::read_to_string(path).map_err(|e| Fail::io(&format!("读取 {path}"), e))?;
    let rules = parse_config(&text)?;
    if a.flag("check") || !a.flag("daemon") {
        println!("配置正确：{} 条规则", rules.len());
        for r in &rules {
            println!("  {}:{} → {:?}", if r.long { "long" } else { "double" }, r.key_name, r.action);
        }
        if !a.flag("daemon") && !a.flag("check") {
            println!("（加 --daemon 开始运行）");
        }
        return Ok(());
    }
    let long_ms = Duration::from_millis(parse_duration_ms(a.opt("long").unwrap_or("600ms"))?);
    let double_ms = Duration::from_millis(parse_duration_ms(a.opt("double").unwrap_or("300ms"))?);
    let dev_name = a.opt("device").unwrap_or("XGIMI RC").to_string();
    // 执行 sh 动作后不等待：忽略 SIGCHLD，子进程结束后由内核自动回收，不留僵尸进程
    unsafe { libc::signal(libc::SIGCHLD, libc::SIG_IGN) };
    let mut injector: Option<Injector> = None;
    let fire = |r: &Rule, injector: &mut Option<Injector>| {
        log(&format!("触发 {}:{}", if r.long { "long" } else { "double" }, r.key_name));
        match &r.action {
            Action::Shell(cmd) => {
                if let Err(e) = std::process::Command::new("/system/bin/sh").arg("-c").arg(cmd).spawn() {
                    log(&format!("执行失败：{e}"));
                }
            }
            Action::Keys(ks) => {
                if injector.is_none() {
                    match Injector::uinput(Duration::from_millis(150)) {
                        Ok(i) => *injector = Some(i),
                        Err(f) => return log(&format!("无法创建虚拟键盘：{}", f.msg)),
                    }
                }
                for k in ks {
                    if let Some(i) = injector.as_mut() {
                        let _ = i.tap(*k);
                    }
                    std::thread::sleep(Duration::from_millis(40));
                }
            }
        }
    };
    log(&format!("keymap 启动：{} 条规则，设备名包含「{dev_name}」", rules.len()));
    loop {
        let Some(dev) = find_device(&dev_name) else {
            std::thread::sleep(Duration::from_secs(2));
            continue;
        };
        let mut f = match File::open(&dev) {
            Ok(f) => f,
            Err(e) => {
                log(&format!("打开 {dev} 失败：{e}"));
                std::thread::sleep(Duration::from_secs(2));
                continue;
            }
        };
        log(&format!("已绑定 {dev}"));
        let mut states: std::collections::HashMap<u16, KeyState> = Default::default();
        let mut buf = [0u8; 24];
        'dev: loop {
            // 下一个长按判定的到期时间，决定 poll 的等待时长
            let now = Instant::now();
            let timeout = states
                .values()
                .filter(|s| !s.long_fired)
                .filter_map(|s| s.down_at.map(|d| (d + long_ms).saturating_duration_since(now)))
                .min()
                .map(|d| d.as_millis() as i32)
                .unwrap_or(-1);
            let mut pfd = libc::pollfd { fd: f.as_raw_fd(), events: libc::POLLIN, revents: 0 };
            let n = unsafe { libc::poll(&mut pfd, 1, timeout) };
            if n == 0 {
                let now = Instant::now();
                for (code, s) in states.iter_mut() {
                    if let Some(d) = s.down_at {
                        if !s.long_fired && now >= d + long_ms {
                            s.long_fired = true;
                            if let Some(r) = rules.iter().find(|r| r.long && r.codes.contains(code)) {
                                fire(r, &mut injector);
                            }
                        }
                    }
                }
                continue;
            }
            if n < 0 || pfd.revents & (libc::POLLERR | libc::POLLHUP | libc::POLLNVAL) != 0 {
                break 'dev;
            }
            if f.read_exact(&mut buf).is_err() {
                break 'dev; // 设备消失（遥控器断开）：回到外层循环重新查找
            }
            let typ = u16::from_ne_bytes([buf[16], buf[17]]);
            let code = u16::from_ne_bytes([buf[18], buf[19]]);
            let value = i32::from_ne_bytes([buf[20], buf[21], buf[22], buf[23]]);
            if typ != 1 {
                continue;
            }
            let s = states.entry(code).or_insert(KeyState { down_at: None, long_fired: false, last_tap: None });
            match value {
                1 => {
                    s.down_at = Some(Instant::now());
                    s.long_fired = false;
                }
                0 => {
                    let was_long = s.long_fired;
                    s.down_at = None;
                    s.long_fired = false;
                    if was_long {
                        continue;
                    }
                    let now = Instant::now();
                    if let Some(r) = rules.iter().find(|r| !r.long && r.codes.contains(&code)) {
                        if s.last_tap.is_some_and(|t| now - t <= double_ms) {
                            s.last_tap = None;
                            fire(r, &mut injector);
                        } else {
                            s.last_tap = Some(now);
                        }
                    }
                }
                _ => {} // 2 为按住时的自动重复，忽略
            }
        }
        log(&format!("{dev} 已断开，等待重新连接"));
        std::thread::sleep(Duration::from_secs(2));
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn config() {
        let r = parse_config("# 注释\nlong:back = sh: am start -n a/.B\ndouble:home = key: menu ok\n").ok().unwrap();
        assert_eq!(r.len(), 2);
        assert!(r[0].long && r[0].codes.contains(&158) && r[0].codes.contains(&1));
        assert_eq!(r[0].action, Action::Shell("am start -n a/.B".into()));
        assert_eq!(r[1].action, Action::Keys(vec![139, 232]));
        assert!(parse_config("long:nokey = key: menu").is_err());
        assert!(parse_config("tap:back = key: menu").is_err());
        assert!(parse_config("long:back = run: x").is_err());
    }
}
