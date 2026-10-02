//! z6x keymap --daemon：遥控器按键重映射。
//!
//! 两类按键，两种读取方式：
//!   - 普通按键（方向、确认、返回、菜单等）：读取遥控器的输入设备节点。遥控器在系统中是两个输入设备
//!     （XGIMI RC Keyboard 与 XGIMI RC Consumer Control），同时监听名称匹配的全部设备。
//!     **不独占设备**（不调用 EVIOCGRAB），原有功能不受影响；识别长按后执行配置中的动作。
//!     只支持短按（极米快捷键）与长按两种触发：用户认为双击容易误触（2026-10-02），已去掉。
//!     遥控器断开重连导致节点变化时，每 2 秒重新查找并绑定。
//!   - 极米的四个影视快捷键（酷喵、云视听极光、奇异果、芒果）：它们不经过输入设备节点（getevent 录不到），
//!     而是以极米自定义的按键码（2118～2121）直接进入系统，由极米改过的窗口管理拦截后交给 XRM 服务打开对应应用，
//!     系统的 WindowManager 在日志中完整记录按键过程（2026-10-02 实测）：
//!       按下「interceptKeyTi keyCode=2118 down=true repeatCount=0」，按住约 0.4 秒后每 50ms 一条
//!       repeatCount=1、2、3…，松开「down=false」。
//!     守护进程读取这些日志：按住达到长按时长即执行长按动作，在此之前松开则执行短按动作。
//!     对应的影视应用未安装，原来的动作什么也不会打开，不会冲突。
//!
//! 注意：因为不独占，触发长按的那个普通键在松开时仍会产生它原本的效果（例如长按返回，松开时仍会返回一次）。

use crate::cli::{parse_duration_ms, Args, Fail, Result};
use crate::key::{find_devices, Injector, KEYS};
use std::fs::File;
use std::io::{BufRead, BufReader, Read};
use std::os::fd::AsRawFd;
use std::time::{Duration, Instant};

pub const HELP: &str = "z6x keymap --daemon --config <文件> [--device 名称] [--long 600ms]
z6x keymap --check --config <文件>     只检查配置
  配置文件每行一条：<触发> = <动作>，# 开头为注释。
    触发：press:<极米快捷键>（短按）、long:<极米快捷键或普通键名>（长按，默认按住 600ms）
          极米快捷键：youku（酷喵）、jiguang（云视听极光）、qiyiguo（奇异果）、mango（芒果）、bilibili、
                      wallpaper（壁纸键）、side（调焦键另一侧的键），或按键码 2118 等
    动作：key: <键名> [<键名>…]（注入按键）、sh: <命令>（用 /system/bin/sh 执行，不等待结束）
  示例：
    press:youku = sh: am start -n org.smarttube.stable/com.liskovsoft.smartyoutubetv2.tv.ui.main.SplashActivity
    long:back   = sh: am start -n de.szalkowski.activitylauncher/.MainActivity";

/// 极米影视快捷键：名称 → 极米自定义按键码（来自 XRM 服务日志中的 appName 对照表）。
pub const XGIMI_KEYS: &[(&str, u32, &str)] = &[
    ("youku", 2118, "酷喵（youkutv）"),
    ("jiguang", 2119, "云视听极光（tencenttv）"),
    ("qiyiguo", 2120, "奇异果（aiqiyitv）"),
    ("mango", 2121, "芒果（mangotv）"),
    ("bilibili", 2126, "哔哩哔哩（bilibilitv）"),
    // 调焦键两侧的两个键（2026-10-02 实测）：2116 原为壁纸（极米的壁纸功能已在精简时停用），2117 极米不拦截、应用也不处理
    ("wallpaper", 2116, "壁纸键"),
    ("side", 2117, "调焦键另一侧的键"),
];

fn codes_for(name: &str) -> Vec<u16> {
    let mut v: Vec<u16> = KEYS.iter().filter(|(n, _, _)| *n == name).map(|(_, c, _)| *c).collect();
    // 遥控器上报的是它自己的按键码（如返回为 1、主页为 102），与通用键名一并匹配
    v.extend(crate::key::remote_code(name));
    if let Ok(n) = name.parse::<u16>() {
        v.push(n);
    }
    v
}

fn xgimi_code(name: &str) -> Option<u32> {
    XGIMI_KEYS.iter().find(|(n, _, _)| *n == name).map(|(_, c, _)| *c).or_else(|| name.parse::<u32>().ok().filter(|c| *c >= 2000))
}

#[derive(Debug, Clone, PartialEq)]
pub enum Action {
    Keys(Vec<u16>),
    Shell(String),
}

#[derive(Debug, Clone, Copy, PartialEq)]
pub enum Kind {
    Long,
    Press,
}

#[derive(Debug, PartialEq)]
pub struct Rule {
    pub kind: Kind,
    pub codes: Vec<u16>, // 普通按键（长按）
    pub xgimi: u32,      // 极米快捷键（单按），其他为 0
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
        let (kind, key) = trig.trim().split_once(':').ok_or_else(|| bad("触发应为 long: 或 press: 加键名"))?;
        let key = key.trim();
        let (kind, codes, xgimi) = match kind {
            "long" => match xgimi_code(key) {
                Some(x) => (Kind::Long, vec![], x), // 极米快捷键的长按（从日志识别）
                None => {
                    let c = codes_for(key);
                    if c.is_empty() {
                        return Err(bad("未知键名"));
                    }
                    (Kind::Long, c, 0)
                }
            },
            "press" => (Kind::Press, vec![], xgimi_code(key).ok_or_else(|| bad("press 只用于极米快捷键：youku、jiguang、qiyiguo、mango、bilibili、wallpaper、side"))?),
            _ => return Err(bad("触发只能是 long（长按）或 press（极米快捷键短按）")),
        };
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
        rules.push(Rule { kind, codes, xgimi, key_name: key.to_string(), action });
    }
    Ok(rules)
}

/// 解析 WindowManager 的按键日志：(按键码, 是否按下, 重复次数)。
pub fn parse_key_log(line: &str) -> Option<(u32, bool, u32)> {
    let rest = line.split("interceptKeyTi keyCode=").nth(1)?;
    let (code, rest) = rest.split_once(' ')?;
    let down = rest.strip_prefix("down=")?.starts_with("true");
    let rep = rest.split("repeatCount=").nth(1)?.split_whitespace().next()?;
    Some((code.parse().ok()?, down, rep.parse().ok()?))
}

fn log(msg: &str) {
    let t = std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).map(|d| d.as_secs()).unwrap_or(0);
    eprintln!("[{t}] {msg}");
}

/// 执行动作。注入按键时按需创建虚拟键盘并复用。
fn fire(r: &Rule, injector: &mut Option<Injector>) {
    let kind = match r.kind {
        Kind::Long => "long",
        Kind::Press => "press",
    };
    log(&format!("触发 {kind}:{}", r.key_name));
    match &r.action {
        Action::Shell(cmd) => {
            if let Err(e) = std::process::Command::new("/system/bin/sh").arg("-c").arg(cmd).spawn() {
                log(&format!("执行失败：{e}"));
            }
        }
        Action::Keys(ks) => {
            if injector.is_none() {
                match Injector::uinput(Duration::from_millis(40)) {
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
}

/// 读取 WindowManager 日志，识别极米快捷键的短按与长按。logcat 退出时 5 秒后重新启动。
fn watch_xgimi_keys(rules: std::sync::Arc<Vec<Rule>>, long_ms: Duration) {
    let mut injector = None;
    let xgimi: Vec<u32> = rules.iter().filter(|r| r.xgimi > 0).map(|r| r.xgimi).collect();
    // 每个键：按下时刻、长按是否已触发
    let mut held: std::collections::HashMap<u32, (Instant, bool)> = Default::default();
    loop {
        // -T 1：只读之后的新日志；只保留 WindowManager 的 D 级日志（含按键过程）
        let child = std::process::Command::new("logcat")
            .args(["-v", "brief", "-T", "1", "WindowManager:D", "*:S"])
            .stdout(std::process::Stdio::piped())
            .stderr(std::process::Stdio::null())
            .spawn();
        let Ok(mut child) = child else {
            log("无法启动 logcat，5 秒后重试");
            std::thread::sleep(Duration::from_secs(5));
            continue;
        };
        log("已开始读取日志，识别极米快捷键");
        if let Some(out) = child.stdout.take() {
            for line in BufReader::new(out).lines().map_while(|l| l.ok()) {
                let Some((code, down, rep)) = parse_key_log(&line) else { continue };
                if !xgimi.contains(&code) {
                    continue;
                }
                let find = |k: Kind| rules.iter().find(|r| r.kind == k && r.xgimi == code);
                match (down, rep) {
                    (true, 0) => {
                        held.insert(code, (Instant::now(), false));
                    }
                    (true, _) => {
                        // 按住时的重复：达到长按时长且尚未触发时，执行长按动作
                        if let Some((t, fired)) = held.get_mut(&code) {
                            if !*fired && t.elapsed() >= long_ms {
                                *fired = true;
                                match find(Kind::Long) {
                                    Some(r) => fire(r, &mut injector),
                                    None => log(&format!("极米快捷键 {code} 长按未配置动作")),
                                }
                            }
                        }
                    }
                    (false, _) => {
                        // 松开：长按未触发过即为短按
                        if let Some((_, fired)) = held.remove(&code) {
                            if !fired {
                                match find(Kind::Press) {
                                    Some(r) => fire(r, &mut injector),
                                    None => log(&format!("极米快捷键 {code} 短按未配置动作")),
                                }
                            }
                        }
                    }
                }
            }
        }
        let _ = child.wait(); // 忽略 SIGCHLD 时返回错误，不影响循环
        log("logcat 已退出，5 秒后重新启动");
        std::thread::sleep(Duration::from_secs(5));
    }
}

struct KeyState {
    down_at: Option<Instant>,
    long_fired: bool,
}

pub fn main(raw: &[String]) -> Result<()> {
    let a = Args::parse(raw, &["config", "device", "long"])?;
    a.reject_unknown(&["daemon", "check"])?;
    let path = a.opt("config").ok_or_else(|| Fail::usage("需要 --config <文件>"))?;
    let text = std::fs::read_to_string(path).map_err(|e| Fail::io(&format!("读取 {path}"), e))?;
    let rules = parse_config(&text)?;
    if a.flag("check") || !a.flag("daemon") {
        println!("配置正确：{} 条规则", rules.len());
        for r in &rules {
            println!("  {:?}:{} → {:?}", r.kind, r.key_name, r.action);
        }
        if !a.flag("daemon") && !a.flag("check") {
            println!("（加 --daemon 开始运行）");
        }
        return Ok(());
    }
    let long_ms = Duration::from_millis(parse_duration_ms(a.opt("long").unwrap_or("600ms"))?);
    let dev_name = a.opt("device").unwrap_or("XGIMI RC").to_string();
    // 执行 sh 动作后不等待：忽略 SIGCHLD，子进程结束后由内核自动回收，不留僵尸进程
    unsafe { libc::signal(libc::SIGCHLD, libc::SIG_IGN) };
    // 配置文件被修改（例如在 hub 的「遥控器按键」页面保存）时正常退出，由 z6x run 立即重新启动以读入新配置
    let cfg_path = path.to_string();
    let mtime = |p: &str| std::fs::metadata(p).and_then(|m| m.modified()).ok();
    let start_mtime = mtime(&cfg_path);
    std::thread::spawn(move || loop {
        std::thread::sleep(Duration::from_secs(2));
        if mtime(&cfg_path) != start_mtime {
            log("配置文件已修改，退出以重新加载");
            std::process::exit(0);
        }
    });
    let rules = std::sync::Arc::new(rules);
    if rules.iter().any(|r| r.xgimi > 0) {
        let r = rules.clone();
        std::thread::spawn(move || watch_xgimi_keys(r, long_ms));
    }
    log(&format!("keymap 启动：{} 条规则，设备名包含「{dev_name}」", rules.len()));
    if !rules.iter().any(|r| r.xgimi == 0) {
        loop {
            std::thread::sleep(Duration::from_secs(3600)); // 只有极米快捷键规则：不需要读设备节点
        }
    }
    let mut injector: Option<Injector> = None;
    loop {
        let paths = find_devices(&dev_name);
        let mut files: Vec<File> = paths.iter().filter_map(|p| File::open(p).ok()).collect();
        if files.is_empty() {
            std::thread::sleep(Duration::from_secs(2));
            continue;
        }
        log(&format!("已绑定 {}", paths.join("、")));
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
            let mut pfds: Vec<libc::pollfd> = files.iter().map(|f| libc::pollfd { fd: f.as_raw_fd(), events: libc::POLLIN, revents: 0 }).collect();
            let n = unsafe { libc::poll(pfds.as_mut_ptr(), pfds.len() as libc::nfds_t, timeout) };
            if n == 0 {
                let now = Instant::now();
                for (code, s) in states.iter_mut() {
                    if let Some(d) = s.down_at {
                        if !s.long_fired && now >= d + long_ms {
                            s.long_fired = true;
                            if let Some(r) = rules.iter().find(|r| r.kind == Kind::Long && r.xgimi == 0 && r.codes.contains(code)) {
                                fire(r, &mut injector);
                            }
                        }
                    }
                }
                continue;
            }
            if n < 0 {
                break 'dev;
            }
            for (i, p) in pfds.iter().enumerate() {
                if p.revents & (libc::POLLERR | libc::POLLHUP | libc::POLLNVAL) != 0 {
                    break 'dev; // 设备消失（遥控器断开）：回到外层循环重新查找
                }
                if p.revents & libc::POLLIN == 0 {
                    continue;
                }
                if files[i].read_exact(&mut buf).is_err() {
                    break 'dev;
                }
                let typ = u16::from_ne_bytes([buf[16], buf[17]]);
                let code = u16::from_ne_bytes([buf[18], buf[19]]);
                let value = i32::from_ne_bytes([buf[20], buf[21], buf[22], buf[23]]);
                if typ != 1 {
                    continue;
                }
                let s = states.entry(code).or_insert(KeyState { down_at: None, long_fired: false });
                match value {
                    1 => {
                        s.down_at = Some(Instant::now());
                        s.long_fired = false;
                    }
                    0 => {
                        s.down_at = None;
                        s.long_fired = false;
                    }
                    _ => {} // 2 为按住时的自动重复，忽略
                }
            }
        }
        files.clear();
        log("遥控器已断开，等待重新连接");
        std::thread::sleep(Duration::from_secs(2));
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn config() {
        let r = parse_config("# 注释\nlong:back = sh: am start -n a/.B\nlong:home = key: menu ok\npress:youku = sh: echo 1\npress:2121 = key: home\n").ok().unwrap();
        assert_eq!(r.len(), 4);
        assert!(r[0].kind == Kind::Long && r[0].codes.contains(&158) && r[0].codes.contains(&1));
        assert_eq!(r[0].action, Action::Shell("am start -n a/.B".into()));
        assert_eq!(r[1].action, Action::Keys(vec![139, 232]));
        assert_eq!((r[2].kind, r[2].xgimi), (Kind::Press, 2118));
        assert_eq!(r[3].xgimi, 2121);
        assert!(parse_config("long:nokey = key: menu").is_err());
        assert!(parse_config("press:back = key: menu").is_err());
        assert!(parse_config("tap:back = key: menu").is_err());
        assert!(parse_config("double:back = key: menu").is_err(), "双击已去掉");
        assert!(parse_config("long:back = run: x").is_err());
    }

    #[test]
    fn key_log_line() {
        let l = "D/WindowManager( 3309): interceptKeyTi keyCode=2118 down=true repeatCount=3 keyguardOn=false canceled=false";
        assert_eq!(parse_key_log(l), Some((2118, true, 3)));
        assert_eq!(parse_key_log("D/WindowManager( 3309): interceptKeyTi keyCode=2118 down=false repeatCount=0 keyguardOn=false"), Some((2118, false, 0)));
        assert_eq!(parse_key_log("D/WindowManager( 3309): something else"), None);
        let r = parse_config("long:youku = key: home\npress:youku = key: back\n").ok().unwrap();
        assert_eq!((r[0].kind, r[0].xgimi), (Kind::Long, 2118));
    }
}
