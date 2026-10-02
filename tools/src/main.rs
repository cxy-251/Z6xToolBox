//! z6x：运行在极米 Z6X Pro（及安卓手机）上的命令集，形式与 BusyBox 相同：
//! 一个静态程序，以子命令区分功能，执行完毕即退出（keymap 守护、watch、run 除外）。
//! 也可以像 BusyBox 一样通过链接调用：程序名为 `z6x-ports` 时等同于 `z6x ports`。
//!
//! 规格见工具箱「提案 → 规格 → 规格：z6x-tools」。

mod cli;
mod hash;
mod iobench;
mod key;
mod keymap;
mod ping;
mod ports;
mod run;
mod sys;
mod watch;

use cli::Fail;

const HELP: &str = "z6x —— 极米 Z6X Pro 命令集

用法：z6x <子命令> [参数]      每个子命令都支持 --json 与 --help

  sys       系统指标：CPU、内存、温度、存储、运行时长（--watch 秒 持续刷新）
  ports     监听端口及其所属的 uid 与应用
  key       注入按键：z6x key home、z6x key volup --repeat 5（--list 列出键名）
  keymap    遥控器按键重映射守护：长按、双击触发动作（--daemon --config 文件）
  hash      查找重复文件，只报告不删除：z6x hash <目录>…
  watch     监听目录中写入完成的文件，每行输出一个事件（--exec 对每个事件执行命令）
  ping      ICMP 延迟、抖动与丢包：z6x ping <IP> [--count 10]
  iobench   存储读写测速：z6x iobench <目录> [--size 256]
  run       进程守护：崩溃后按退避间隔重启：z6x run [--name 名称] -- <命令> [参数]

退出码：0 成功，1 一般错误，2 参数错误，3 权限不足。";

fn main() {
    let mut args: Vec<String> = std::env::args().collect();
    let prog = std::path::Path::new(&args[0]).file_name().and_then(|s| s.to_str()).unwrap_or("z6x").to_string();
    // 以 z6x-<子命令> 的名字调用时，等同于 z6x <子命令>
    let sub = if let Some(s) = prog.strip_prefix("z6x-") {
        s.to_string()
    } else if args.len() > 1 {
        args.remove(1)
    } else {
        println!("{HELP}");
        return;
    };
    let rest = &args[1..];
    if rest.iter().any(|a| a == "--help" || a == "-h") {
        println!("{}", help_for(&sub));
        return;
    }
    let res = match sub.as_str() {
        "sys" => sys::main(rest),
        "ports" => ports::main(rest),
        "key" => key::main(rest),
        "keymap" => keymap::main(rest),
        "hash" => hash::main(rest),
        "watch" => watch::main(rest),
        "ping" => ping::main(rest),
        "iobench" => iobench::main(rest),
        "run" => run::main(rest),
        "help" | "--help" | "-h" => {
            println!("{HELP}");
            Ok(())
        }
        "version" | "--version" => {
            println!("z6x {}", env!("CARGO_PKG_VERSION"));
            Ok(())
        }
        other => Err(Fail::usage(format!("未知子命令：{other}（z6x --help 列出全部子命令）"))),
    };
    if let Err(f) = res {
        eprintln!("z6x {sub}：{}", f.msg);
        std::process::exit(f.code);
    }
}

fn help_for(sub: &str) -> &'static str {
    match sub {
        "sys" => "z6x sys [--json] [--watch 秒]\n  一次性输出 CPU 使用率（间隔 500ms 采样两次 /proc/stat）、内存、各温区温度、/data 剩余空间、运行时长。\n  --watch N：每 N 秒刷新一次（--json 时每次输出一行）。",
        "ports" => "z6x ports [--json] [--all]\n  列出监听中的 TCP 端口与绑定的 UDP 端口，以及所属 uid、应用包名。\n  包名通过 pm list packages -U 获得（/data/system/packages.list 对 shell 不可读）。--all 同时列出已建立的连接。",
        "key" => key::HELP,
        "keymap" => keymap::HELP,
        "hash" => "z6x hash <目录>… [--json] [--min 字节]\n  查找重复文件：先按大小分组，再比较首尾 64KB，最后计算整个文件的 xxHash64。\n  只报告重复组和可节省的空间，不删除任何文件。--min 忽略小于该大小的文件（默认 1）。",
        "watch" => "z6x watch <目录>… [--exec 命令] [--json]\n  用 inotify 监听目录（含子目录），文件写入完成（CLOSE_WRITE）或移入（MOVED_TO）时输出一行。\n  --exec：对每个事件执行命令，文件路径通过环境变量 Z6X_PATH 传入。\n  注意：系统的 max_user_watches 可能只有 8192 个目录。",
        "ping" => "z6x ping <IP> [--count 10] [--interval 200ms] [--timeout 1s] [--json]\n  使用普通身份的 ICMP 套接字，输出最小、平均、最大延迟、抖动（mdev）与丢包率。\n  只接受 IP 地址：静态程序在安卓上无法解析域名（安卓没有 /etc/resolv.conf）。",
        "iobench" => "z6x iobench <目录> [--size MB] [--seconds 秒] [--json]\n  在目录中写入测试文件，测顺序写、顺序读与 4KB 随机读，测完删除测试文件。\n  文件系统不支持 O_DIRECT 时（如 FAT32、exFAT）自动改为普通读写，并在结果中注明。",
        "run" => run::HELP,
        _ => HELP,
    }
}
