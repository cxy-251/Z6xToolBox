//! z6x run：极简的进程守护。启动一个命令，退出或崩溃后按退避间隔重启，并记录退出原因。
//!
//! 退避：1、2、4…最长 60 秒；子进程连续运行超过 60 秒后，间隔恢复为 1 秒。
//! 收到 SIGTERM 或 SIGINT 时，把信号转发给子进程，等它退出后自身也退出，不再重启。

use crate::cli::{Args, Fail, Result};
use std::process::Command;
use std::sync::atomic::{AtomicBool, AtomicI32, Ordering};
use std::time::{Duration, Instant};

pub const HELP: &str = "z6x run [--name 名称] [--max-restarts N] -- <命令> [参数]
  启动命令并守护：退出或崩溃后重启，间隔 1、2、4… 最长 60 秒（连续运行超过 60 秒后恢复为 1 秒）。
  退出原因（退出码或信号）写入 stderr。收到 SIGTERM / SIGINT 时转发给子进程并停止守护。
  示例：z6x run --name hub -- ./z6x-hub -c hub.yaml";

static STOP: AtomicBool = AtomicBool::new(false);
static CHILD: AtomicI32 = AtomicI32::new(0);

extern "C" fn on_signal(sig: libc::c_int) {
    STOP.store(true, Ordering::SeqCst);
    let pid = CHILD.load(Ordering::SeqCst);
    if pid > 0 {
        unsafe { libc::kill(pid, sig) };
    }
}

fn log(name: &str, msg: &str) {
    let t = std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).map(|d| d.as_secs()).unwrap_or(0);
    eprintln!("[{t}] z6x run {name}：{msg}");
}

/// 退出状态的说明：正常退出给退出码，被信号结束给信号名。
pub fn describe(status: std::process::ExitStatus) -> String {
    use std::os::unix::process::ExitStatusExt;
    if let Some(c) = status.code() {
        return format!("退出码 {c}");
    }
    match status.signal() {
        Some(9) => "被 SIGKILL 结束（可能是内存不足或被系统清理）".into(),
        Some(11) => "段错误（SIGSEGV）".into(),
        Some(6) => "异常终止（SIGABRT）".into(),
        Some(15) => "被 SIGTERM 结束".into(),
        Some(s) => format!("被信号 {s} 结束"),
        None => "未知".into(),
    }
}

pub fn next_backoff(cur: Duration, ran: Duration) -> Duration {
    if ran > Duration::from_secs(60) { Duration::from_secs(1) } else { (cur * 2).min(Duration::from_secs(60)) }
}

pub fn main(raw: &[String]) -> Result<()> {
    let a = Args::parse(raw, &["name", "max-restarts"])?;
    a.reject_unknown(&[])?;
    if a.rest.is_empty() {
        return Err(Fail::usage("需要在 -- 之后给出要守护的命令"));
    }
    let name = a.opt("name").unwrap_or(&a.rest[0]).to_string();
    let max: u64 = a.num("max-restarts", u64::MAX)?;
    unsafe {
        libc::signal(libc::SIGTERM, on_signal as *const () as usize);
        libc::signal(libc::SIGINT, on_signal as *const () as usize);
    }
    let mut backoff = Duration::from_secs(1);
    let mut restarts = 0u64;
    loop {
        let start = Instant::now();
        let mut child = Command::new(&a.rest[0]).args(&a.rest[1..]).spawn().map_err(|e| Fail::io(&format!("启动 {}", a.rest[0]), e))?;
        CHILD.store(child.id() as i32, Ordering::SeqCst);
        log(&name, &format!("已启动（PID {}）", child.id()));
        let status = child.wait().map_err(|e| Fail::io("等待子进程", e))?;
        CHILD.store(0, Ordering::SeqCst);
        let ran = start.elapsed();
        log(&name, &format!("{}，运行了 {} 秒", describe(status), ran.as_secs()));
        if STOP.load(Ordering::SeqCst) {
            log(&name, "收到停止信号，不再重启");
            return Ok(());
        }
        restarts += 1;
        if restarts > max {
            return Err(Fail::error(format!("已重启 {max} 次，停止守护")));
        }
        // 本次等待 delay，下次加倍；连续运行超过 60 秒说明不是启动即崩溃，间隔恢复为 1 秒。
        // 正常退出（退出码 0，例如 keymap 因配置修改而退出）属于计划内重启，同样不加长间隔
        if ran > Duration::from_secs(60) || status.success() {
            backoff = Duration::from_secs(1);
        }
        let delay = backoff;
        backoff = if status.success() { Duration::from_secs(1) } else { next_backoff(backoff, ran) };
        log(&name, &format!("{} 秒后重启（第 {restarts} 次）", delay.as_secs()));
        let until = Instant::now() + delay;
        while Instant::now() < until {
            if STOP.load(Ordering::SeqCst) {
                return Ok(());
            }
            std::thread::sleep(Duration::from_millis(100));
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn backoff() {
        let s = Duration::from_secs;
        assert_eq!(next_backoff(s(1), s(5)), s(2));
        assert_eq!(next_backoff(s(32), s(5)), s(60));
        assert_eq!(next_backoff(s(60), s(5)), s(60));
        assert_eq!(next_backoff(s(60), s(61)), s(1));
    }

    #[test]
    fn describes_exit() {
        let st = Command::new("/bin/sh").arg("-c").arg("exit 3").status().unwrap();
        assert_eq!(describe(st), "退出码 3");
        let st = Command::new("/bin/sh").arg("-c").arg("kill -9 $$").status().unwrap();
        assert!(describe(st).contains("SIGKILL"));
    }
}
