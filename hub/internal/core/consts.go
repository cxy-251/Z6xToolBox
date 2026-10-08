package core

import "time"

// hub 核心的内部常量：技术细节，不在配置文件中开放。用户可以调整的参数在 Config 中（hub.yaml）。
const (
	// gateCheckInterval：多久检查一次网络（是否仍在可信 Wi-Fi、监听是否被系统作废）
	gateCheckInterval = 15 * time.Second
	// shutdownTimeout：停止 hub 时等待正在处理的请求完成的最长时间
	shutdownTimeout = 5 * time.Second
	// loginFailDelay：密码错误后延迟响应，减缓暴力尝试
	loginFailDelay = time.Second
	// stopDelay：网页上点「停止 hub」后，先返回页面再退出
	stopDelay = 500 * time.Millisecond
	// defaultLoginDays：浏览器登录后保持多少天（配置项 login_days 的默认值）
	defaultLoginDays = 180
)
