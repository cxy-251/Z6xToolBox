//! z6x watch：用 inotify 监听目录（含子目录），文件写入完成（CLOSE_WRITE）或移入（MOVED_TO）时输出一行。
//! 只在写入完成时报告，写入过程中不会重复输出；新建的子目录会自动加入监听。

use crate::cli::{quote, Args, Fail, Json, Result};
use std::collections::HashMap;
use std::ffi::CString;
use std::path::{Path, PathBuf};

struct Watcher {
    fd: i32,
    dirs: HashMap<i32, PathBuf>,
    full: bool, // 是否已达到 max_user_watches 上限
}

const MASK: u32 = libc::IN_CLOSE_WRITE | libc::IN_MOVED_TO | libc::IN_CREATE | libc::IN_DELETE_SELF;

impl Watcher {
    fn add(&mut self, dir: &Path) {
        let Ok(c) = CString::new(dir.as_os_str().as_encoded_bytes()) else { return };
        let wd = unsafe { libc::inotify_add_watch(self.fd, c.as_ptr(), MASK) };
        if wd < 0 {
            let e = std::io::Error::last_os_error();
            if e.raw_os_error() == Some(libc::ENOSPC) && !self.full {
                self.full = true;
                eprintln!("z6x watch：已达到系统的监听上限（/proc/sys/fs/inotify/max_user_watches），其余子目录不再监听");
            }
            return;
        }
        self.dirs.insert(wd, dir.to_path_buf());
        if let Ok(rd) = std::fs::read_dir(dir) {
            for e in rd.flatten() {
                if e.file_type().map(|t| t.is_dir() && !t.is_symlink()).unwrap_or(false) {
                    self.add(&e.path());
                }
            }
        }
    }
}

pub fn main(raw: &[String]) -> Result<()> {
    let a = Args::parse(raw, &["exec"])?;
    a.reject_unknown(&["json"])?;
    if a.positional.is_empty() {
        return Err(Fail::usage("需要至少一个目录"));
    }
    let fd = unsafe { libc::inotify_init1(libc::IN_CLOEXEC) };
    if fd < 0 {
        return Err(Fail::io("inotify_init1", std::io::Error::last_os_error()));
    }
    let mut w = Watcher { fd, dirs: HashMap::new(), full: false };
    for d in &a.positional {
        let p = PathBuf::from(d);
        if !p.is_dir() {
            return Err(Fail::usage(format!("不是目录：{d}")));
        }
        w.add(&p);
    }
    if w.dirs.is_empty() {
        return Err(Fail::error("没有可以监听的目录（权限不足？）"));
    }
    eprintln!("z6x watch：监听 {} 个目录", w.dirs.len());
    unsafe { libc::signal(libc::SIGCHLD, libc::SIG_IGN) };
    let exec = a.opt("exec").map(|s| s.to_string());
    let mut buf = vec![0u8; 64 * 1024];
    loop {
        let n = unsafe { libc::read(fd, buf.as_mut_ptr() as *mut libc::c_void, buf.len()) };
        if n <= 0 {
            return Err(Fail::io("读取 inotify 事件", std::io::Error::last_os_error()));
        }
        let mut off = 0usize;
        while off + 16 <= n as usize {
            // struct inotify_event { int wd; uint32 mask; uint32 cookie; uint32 len; char name[]; }
            let wd = i32::from_ne_bytes(buf[off..off + 4].try_into().unwrap());
            let mask = u32::from_ne_bytes(buf[off + 4..off + 8].try_into().unwrap());
            let len = u32::from_ne_bytes(buf[off + 12..off + 16].try_into().unwrap()) as usize;
            let name_bytes = &buf[off + 16..off + 16 + len];
            off += 16 + len;
            let name = String::from_utf8_lossy(name_bytes.split(|b| *b == 0).next().unwrap_or(&[])).to_string();
            if mask & libc::IN_DELETE_SELF != 0 {
                w.dirs.remove(&wd);
                continue;
            }
            let Some(dir) = w.dirs.get(&wd).cloned() else { continue };
            let path = dir.join(&name);
            if mask & libc::IN_ISDIR != 0 {
                if mask & (libc::IN_CREATE | libc::IN_MOVED_TO) != 0 {
                    w.add(&path); // 新建或移入的子目录也加入监听
                }
                continue;
            }
            let event = if mask & libc::IN_CLOSE_WRITE != 0 {
                "close_write"
            } else if mask & libc::IN_MOVED_TO != 0 {
                "moved_to"
            } else {
                continue; // IN_CREATE：文件刚创建、尚未写完，不报告
            };
            let ps = path.to_string_lossy().to_string();
            if a.flag("json") {
                println!("{}", Json::obj().str("event", event).raw("path", &quote(&ps)).end());
            } else {
                println!("{event}\t{ps}");
            }
            if let Some(cmd) = &exec {
                let _ = std::process::Command::new("/system/bin/sh").arg("-c").arg(cmd).env("Z6X_PATH", &ps).env("Z6X_EVENT", event).spawn()
                    .or_else(|_| std::process::Command::new("/bin/sh").arg("-c").arg(cmd).env("Z6X_PATH", &ps).env("Z6X_EVENT", event).spawn());
            }
        }
    }
}
