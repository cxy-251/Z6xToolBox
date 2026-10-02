//! 极简的命令行解析与输出约定（不引入 clap 等依赖，减少编译时间和体积）。
//!
//! 约定（见「规格：z6x-tools」）：
//!   - 每个子命令支持 `--json`，输出一个 JSON 对象（逐行事件的命令每行一个对象）；
//!   - 退出码：0 成功，1 一般错误，2 参数错误，3 权限不足；错误信息写入 stderr。

use std::collections::HashMap;
use std::fmt::Write as _;

pub const EXIT_ERROR: i32 = 1;
pub const EXIT_USAGE: i32 = 2;
pub const EXIT_PERMISSION: i32 = 3;

/// 命令执行失败：附带退出码与说明。
pub struct Fail {
    pub code: i32,
    pub msg: String,
}

impl Fail {
    pub fn usage(msg: impl Into<String>) -> Fail {
        Fail { code: EXIT_USAGE, msg: msg.into() }
    }
    pub fn error(msg: impl Into<String>) -> Fail {
        Fail { code: EXIT_ERROR, msg: msg.into() }
    }
    /// 按 io 错误的种类给出退出码：无权限为 3，其余为 1。
    pub fn io(what: &str, e: std::io::Error) -> Fail {
        let code = if e.kind() == std::io::ErrorKind::PermissionDenied { EXIT_PERMISSION } else { EXIT_ERROR };
        Fail { code, msg: format!("{what}：{e}") }
    }
}

pub type Result<T> = std::result::Result<T, Fail>;

/// 解析后的参数：`--名称 值`、`--开关`、位置参数，以及 `--` 之后的原样参数。
pub struct Args {
    opts: HashMap<String, String>,
    flags: Vec<String>,
    pub positional: Vec<String>,
    pub rest: Vec<String>,
}

impl Args {
    /// `with_value` 列出需要取值的选项名（不含 `--`），其余 `--xxx` 都视为开关。
    pub fn parse(raw: &[String], with_value: &[&str]) -> Result<Args> {
        let mut a = Args { opts: HashMap::new(), flags: vec![], positional: vec![], rest: vec![] };
        let mut i = 0;
        while i < raw.len() {
            let s = &raw[i];
            if s == "--" {
                a.rest = raw[i + 1..].to_vec();
                break;
            }
            if let Some(name) = s.strip_prefix("--") {
                if let Some((k, v)) = name.split_once('=') {
                    a.opts.insert(k.to_string(), v.to_string());
                } else if with_value.contains(&name) {
                    i += 1;
                    let v = raw.get(i).ok_or_else(|| Fail::usage(format!("--{name} 需要一个值")))?;
                    a.opts.insert(name.to_string(), v.clone());
                } else {
                    a.flags.push(name.to_string());
                }
            } else {
                a.positional.push(s.clone());
            }
            i += 1;
        }
        Ok(a)
    }

    pub fn flag(&self, name: &str) -> bool {
        self.flags.iter().any(|f| f == name)
    }

    pub fn opt(&self, name: &str) -> Option<&str> {
        self.opts.get(name).map(|s| s.as_str())
    }

    /// 取数值选项；没有给出时返回默认值，格式错误时报参数错误。
    pub fn num<T: std::str::FromStr>(&self, name: &str, default: T) -> Result<T> {
        match self.opt(name) {
            None => Ok(default),
            Some(v) => v.parse().map_err(|_| Fail::usage(format!("--{name} 的值不是有效数字：{v}"))),
        }
    }

    /// 检查是否有未识别的开关，避免拼错的参数被悄悄忽略。
    pub fn reject_unknown(&self, known_flags: &[&str]) -> Result<()> {
        for f in &self.flags {
            if !known_flags.contains(&f.as_str()) {
                return Err(Fail::usage(format!("未知参数：--{f}")));
            }
        }
        Ok(())
    }
}

/// 解析时长：`500ms`、`2s`、`1.5s`，纯数字按毫秒。
pub fn parse_duration_ms(s: &str) -> Result<u64> {
    let bad = || Fail::usage(format!("时长格式不正确：{s}（例如 50ms、2s）"));
    if let Some(v) = s.strip_suffix("ms") {
        return v.trim().parse::<u64>().map_err(|_| bad());
    }
    if let Some(v) = s.strip_suffix('s') {
        return v.trim().parse::<f64>().map(|x| (x * 1000.0) as u64).map_err(|_| bad());
    }
    s.parse::<u64>().map_err(|_| bad())
}

// ---------------- JSON 输出 ----------------

/// 极简的 JSON 构造器：只需要写出对象、数组、字符串和数字，不需要解析。
pub struct Json(String);

impl Json {
    pub fn obj() -> Json {
        Json("{".into())
    }
    fn sep(&mut self) {
        if !self.0.ends_with('{') && !self.0.ends_with('[') {
            self.0.push(',');
        }
    }
    fn key(&mut self, k: &str) {
        self.sep();
        push_str(&mut self.0, k);
        self.0.push(':');
    }
    pub fn str(mut self, k: &str, v: &str) -> Json {
        self.key(k);
        push_str(&mut self.0, v);
        self
    }
    pub fn num(mut self, k: &str, v: impl std::fmt::Display) -> Json {
        self.key(k);
        let _ = write!(self.0, "{v}");
        self
    }
    /// 浮点数保留两位小数；NaN、无穷写成 null。
    pub fn f2(mut self, k: &str, v: f64) -> Json {
        self.key(k);
        if v.is_finite() {
            let _ = write!(self.0, "{v:.2}");
        } else {
            self.0.push_str("null");
        }
        self
    }
    pub fn bool(mut self, k: &str, v: bool) -> Json {
        self.key(k);
        self.0.push_str(if v { "true" } else { "false" });
        self
    }
    pub fn raw(mut self, k: &str, json: &str) -> Json {
        self.key(k);
        self.0.push_str(json);
        self
    }
    pub fn end(mut self) -> String {
        self.0.push('}');
        self.0
    }
}

/// 把若干已生成的 JSON 片段拼成数组。
pub fn array(items: impl IntoIterator<Item = String>) -> String {
    let v: Vec<String> = items.into_iter().collect();
    format!("[{}]", v.join(","))
}

pub fn quote(s: &str) -> String {
    let mut out = String::new();
    push_str(&mut out, s);
    out
}

fn push_str(out: &mut String, s: &str) {
    out.push('"');
    for c in s.chars() {
        match c {
            '"' => out.push_str("\\\""),
            '\\' => out.push_str("\\\\"),
            '\n' => out.push_str("\\n"),
            '\r' => out.push_str("\\r"),
            '\t' => out.push_str("\\t"),
            c if (c as u32) < 0x20 => {
                let _ = write!(out, "\\u{:04x}", c as u32);
            }
            c => out.push(c),
        }
    }
    out.push('"');
}

/// 字节数换算为便于阅读的形式。
pub fn human_bytes(n: u64) -> String {
    const U: [&str; 5] = ["B", "KB", "MB", "GB", "TB"];
    let mut v = n as f64;
    let mut i = 0;
    while v >= 1024.0 && i < U.len() - 1 {
        v /= 1024.0;
        i += 1;
    }
    if i == 0 { format!("{n} B") } else { format!("{v:.1} {}", U[i]) }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parse_args() {
        let raw: Vec<String> = ["home", "--repeat", "3", "--json", "--interval=50ms", "--", "x", "--y"].iter().map(|s| s.to_string()).collect();
        let a = Args::parse(&raw, &["repeat", "interval"]).ok().unwrap();
        assert_eq!(a.positional, vec!["home"]);
        assert_eq!(a.num("repeat", 1u32).ok(), Some(3));
        assert!(a.flag("json"));
        assert_eq!(a.opt("interval"), Some("50ms"));
        assert_eq!(a.rest, vec!["x", "--y"]);
        assert!(a.reject_unknown(&["json"]).is_ok());
        assert!(a.reject_unknown(&[]).is_err());
    }

    #[test]
    fn durations() {
        assert_eq!(parse_duration_ms("50ms").ok(), Some(50));
        assert_eq!(parse_duration_ms("1.5s").ok(), Some(1500));
        assert_eq!(parse_duration_ms("20").ok(), Some(20));
        assert!(parse_duration_ms("abc").is_err());
    }

    #[test]
    fn json_escape() {
        let j = Json::obj().str("a", "x\"y\n").num("n", 3).f2("f", 1.234).bool("b", true).end();
        assert_eq!(j, r#"{"a":"x\"y\n","n":3,"f":1.23,"b":true}"#);
    }
}
