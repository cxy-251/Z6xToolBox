//! z6x http：极简的 HTTP 客户端，供 keymap 的动作调用本机 hub 的接口（例如一键清理后台）。
//! 只支持 http://IP:端口/路径，不支持 https 与域名（静态程序在安卓上无法解析域名）。

use crate::cli::{Args, Fail, Result};
use std::io::{Read, Write};
use std::net::{SocketAddr, TcpStream};
use std::time::Duration;

pub fn main(raw: &[String]) -> Result<()> {
    let a = Args::parse(raw, &[])?;
    a.reject_unknown(&[])?;
    let (method, url) = match a.positional.as_slice() {
        [m, u] => (m.to_uppercase(), u.clone()),
        [u] => ("GET".to_string(), u.clone()),
        _ => return Err(Fail::usage("用法：z6x http [GET|POST] http://IP:端口/路径")),
    };
    let rest = url.strip_prefix("http://").ok_or_else(|| Fail::usage("只支持 http://"))?;
    let (host, path) = match rest.find('/') {
        Some(i) => (&rest[..i], &rest[i..]),
        None => (rest, "/"),
    };
    let addr: SocketAddr = host.parse().map_err(|_| Fail::usage(format!("地址应为 IP:端口：{host}")))?;
    let mut s = TcpStream::connect_timeout(&addr, Duration::from_secs(5)).map_err(|e| Fail::io("连接", e))?;
    s.set_read_timeout(Some(Duration::from_secs(30))).ok();
    write!(s, "{method} {path} HTTP/1.0\r\nHost: {host}\r\nContent-Length: 0\r\n\r\n").map_err(|e| Fail::io("发送", e))?;
    let mut resp = String::new();
    s.read_to_string(&mut resp).map_err(|e| Fail::io("读取", e))?;
    let status = resp.split_whitespace().nth(1).unwrap_or("?").to_string();
    let body = resp.split_once("\r\n\r\n").map(|x| x.1).unwrap_or("");
    println!("{body}");
    if !status.starts_with('2') {
        return Err(Fail::error(format!("HTTP {status}")));
    }
    Ok(())
}
