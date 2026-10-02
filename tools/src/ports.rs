//! z6x ports：监听端口及其所属 uid 与应用。
//!
//! 读取 /proc/net/{tcp,tcp6,udp,udp6}；uid 对应的包名通过 `pm list packages -U` 获得
//! （/data/system/packages.list 对 shell 不可读，已在投影仪上实测）。系统 uid（< 10000）按 Android 的固定名称显示。

use crate::cli::{array, Args, Json, Result};
use std::collections::BTreeMap;
use std::fs;
use std::net::{Ipv4Addr, Ipv6Addr};

pub struct Sock {
    pub proto: &'static str,
    pub addr: String,
    pub port: u16,
    pub uid: u32,
    pub state: &'static str,
}

/// /proc/net/tcp 中的地址为小端十六进制：IPv4 为 8 位，IPv6 为 32 位（4 个 32 位小端字）。
fn parse_addr(hex: &str) -> Option<(String, u16)> {
    let (a, p) = hex.split_once(':')?;
    let port = u16::from_str_radix(p, 16).ok()?;
    let ip = match a.len() {
        8 => Ipv4Addr::from(u32::from_str_radix(a, 16).ok()?.swap_bytes()).to_string(),
        32 => {
            let mut b = [0u8; 16];
            for w in 0..4 {
                let v = u32::from_str_radix(&a[w * 8..w * 8 + 8], 16).ok()?.swap_bytes();
                b[w * 4..w * 4 + 4].copy_from_slice(&v.to_be_bytes());
            }
            let v6 = Ipv6Addr::from(b);
            match v6.to_ipv4_mapped() {
                Some(v4) => v4.to_string(),
                None => v6.to_string(),
            }
        }
        _ => return None,
    };
    Some((ip, port))
}

fn tcp_state(code: &str) -> &'static str {
    match code {
        "0A" => "LISTEN",
        "01" => "ESTABLISHED",
        "06" => "TIME_WAIT",
        "08" => "CLOSE_WAIT",
        _ => "OTHER",
    }
}

pub fn parse_table(text: &str, proto: &'static str) -> Vec<Sock> {
    let mut out = vec![];
    for line in text.lines().skip(1) {
        let f: Vec<&str> = line.split_whitespace().collect();
        if f.len() < 8 {
            continue;
        }
        let Some((addr, port)) = parse_addr(f[1]) else { continue };
        let state = if proto.starts_with("udp") { if f[3] == "07" { "BOUND" } else { "OTHER" } } else { tcp_state(f[3]) };
        out.push(Sock { proto, addr, port, uid: f[7].parse().unwrap_or(0), state });
    }
    out
}

/// Android 系统 uid 的固定名称（android_filesystem_config.h）。
fn system_uid(uid: u32) -> Option<&'static str> {
    Some(match uid {
        0 => "root",
        1000 => "system",
        1001 => "radio",
        1002 => "bluetooth",
        1010 => "wifi",
        1013 => "media",
        1020 => "mdnsr",
        1021 => "gps",
        1041 => "audioserver",
        1051 => "nfc",
        1052 => "dns",
        1068 => "secure_element",
        1072 => "network_stack",
        2000 => "shell",
        9999 => "nobody",
        _ => return None,
    })
}

/// uid → 包名（多个包共用 uid 时用逗号连接）。
fn packages_by_uid() -> BTreeMap<u32, String> {
    let mut m: BTreeMap<u32, String> = BTreeMap::new();
    let Ok(out) = std::process::Command::new("pm").args(["list", "packages", "-U"]).output() else { return m };
    for line in String::from_utf8_lossy(&out.stdout).lines() {
        // package:com.example uid:10068
        let Some(rest) = line.strip_prefix("package:") else { continue };
        let Some((pkg, uid)) = rest.split_once(" uid:") else { continue };
        for u in uid.split(',') {
            if let Ok(u) = u.trim().parse::<u32>() {
                m.entry(u).and_modify(|s| { s.push(','); s.push_str(pkg) }).or_insert_with(|| pkg.to_string());
            }
        }
    }
    m
}

pub fn main(raw: &[String]) -> Result<()> {
    let a = Args::parse(raw, &[])?;
    a.reject_unknown(&["json", "all"])?;
    let mut socks = vec![];
    for (file, proto) in [("tcp", "tcp"), ("tcp6", "tcp6"), ("udp", "udp"), ("udp6", "udp6")] {
        if let Ok(t) = fs::read_to_string(format!("/proc/net/{file}")) {
            socks.extend(parse_table(&t, proto));
        }
    }
    socks.retain(|s| a.flag("all") || s.state == "LISTEN" || s.state == "BOUND");
    socks.sort_by_key(|s| (s.port, s.proto));
    socks.dedup_by(|x, y| x.port == y.port && x.proto == y.proto && x.addr == y.addr);
    let pkgs = packages_by_uid();
    // 系统 uid（< 10000）由数十个系统包共用（如 1000 为 android.uid.system），只显示固定名称；
    // 应用 uid 被多个包共用时只列前两个并注明总数。
    let owner = |uid: u32| -> String {
        let app = uid % 100000;
        if app < 10000 {
            return system_uid(app).map(|s| s.to_string()).unwrap_or_else(|| format!("uid {uid}"));
        }
        match pkgs.get(&uid) {
            Some(p) => {
                let list: Vec<&str> = p.split(',').collect();
                if list.len() > 2 { format!("{}, {} 等 {} 个包", list[0], list[1], list.len()) } else { p.clone() }
            }
            None => format!("uid {uid}"),
        }
    };
    if a.flag("json") {
        let items = socks.iter().map(|s| {
            Json::obj().str("proto", s.proto).str("addr", &s.addr).num("port", s.port).num("uid", s.uid).str("state", s.state).str("owner", &owner(s.uid)).end()
        });
        println!("{}", Json::obj().raw("sockets", &array(items)).end());
        return Ok(());
    }
    println!("{:<6} {:<40} {:>6}  {:<11} {:>6}  所属", "协议", "地址", "端口", "状态", "uid");
    for s in &socks {
        println!("{:<6} {:<40} {:>6}  {:<11} {:>6}  {}", s.proto, s.addr, s.port, s.state, s.uid, owner(s.uid));
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parse_proc_net() {
        // 192.168.0.104:8090 LISTEN uid 10639；IPv6 任意地址 :::5555 uid 2000
        let t4 = "  sl  local_address rem_address   st tx_queue rx_queue tr tm->when retrnsmt   uid  timeout inode\n   0: 6800A8C0:1F9A 00000000:0000 0A 00000000:00000000 00:00000000 00000000 10639        0 1 1\n";
        let s = parse_table(t4, "tcp");
        assert_eq!((s[0].addr.as_str(), s[0].port, s[0].uid, s[0].state), ("192.168.0.104", 8090, 10639, "LISTEN"));
        let t6 = "hdr\n   0: 00000000000000000000000000000000:15B3 00000000000000000000000000000000:0000 0A 00000000:00000000 00:00000000 00000000  2000        0 1 1\n";
        let s = parse_table(t6, "tcp6");
        assert_eq!((s[0].addr.as_str(), s[0].port, s[0].uid), ("::", 5555, 2000));
        let mapped = "hdr\n   0: 0000000000000000FFFF00006D00A8C0:1F9A 00000000000000000000000000000000:0000 0A 0 0 0 2000 0 1 1\n";
        assert_eq!(parse_table(mapped, "tcp6")[0].addr, "192.168.0.109");
    }
}
