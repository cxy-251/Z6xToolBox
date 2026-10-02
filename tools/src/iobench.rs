//! z6x iobench：存储读写测速（顺序写、顺序读、4KB 随机读），测完删除测试文件。
//!
//! 优先用 O_DIRECT 绕过页缓存，测得的是存储本身的速度；文件系统不支持时（FAT32、exFAT 常见）
//! 改为普通读写：写入后 fsync，读取前用 posix_fadvise 丢弃该文件的缓存，并在结果中注明。

use crate::cli::{Args, Fail, Json, Result};
use std::fs::OpenOptions;
use std::io::{Read, Seek, SeekFrom, Write};
use std::os::unix::fs::OpenOptionsExt;
use std::path::PathBuf;
use std::time::{Duration, Instant};

/// O_DIRECT 要求缓冲区按 4096 字节对齐：多分配一页，取对齐的切片。
fn aligned(size: usize) -> (Vec<u8>, usize) {
    let v = vec![0u8; size + 4096];
    let off = (4096 - (v.as_ptr() as usize % 4096)) % 4096;
    (v, off)
}

fn open(path: &PathBuf, write: bool, direct: bool) -> std::io::Result<std::fs::File> {
    let mut o = OpenOptions::new();
    if write {
        o.write(true).create(true).truncate(true);
    } else {
        o.read(true);
    }
    if direct {
        o.custom_flags(libc::O_DIRECT);
    }
    o.open(path)
}

/// 简单的伪随机数（xorshift），用于随机读的位置，不需要密码学强度。
struct Rng(u64);
impl Rng {
    fn next(&mut self) -> u64 {
        self.0 ^= self.0 << 13;
        self.0 ^= self.0 >> 7;
        self.0 ^= self.0 << 17;
        self.0
    }
}

pub fn main(raw: &[String]) -> Result<()> {
    let a = Args::parse(raw, &["size", "seconds"])?;
    a.reject_unknown(&["json"])?;
    let dir = PathBuf::from(a.positional.first().ok_or_else(|| Fail::usage("需要一个目录，例如 z6x iobench /storage/1A2B-3C4D"))?);
    if !dir.is_dir() {
        return Err(Fail::usage(format!("不是目录：{}", dir.display())));
    }
    let size_mb: u64 = a.num("size", 256)?;
    let seconds: u64 = a.num("seconds", 5)?;
    let path = dir.join(format!(".z6x-iobench-{}", std::process::id()));
    // 先探测 O_DIRECT 是否可用（不支持时 open 返回 EINVAL）
    let direct = match open(&path, true, true) {
        Ok(_) => true,
        Err(e) if e.raw_os_error() == Some(libc::EINVAL) => false,
        Err(e) => return Err(Fail::io("创建测试文件", e)),
    };
    let result = run(&path, size_mb, seconds, direct);
    let _ = std::fs::remove_file(&path); // 无论成功与否都删除测试文件
    let (w, r, iops) = result.map_err(|e| Fail::io("测速", e))?;
    if a.flag("json") {
        println!("{}", Json::obj().str("dir", &dir.to_string_lossy()).num("size_mb", size_mb).bool("o_direct", direct)
            .f2("seq_write_mb_s", w).f2("seq_read_mb_s", r).f2("rand_read_4k_iops", iops).end());
    } else {
        println!("目录 {}（测试文件 {size_mb} MB，{}）", dir.display(), if direct { "O_DIRECT" } else { "不支持 O_DIRECT，改用普通读写并丢弃缓存" });
        println!("顺序写   {w:>8.1} MB/s\n顺序读   {r:>8.1} MB/s\n随机读 4K {iops:>7.0} IOPS（{:.1} MB/s）", iops * 4.0 / 1024.0);
    }
    Ok(())
}

fn run(path: &PathBuf, size_mb: u64, seconds: u64, direct: bool) -> std::io::Result<(f64, f64, f64)> {
    let chunk = 1 << 20;
    let (mut buf, off) = aligned(chunk);
    for (i, b) in buf[off..off + chunk].iter_mut().enumerate() {
        *b = (i * 31 % 251) as u8; // 非零数据，避免被文件系统特殊处理
    }
    // 顺序写
    let mut f = open(path, true, direct)?;
    let t = Instant::now();
    for _ in 0..size_mb {
        f.write_all(&buf[off..off + chunk])?;
    }
    f.sync_all()?;
    let write = size_mb as f64 / t.elapsed().as_secs_f64();
    drop(f);
    // 顺序读（非 O_DIRECT 时先丢弃缓存，否则读到的是内存）
    let mut f = open(path, false, direct)?;
    if !direct {
        unsafe { libc::posix_fadvise(std::os::fd::AsRawFd::as_raw_fd(&f), 0, 0, libc::POSIX_FADV_DONTNEED) };
    }
    let t = Instant::now();
    let mut total = 0usize;
    loop {
        let n = f.read(&mut buf[off..off + chunk])?;
        if n == 0 {
            break;
        }
        total += n;
    }
    let read = total as f64 / 1048576.0 / t.elapsed().as_secs_f64();
    // 4KB 随机读，持续 seconds 秒
    if !direct {
        unsafe { libc::posix_fadvise(std::os::fd::AsRawFd::as_raw_fd(&f), 0, 0, libc::POSIX_FADV_DONTNEED) };
    }
    let blocks = size_mb * 256;
    let mut rng = Rng(0x9E3779B97F4A7C15);
    let t = Instant::now();
    let mut ops = 0u64;
    while t.elapsed() < Duration::from_secs(seconds) {
        f.seek(SeekFrom::Start(rng.next() % blocks * 4096))?;
        f.read_exact(&mut buf[off..off + 4096])?;
        ops += 1;
    }
    Ok((write, read, ops as f64 / t.elapsed().as_secs_f64()))
}
