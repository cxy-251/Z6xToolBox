//! z6x ping：ICMP 延迟、抖动与丢包。
//!
//! 使用普通身份可用的 ICMP 数据报套接字（socket(AF_INET, SOCK_DGRAM, IPPROTO_ICMP)），
//! 需要 /proc/sys/net/ipv4/ping_group_range 包含当前组（投影仪与手机均为 0～2147483647，已实测）。
//! 这类套接字由内核填写标识符，回复也由内核按标识符分发，因此只需核对序号。

use crate::cli::{parse_duration_ms, Args, Fail, Json, Result};
use std::net::Ipv4Addr;
use std::time::{Duration, Instant};

fn checksum(b: &[u8]) -> u16 {
    let mut sum = 0u32;
    for c in b.chunks(2) {
        sum += u16::from_be_bytes([c[0], *c.get(1).unwrap_or(&0)]) as u32;
    }
    while sum >> 16 != 0 {
        sum = (sum & 0xffff) + (sum >> 16);
    }
    !(sum as u16)
}

pub struct Stats {
    pub sent: u32,
    pub received: u32,
    pub rtts: Vec<f64>,
}

impl Stats {
    pub fn summary(&self) -> (f64, f64, f64, f64, f64) {
        let loss = if self.sent == 0 { 0.0 } else { (self.sent - self.received) as f64 / self.sent as f64 * 100.0 };
        if self.rtts.is_empty() {
            return (f64::NAN, f64::NAN, f64::NAN, f64::NAN, loss);
        }
        let n = self.rtts.len() as f64;
        let min = self.rtts.iter().cloned().fold(f64::INFINITY, f64::min);
        let max = self.rtts.iter().cloned().fold(0.0, f64::max);
        let avg = self.rtts.iter().sum::<f64>() / n;
        let mdev = (self.rtts.iter().map(|r| (r - avg).powi(2)).sum::<f64>() / n).sqrt();
        (min, avg, max, mdev, loss)
    }
}

pub fn main(raw: &[String]) -> Result<()> {
    let a = Args::parse(raw, &["count", "interval", "timeout"])?;
    a.reject_unknown(&["json"])?;
    let target = a.positional.first().ok_or_else(|| Fail::usage("需要目标 IP，例如 z6x ping 192.168.0.1"))?;
    let ip: Ipv4Addr = target.parse().map_err(|_| Fail::usage(format!("只接受 IPv4 地址（静态程序在安卓上无法解析域名）：{target}")))?;
    let count: u32 = a.num("count", 10)?;
    let interval = Duration::from_millis(parse_duration_ms(a.opt("interval").unwrap_or("200ms"))?);
    let timeout = Duration::from_millis(parse_duration_ms(a.opt("timeout").unwrap_or("1s"))?);
    let fd = unsafe { libc::socket(libc::AF_INET, libc::SOCK_DGRAM | libc::SOCK_CLOEXEC, libc::IPPROTO_ICMP) };
    if fd < 0 {
        return Err(Fail::io("创建 ICMP 套接字（检查 ping_group_range）", std::io::Error::last_os_error()));
    }
    let addr = libc::sockaddr_in { sin_family: libc::AF_INET as u16, sin_port: 0, sin_addr: libc::in_addr { s_addr: u32::from(ip).to_be() }, sin_zero: [0; 8] };
    let json = a.flag("json");
    let mut st = Stats { sent: 0, received: 0, rtts: vec![] };
    let mut buf = [0u8; 1500];
    for seq in 1..=count as u16 {
        if seq > 1 {
            std::thread::sleep(interval);
        }
        // ICMP 回显请求：类型 8、代码 0、校验和、标识符（内核填写）、序号、56 字节数据
        let mut pkt = vec![8u8, 0, 0, 0, 0, 0];
        pkt.extend_from_slice(&seq.to_be_bytes());
        pkt.extend((0..56u8).map(|i| i));
        let c = checksum(&pkt);
        pkt[2..4].copy_from_slice(&c.to_be_bytes());
        let start = Instant::now();
        let r = unsafe { libc::sendto(fd, pkt.as_ptr() as *const libc::c_void, pkt.len(), 0, &addr as *const _ as *const libc::sockaddr, std::mem::size_of::<libc::sockaddr_in>() as u32) };
        if r < 0 {
            return Err(Fail::io("发送", std::io::Error::last_os_error()));
        }
        st.sent += 1;
        let mut got = None;
        while start.elapsed() < timeout {
            let left = timeout.saturating_sub(start.elapsed());
            let mut pfd = libc::pollfd { fd, events: libc::POLLIN, revents: 0 };
            if unsafe { libc::poll(&mut pfd, 1, left.as_millis().max(1) as i32) } <= 0 {
                break;
            }
            let n = unsafe { libc::recv(fd, buf.as_mut_ptr() as *mut libc::c_void, buf.len(), 0) };
            // 回复：类型 0（回显应答），序号相同（来自之前超时的旧回复则忽略）
            if n >= 8 && buf[0] == 0 && u16::from_be_bytes([buf[6], buf[7]]) == seq {
                got = Some(start.elapsed().as_secs_f64() * 1000.0);
                break;
            }
        }
        match got {
            Some(ms) => {
                st.received += 1;
                st.rtts.push(ms);
                if !json {
                    println!("来自 {ip}：序号 {seq} 时间 {ms:.2} ms");
                }
            }
            None if !json => println!("序号 {seq} 超时"),
            None => {}
        }
    }
    unsafe { libc::close(fd) };
    let (min, avg, max, mdev, loss) = st.summary();
    if json {
        println!("{}", Json::obj().str("target", &ip.to_string()).num("sent", st.sent).num("received", st.received).f2("loss_percent", loss)
            .f2("min_ms", min).f2("avg_ms", avg).f2("max_ms", max).f2("mdev_ms", mdev).end());
    } else {
        println!("发送 {}，收到 {}，丢包 {loss:.1}%；延迟 最小/平均/最大/抖动 = {min:.2}/{avg:.2}/{max:.2}/{mdev:.2} ms", st.sent, st.received);
    }
    if st.received == 0 {
        return Err(Fail::error("全部超时"));
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn icmp_checksum_and_stats() {
        // RFC 1071 示例数据的校验和
        assert_eq!(checksum(&[0x00, 0x01, 0xf2, 0x03, 0xf4, 0xf5, 0xf6, 0xf7]), !0xddf2u16);
        let s = Stats { sent: 4, received: 3, rtts: vec![1.0, 2.0, 3.0] };
        let (min, avg, max, mdev, loss) = s.summary();
        assert_eq!((min, avg, max), (1.0, 2.0, 3.0));
        assert!((mdev - 0.8165).abs() < 0.001);
        assert_eq!(loss, 25.0);
    }
}
