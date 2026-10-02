//! z6x hash：查找重复文件，只报告，不删除。
//!
//! 三步筛选，尽量少读数据：先按大小分组；同样大小的再比较首尾各 64KB；仍相同的计算整个文件的 xxHash64。
//! xxHash64 在这里手写实现（约 60 行），不引入依赖。规格原写 xxh3，为免依赖改用同系列的 xxHash64，碰撞概率同样可忽略。

use crate::cli::{array, human_bytes, quote, Args, Fail, Json, Result};
use std::collections::HashMap;
use std::fs::{self, File};
use std::io::{Read, Seek, SeekFrom};
use std::path::{Path, PathBuf};

// ---------------- xxHash64 ----------------

const P1: u64 = 0x9E3779B185EBCA87;
const P2: u64 = 0xC2B2AE3D27D4EB4F;
const P3: u64 = 0x165667B19E3779F9;
const P4: u64 = 0x85EBCA77C2B2AE63;
const P5: u64 = 0x27D4EB2F165667C5;

fn round(acc: u64, v: u64) -> u64 {
    acc.wrapping_add(v.wrapping_mul(P2)).rotate_left(31).wrapping_mul(P1)
}
fn merge(acc: u64, v: u64) -> u64 {
    (acc ^ round(0, v)).wrapping_mul(P1).wrapping_add(P4)
}

/// 流式 xxHash64：可分多次输入数据。
pub struct Xxh64 {
    v: [u64; 4],
    buf: [u8; 32],
    buf_len: usize,
    total: u64,
}

impl Xxh64 {
    pub fn new() -> Xxh64 {
        Xxh64 { v: [P1.wrapping_add(P2), P2, 0, 0u64.wrapping_sub(P1)], buf: [0; 32], buf_len: 0, total: 0 }
    }
    pub fn update(&mut self, mut data: &[u8]) {
        self.total += data.len() as u64;
        if self.buf_len > 0 {
            let n = (32 - self.buf_len).min(data.len());
            self.buf[self.buf_len..self.buf_len + n].copy_from_slice(&data[..n]);
            self.buf_len += n;
            data = &data[n..];
            if self.buf_len < 32 {
                return;
            }
            let b = self.buf;
            self.stripe(&b);
            self.buf_len = 0;
        }
        while data.len() >= 32 {
            self.stripe(&data[..32]);
            data = &data[32..];
        }
        self.buf[..data.len()].copy_from_slice(data);
        self.buf_len = data.len();
    }
    fn stripe(&mut self, s: &[u8]) {
        for i in 0..4 {
            self.v[i] = round(self.v[i], u64::from_le_bytes(s[i * 8..i * 8 + 8].try_into().unwrap()));
        }
    }
    pub fn finish(&self) -> u64 {
        let mut h = if self.total >= 32 {
            let [a, b, c, d] = self.v;
            let mut h = a.rotate_left(1).wrapping_add(b.rotate_left(7)).wrapping_add(c.rotate_left(12)).wrapping_add(d.rotate_left(18));
            for v in self.v {
                h = merge(h, v);
            }
            h
        } else {
            P5
        };
        h = h.wrapping_add(self.total);
        let mut rest = &self.buf[..self.buf_len];
        while rest.len() >= 8 {
            h = (h ^ round(0, u64::from_le_bytes(rest[..8].try_into().unwrap()))).rotate_left(27).wrapping_mul(P1).wrapping_add(P4);
            rest = &rest[8..];
        }
        if rest.len() >= 4 {
            h = (h ^ (u32::from_le_bytes(rest[..4].try_into().unwrap()) as u64).wrapping_mul(P1)).rotate_left(23).wrapping_mul(P2).wrapping_add(P3);
            rest = &rest[4..];
        }
        for &b in rest {
            h = (h ^ (b as u64).wrapping_mul(P5)).rotate_left(11).wrapping_mul(P1);
        }
        h ^= h >> 33;
        h = h.wrapping_mul(P2);
        h ^= h >> 29;
        h = h.wrapping_mul(P3);
        h ^ (h >> 32)
    }
}

// ---------------- 查重 ----------------

const SAMPLE: u64 = 64 * 1024;

fn walk(dir: &Path, min: u64, out: &mut Vec<(PathBuf, u64)>, errors: &mut u32) {
    let Ok(rd) = fs::read_dir(dir) else {
        *errors += 1;
        return;
    };
    for e in rd.flatten() {
        let Ok(ft) = e.file_type() else { continue };
        if ft.is_symlink() {
            continue; // 不跟随符号链接，避免重复计算或循环
        }
        if ft.is_dir() {
            walk(&e.path(), min, out, errors);
        } else if ft.is_file() {
            if let Ok(m) = e.metadata() {
                if m.len() >= min {
                    out.push((e.path(), m.len()));
                }
            }
        }
    }
}

/// 首尾各 64KB 的哈希（小文件即整个文件）。
fn sample_hash(p: &Path, size: u64) -> std::io::Result<u64> {
    let mut f = File::open(p)?;
    let mut h = Xxh64::new();
    let mut buf = vec![0u8; SAMPLE.min(size) as usize];
    f.read_exact(&mut buf)?;
    h.update(&buf);
    if size > SAMPLE * 2 {
        f.seek(SeekFrom::Start(size - SAMPLE))?;
        f.read_exact(&mut buf)?;
        h.update(&buf);
    } else if size > SAMPLE {
        let mut rest = vec![];
        f.read_to_end(&mut rest)?;
        h.update(&rest);
    }
    Ok(h.finish())
}

fn full_hash(p: &Path) -> std::io::Result<u64> {
    let mut f = File::open(p)?;
    let mut h = Xxh64::new();
    let mut buf = vec![0u8; 1 << 20];
    loop {
        let n = f.read(&mut buf)?;
        if n == 0 {
            return Ok(h.finish());
        }
        h.update(&buf[..n]);
    }
}

/// 把 (路径, 大小) 列表按 key 函数分组，只保留两个以上的组。
fn regroup<K: std::hash::Hash + Eq>(items: Vec<(PathBuf, u64)>, mut key: impl FnMut(&Path, u64) -> Option<K>) -> Vec<Vec<(PathBuf, u64)>> {
    let mut m: HashMap<K, Vec<(PathBuf, u64)>> = HashMap::new();
    for (p, s) in items {
        if let Some(k) = key(&p, s) {
            m.entry(k).or_default().push((p, s));
        }
    }
    m.into_values().filter(|g| g.len() > 1).collect()
}

pub fn find_duplicates(dirs: &[PathBuf], min: u64) -> (Vec<Vec<(PathBuf, u64)>>, usize, u32) {
    let mut files = vec![];
    let mut errors = 0;
    for d in dirs {
        walk(d, min, &mut files, &mut errors);
    }
    let scanned = files.len();
    let mut groups = vec![];
    for g in regroup(files, |_, s| Some(s)) {
        for g2 in regroup(g, |p, s| sample_hash(p, s).ok()) {
            let size = g2[0].1;
            if size <= SAMPLE * 2 {
                groups.push(g2); // 已读过整个文件，无需再算全文件哈希
            } else {
                groups.extend(regroup(g2, |p, _| full_hash(p).ok()));
            }
        }
    }
    for g in groups.iter_mut() {
        g.sort();
    }
    groups.sort_by(|a, b| (b[0].1 * (b.len() as u64 - 1)).cmp(&(a[0].1 * (a.len() as u64 - 1))));
    (groups, scanned, errors)
}

pub fn main(raw: &[String]) -> Result<()> {
    let a = Args::parse(raw, &["min"])?;
    a.reject_unknown(&["json"])?;
    if a.positional.is_empty() {
        return Err(Fail::usage("需要至少一个目录，例如 z6x hash /storage/emulated/0/Download"));
    }
    let dirs: Vec<PathBuf> = a.positional.iter().map(PathBuf::from).collect();
    for d in &dirs {
        if !d.is_dir() {
            return Err(Fail::usage(format!("不是目录：{}", d.display())));
        }
    }
    let (groups, scanned, errors) = find_duplicates(&dirs, a.num("min", 1u64)?);
    let saving: u64 = groups.iter().map(|g| g[0].1 * (g.len() as u64 - 1)).sum();
    if a.flag("json") {
        let gs = groups.iter().map(|g| {
            Json::obj().num("size", g[0].1).raw("files", &array(g.iter().map(|(p, _)| quote(&p.to_string_lossy())))).end()
        });
        println!("{}", Json::obj().num("scanned", scanned).num("unreadable_dirs", errors).num("saving_bytes", saving).raw("groups", &array(gs)).end());
        return Ok(());
    }
    for g in &groups {
        println!("{}  × {}", human_bytes(g[0].1), g.len());
        for (p, _) in g {
            println!("    {}", p.display());
        }
    }
    println!("扫描 {scanned} 个文件，{} 组重复，删除多余副本可节省 {}（本命令不删除任何文件）", groups.len(), human_bytes(saving));
    if errors > 0 {
        println!("另有 {errors} 个目录无法读取");
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    fn xxh(data: &[u8]) -> u64 {
        let mut h = Xxh64::new();
        h.update(data);
        h.finish()
    }

    #[test]
    fn xxh64_known_values() {
        // 官方实现（seed 0）的已知结果
        assert_eq!(xxh(b""), 0xEF46DB3751D8E999);
        assert_eq!(xxh(b"a"), 0xD24EC4F1A98C6E5B);
        assert_eq!(xxh(b"abc"), 0x44BC2CF5AD770999);
        let long: Vec<u8> = (0..1000u32).map(|i| (i % 251) as u8).collect();
        // 分块输入与一次输入结果相同
        let mut h = Xxh64::new();
        for c in long.chunks(7) {
            h.update(c);
        }
        assert_eq!(h.finish(), xxh(&long));
    }

    #[test]
    fn duplicates() {
        let d = std::env::temp_dir().join(format!("z6x-hash-test-{}", std::process::id()));
        let _ = fs::remove_dir_all(&d);
        fs::create_dir_all(d.join("sub")).unwrap();
        let big: Vec<u8> = (0..300_000u32).map(|i| (i % 253) as u8).collect();
        let mut big2 = big.clone();
        big2[150_000] ^= 1; // 只有中间一个字节不同：首尾采样相同，必须靠全文件哈希区分
        fs::write(d.join("a.bin"), &big).unwrap();
        fs::write(d.join("sub/b.bin"), &big).unwrap();
        fs::write(d.join("c.bin"), &big2).unwrap();
        fs::write(d.join("x.txt"), b"same").unwrap();
        fs::write(d.join("sub/y.txt"), b"same").unwrap();
        fs::write(d.join("z.txt"), b"diff").unwrap();
        let (groups, scanned, _) = find_duplicates(&[d.clone()], 1);
        assert_eq!(scanned, 6);
        assert_eq!(groups.len(), 2);
        assert_eq!(groups[0].len(), 2); // 大文件组排在前面（可节省空间更多）
        assert!(groups[0].iter().all(|(p, _)| !p.ends_with("c.bin")));
        assert!(d.join("a.bin").exists() && d.join("sub/b.bin").exists(), "不得删除任何文件");
        fs::remove_dir_all(&d).unwrap();
    }
}
