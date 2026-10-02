//! z6x sys：系统指标。数据全部来自 /proc 与 /sys，不调用外部程序。

use crate::cli::{array, human_bytes, Args, Fail, Json, Result};
use std::fs;
use std::thread::sleep;
use std::time::Duration;

/// /proc/stat 第一行的累计时间：(总时间, 空闲时间)，单位为时钟周期。
fn cpu_times(stat: &str) -> Option<(u64, u64)> {
    let line = stat.lines().next()?;
    let v: Vec<u64> = line.split_whitespace().skip(1).filter_map(|x| x.parse().ok()).collect();
    if v.len() < 4 {
        return None;
    }
    let idle = v[3] + v.get(4).copied().unwrap_or(0); // idle + iowait
    Some((v.iter().sum(), idle))
}

/// 两次采样之间的 CPU 使用率（百分比）。读不到 /proc/stat 时（普通应用身份）返回 None。
fn cpu_percent(interval: Duration) -> Option<f64> {
    let a = cpu_times(&fs::read_to_string("/proc/stat").ok()?)?;
    sleep(interval);
    let b = cpu_times(&fs::read_to_string("/proc/stat").ok()?)?;
    let total = b.0.saturating_sub(a.0) as f64;
    let idle = b.1.saturating_sub(a.1) as f64;
    if total <= 0.0 { None } else { Some((total - idle) / total * 100.0) }
}

fn meminfo(text: &str, key: &str) -> u64 {
    text.lines()
        .find(|l| l.starts_with(key) && l[key.len()..].starts_with(':'))
        .and_then(|l| l.split_whitespace().nth(1))
        .and_then(|v| v.parse().ok())
        .unwrap_or(0)
}

/// 各温区：(名称, 摄氏度)。-40～150 ℃ 以外的读数视为无效（手机上的 bcl-warn 读数为 -273）。
fn thermal() -> Vec<(String, f64)> {
    let mut out = vec![];
    let Ok(dir) = fs::read_dir("/sys/class/thermal") else { return out };
    let mut zones: Vec<_> = dir.flatten().filter(|e| e.file_name().to_string_lossy().starts_with("thermal_zone")).collect();
    zones.sort_by_key(|e| e.file_name().to_string_lossy().trim_start_matches("thermal_zone").parse::<u32>().unwrap_or(0));
    for z in zones {
        let p = z.path();
        let (Ok(t), Ok(v)) = (fs::read_to_string(p.join("type")), fs::read_to_string(p.join("temp"))) else { continue };
        if let Ok(milli) = v.trim().parse::<f64>() {
            let c = milli / 1000.0;
            if c > -40.0 && c < 150.0 {
                out.push((t.trim().to_string(), c));
            }
        }
    }
    out
}

/// 某个挂载点的 (总字节, 可用字节)。
pub fn statfs(path: &str) -> Option<(u64, u64)> {
    let c = std::ffi::CString::new(path).ok()?;
    let mut s: libc::statfs = unsafe { std::mem::zeroed() };
    if unsafe { libc::statfs(c.as_ptr(), &mut s) } != 0 {
        return None;
    }
    let bs = s.f_bsize as u64;
    Some((s.f_blocks as u64 * bs, s.f_bavail as u64 * bs))
}

fn snapshot(json: bool) -> String {
    let cpu = cpu_percent(Duration::from_millis(500));
    let mem = fs::read_to_string("/proc/meminfo").unwrap_or_default();
    let (total, avail) = (meminfo(&mem, "MemTotal"), meminfo(&mem, "MemAvailable"));
    let temps = thermal();
    let data = statfs("/data");
    let uptime = fs::read_to_string("/proc/uptime").ok().and_then(|s| s.split_whitespace().next()?.parse::<f64>().ok()).unwrap_or(0.0);
    if json {
        let t = array(temps.iter().map(|(n, c)| Json::obj().str("type", n).f2("celsius", *c).end()));
        let mut j = Json::obj();
        j = match cpu {
            Some(c) => j.f2("cpu_percent", c),
            None => j.raw("cpu_percent", "null"),
        };
        j = j.num("mem_total_kb", total).num("mem_available_kb", avail).raw("thermal", &t);
        if let Some((tot, free)) = data {
            j = j.num("data_total_bytes", tot).num("data_free_bytes", free);
        }
        return j.num("uptime_sec", uptime as u64).end();
    }
    let mut s = String::new();
    s += &format!("CPU      {}\n", cpu.map(|c| format!("{c:.1}%")).unwrap_or_else(|| "无法读取（/proc/stat 不可读）".into()));
    s += &format!("内存     可用 {} / 共 {}\n", human_bytes(avail * 1024), human_bytes(total * 1024));
    if let Some((tot, free)) = data {
        s += &format!("/data    剩余 {} / 共 {}\n", human_bytes(free), human_bytes(tot));
    }
    let up = uptime as u64;
    s += &format!("运行时长 {} 天 {} 小时 {} 分\n", up / 86400, up % 86400 / 3600, up % 3600 / 60);
    s += &format!("温度（{} 个温区）\n", temps.len());
    for (n, c) in &temps {
        s += &format!("  {n:<24} {c:>6.1} ℃\n");
    }
    s
}

pub fn main(raw: &[String]) -> Result<()> {
    let a = Args::parse(raw, &["watch"])?;
    a.reject_unknown(&["json"])?;
    let json = a.flag("json");
    let every: u64 = a.num("watch", 0)?;
    if every == 0 {
        print!("{}", snapshot(json));
        if json {
            println!();
        }
        return Ok(());
    }
    if every < 1 {
        return Err(Fail::usage("--watch 至少为 1 秒"));
    }
    loop {
        let s = snapshot(json);
        if json {
            println!("{s}");
        } else {
            println!("{s}");
        }
        sleep(Duration::from_secs(every).saturating_sub(Duration::from_millis(500)));
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parse_stat_and_meminfo() {
        let (t, i) = cpu_times("cpu  100 0 50 800 50 0 0 0 0 0\ncpu0 1 2 3 4\n").unwrap();
        assert_eq!((t, i), (1000, 850));
        let m = "MemTotal:        3800000 kB\nMemAvailable:    1500000 kB\nMemTotalX: 1 kB\n";
        assert_eq!(meminfo(m, "MemTotal"), 3800000);
        assert_eq!(meminfo(m, "MemAvailable"), 1500000);
    }
}
