// Package core 是 z6x-hub 的框架部分：配置、模块注册、HTTP 服务、鉴权、日志。
package core

import (
	"errors"
	"fmt"
	"os"
	"strings"

	"gopkg.in/yaml.v3"
)

// Config 对应 hub.yaml。各模块自己的配置原样保存在 Modules 里，由模块自行解析。
type Config struct {
	// Name 是设备的显示名称（如「投影仪」「手机」），用于页面标题和资源库名称。
	Name   string `yaml:"name"`
	Listen string `yaml:"listen"`
	Token  string `yaml:"token"`
	// Password 是浏览器登录用的简单密码（可选）。登录后浏览器保存的仍是 token；接口与网络指纹只认 token。
	Password string               `yaml:"password"`
	DataDir  string               `yaml:"data_dir"`
	LogFile  string               `yaml:"log_file"`
	Modules  map[string]yaml.Node `yaml:"modules"`
	// TrustLocal 为真时，来自设备本身的请求（例如投影仪上的浏览器）不需要 token；保存配置除外。
	TrustLocal bool `yaml:"trust_local"`
	// LoginDays：浏览器登录后保持多少天，默认 180。
	LoginDays int `yaml:"login_days"`
	// Network 限定在哪个网络上对外服务，见 netguard.go。
	Network NetworkConfig `yaml:"network"`
}

// ModuleConfig 是每个模块配置里共有的字段。
type ModuleConfig struct {
	Enable bool `yaml:"enable"`
	// Port 不为 0 时，该模块需要单独监听这个端口（例如 WebDAV）。
	Port int `yaml:"port"`
}

// LoadConfig 读取并校验配置文件。校验不通过就返回错误，hub 拒绝启动，不带病运行。
func LoadConfig(path string) (*Config, error) {
	raw, err := os.ReadFile(path)
	if err != nil {
		return nil, fmt.Errorf("读取配置失败：%w", err)
	}
	var c Config
	if err := yaml.Unmarshal(raw, &c); err != nil {
		return nil, fmt.Errorf("配置格式错误：%w", err)
	}
	if c.Name == "" {
		c.Name = "设备"
	}
	if c.Listen == "" {
		c.Listen = ":8090"
	}
	if c.DataDir == "" {
		c.DataDir = "data"
	}
	if c.LogFile == "" {
		c.LogFile = "hub.log"
	}
	return &c, c.validate()
}

func (c *Config) validate() error {
	var errs []string
	if len(c.Token) < 16 {
		errs = append(errs, "token 至少 16 个字符（所有接口都靠它鉴权）")
	}
	if c.Password != "" && len(c.Password) < 4 {
		errs = append(errs, "password 至少 4 个字符")
	}
	if strings.Contains(c.Token, "换成") {
		errs = append(errs, "token 仍是示例值，请换成随机字符串")
	}
	mainPort := portOf(c.Listen)
	if mainPort > 0 && mainPort < 1024 {
		errs = append(errs, fmt.Sprintf("listen 端口 %d 低于 1024，shell 身份无法绑定", mainPort))
	}
	used := map[int]string{mainPort: "listen"}
	for name, node := range c.Modules {
		var mc ModuleConfig
		if err := node.Decode(&mc); err != nil {
			errs = append(errs, fmt.Sprintf("模块 %s 的配置格式错误：%v", name, err))
			continue
		}
		if !mc.Enable || mc.Port == 0 {
			continue
		}
		if mc.Port < 1024 {
			errs = append(errs, fmt.Sprintf("模块 %s 的端口 %d 低于 1024，shell 身份无法绑定", name, mc.Port))
		}
		if other, ok := used[mc.Port]; ok {
			errs = append(errs, fmt.Sprintf("端口 %d 冲突：%s 与 %s", mc.Port, other, name))
		}
		used[mc.Port] = name
	}
	if len(c.Network.Trusted) > 0 && c.Network.Iface == "" {
		errs = append(errs, "network.trusted 需要同时配置 network.iface（例如 wlan0）")
	}
	if len(errs) > 0 {
		return errors.New("配置校验失败：\n  - " + strings.Join(errs, "\n  - "))
	}
	return nil
}

// portOf 从 ":8090"、"0.0.0.0:8090" 这类地址中取出端口号，取不到时返回 0。
func portOf(addr string) int {
	i := strings.LastIndex(addr, ":")
	if i < 0 {
		return 0
	}
	var p int
	fmt.Sscanf(addr[i+1:], "%d", &p)
	return p
}

// Enabled 返回某个模块是否在配置中启用，以及它的通用配置。
func (c *Config) Enabled(name string) (ModuleConfig, bool) {
	node, ok := c.Modules[name]
	if !ok {
		return ModuleConfig{}, false
	}
	var mc ModuleConfig
	if node.Decode(&mc) != nil {
		return ModuleConfig{}, false
	}
	return mc, mc.Enable
}

// Decode 把某个模块的完整配置解析到 v 中。
func (c *Config) Decode(name string, v any) error {
	node, ok := c.Modules[name]
	if !ok {
		return nil
	}
	return node.Decode(v)
}
