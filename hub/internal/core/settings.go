package core

// 设置页（/ui/settings/）：以表单修改 hub.yaml 中的可调参数、管理可信网络，并显示系统状态。
// 参数由核心与各模块声明（Setting），页面按分组生成表单；保存时直接修改配置文件中对应的项（保留注释），
// 校验通过后备份旧配置并重启 hub，与「编辑配置」页相同。

import (
	"bytes"
	_ "embed"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"os"
	"slices"
	"strconv"
	"strings"

	"gopkg.in/yaml.v3"
)

// Setting 描述一个可在设置页修改的参数。
type Setting struct {
	Key     string  `json:"key"`   // 在 hub.yaml 中的路径，以点分隔，如 login_days、modules.library.slide_seconds
	Label   string  `json:"label"` // 显示名称
	Help    string  `json:"help,omitempty"`
	Type    string  `json:"type"`           // number、text 或 password
	Unit    string  `json:"unit,omitempty"` // 单位，如「秒」
	Min     float64 `json:"min,omitempty"`
	Max     float64 `json:"max,omitempty"`
	Step    float64 `json:"step,omitempty"` // 数字的步长，整数为 1
	Default any     `json:"default"`        // 未设置时使用的值
}

// SettingsGroup 是设置页上的一组参数。
type SettingsGroup struct {
	Title string    `json:"title"`
	Items []Setting `json:"items"`
}

// SettingsProvider 由提供可调参数的模块实现。
type SettingsProvider interface {
	Settings() []SettingsGroup
}

//go:embed settings.html
var settingsHTML string

func (h *Hub) coreSettings() []SettingsGroup {
	return []SettingsGroup{{Title: "登录", Items: []Setting{
		{Key: "password", Label: "登录密码", Type: "password", Min: 4,
			Help: "浏览器登录页输入的密码（至少 4 个字符）；留空则只能用 token 登录。接口与网络指纹仍使用 token"},
		{Key: "login_days", Label: "登录保持", Type: "number", Unit: "天", Min: 1, Max: 3650, Step: 1, Default: defaultLoginDays},
	}}}
}

func (h *Hub) allSettings() []SettingsGroup {
	groups := h.coreSettings()
	for _, e := range h.entries {
		if p, ok := e.mod.(SettingsProvider); ok && e.status().State == StateRunning {
			groups = append(groups, p.Settings()...)
		}
	}
	return groups
}

// ---------------- 配置文件节点操作（保留注释） ----------------

func (h *Hub) loadDoc() (*yaml.Node, []byte, error) {
	raw, err := os.ReadFile(h.cfgPath)
	if err != nil {
		return nil, nil, err
	}
	var doc yaml.Node
	if err := yaml.Unmarshal(raw, &doc); err != nil || len(doc.Content) == 0 {
		return nil, nil, fmt.Errorf("配置格式错误：%v", err)
	}
	return &doc, raw, nil
}

func encodeDoc(doc *yaml.Node) (string, error) {
	var buf bytes.Buffer
	enc := yaml.NewEncoder(&buf)
	enc.SetIndent(2)
	if err := enc.Encode(doc); err != nil {
		return "", err
	}
	return buf.String(), nil
}

// lookup 返回路径对应的节点，不存在时为 nil。
func lookup(root *yaml.Node, key string) *yaml.Node {
	n := root
	for _, k := range strings.Split(key, ".") {
		if n == nil || n.Kind != yaml.MappingNode {
			return nil
		}
		var next *yaml.Node
		for i := 0; i+1 < len(n.Content); i += 2 {
			if n.Content[i].Value == k {
				next = n.Content[i+1]
			}
		}
		n = next
	}
	return n
}

// setScalar 设置路径对应的标量值，路径中缺少的层级自动创建；value 为 nil 时删除该项（恢复默认值）。
func setScalar(root *yaml.Node, key string, value *string, tag string) {
	parts := strings.Split(key, ".")
	n := root
	for _, k := range parts[:len(parts)-1] {
		if value == nil && lookup(n, k) == nil {
			return // 删除时路径本来就不存在
		}
		n = mapChild(n, k)
	}
	last := parts[len(parts)-1]
	for i := 0; i+1 < len(n.Content); i += 2 {
		if n.Content[i].Value == last {
			if value == nil {
				n.Content = append(n.Content[:i], n.Content[i+2:]...)
				return
			}
			v := n.Content[i+1]
			v.Kind, v.Tag, v.Value, v.Style = yaml.ScalarNode, tag, *value, 0
			return
		}
	}
	if value != nil {
		n.Content = append(n.Content, &yaml.Node{Kind: yaml.ScalarNode, Tag: "!!str", Value: last},
			&yaml.Node{Kind: yaml.ScalarNode, Tag: tag, Value: *value})
	}
}

// ---------------- 接口 ----------------

type trustedNet struct {
	Fingerprint string `json:"fingerprint"`
	Name        string `json:"name"`
}

func (h *Hub) settingsRoutes(mux *http.ServeMux) {
	mux.Handle("GET /ui/settings/{$}", RequireToken(h.cfg.Token, http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.Header().Set("Content-Type", "text/html; charset=utf-8")
		w.Write([]byte(settingsHTML))
	})))
	mux.Handle("GET /api/settings", RequireToken(h.cfg.Token, http.HandlerFunc(h.getSettings)))
	mux.Handle("POST /api/settings", RequireTokenStrict(h.cfg.Token, http.HandlerFunc(h.saveSettings)))
	mux.Handle("POST /api/network/trust", RequireTokenStrict(h.cfg.Token, http.HandlerFunc(h.trustNetwork)))
	mux.Handle("POST /api/network/forget", RequireTokenStrict(h.cfg.Token, http.HandlerFunc(h.forgetNetwork)))
}

func (h *Hub) getSettings(w http.ResponseWriter, _ *http.Request) {
	out := map[string]any{"editable": h.cfgPath != ""}
	// 参数及其当前值（未设置时 value 为 null，页面显示默认值；密码只返回是否已设置）
	type item struct {
		Setting
		Value any `json:"value"`
	}
	type group struct {
		Title string `json:"title"`
		Items []item `json:"items"`
	}
	var doc *yaml.Node
	if h.cfgPath != "" {
		doc, _, _ = h.loadDoc()
	}
	var groups []group
	for _, g := range h.allSettings() {
		gg := group{Title: g.Title}
		for _, s := range g.Items {
			it := item{Setting: s}
			if doc != nil {
				if n := lookup(doc.Content[0], s.Key); n != nil && n.Kind == yaml.ScalarNode {
					if s.Type == "password" {
						it.Value = n.Value != ""
					} else {
						it.Value = n.Value
					}
				}
			}
			gg.Items = append(gg.Items, it)
		}
		groups = append(groups, gg)
	}
	out["groups"] = groups
	// 网络：当前状态、本网络的指纹、已信任的网络
	bound, reason := h.gate.Status()
	nw := map[string]any{"iface": h.cfg.Network.Iface, "bound": bound, "reason": reason, "loopback": h.cfg.Network.loopback()}
	if h.cfg.Network.Iface != "" {
		if st, err := h.gate.probe(); err == nil {
			nw["ip"], nw["fingerprint"] = st.IP, st.Fingerprint
			nw["current_trusted"] = len(h.cfg.Network.Trusted) == 0 || slices.Contains(h.cfg.Network.Trusted, st.Fingerprint)
		} else {
			nw["error"] = err.Error()
		}
	}
	trusted := []trustedNet{}
	for _, fp := range h.cfg.Network.Trusted {
		trusted = append(trusted, trustedNet{fp, h.cfg.Network.Names[fp]})
	}
	nw["trusted"] = trusted
	out["network"] = nw
	health := h.health()
	out["system"] = map[string]any{"name": h.cfg.Name, "version": health.Version, "uptime_sec": health.UptimeSec, "modules": health.Modules}
	WriteJSON(w, out)
}

// saveSettings：{"values": {"modules.library.slide_seconds": "1.5", "login_days": null, …}}，null 表示恢复默认值。
func (h *Hub) saveSettings(w http.ResponseWriter, r *http.Request) {
	var req struct {
		Values map[string]*string `json:"values"`
	}
	if err := json.NewDecoder(http.MaxBytesReader(w, r.Body, 64<<10)).Decode(&req); err != nil {
		WriteError(w, http.StatusBadRequest, "请求格式错误")
		return
	}
	known := map[string]Setting{}
	for _, g := range h.allSettings() {
		for _, s := range g.Items {
			known[s.Key] = s
		}
	}
	doc, _, err := h.loadDoc()
	if err != nil {
		WriteError(w, http.StatusInternalServerError, err.Error())
		return
	}
	for key, v := range req.Values {
		s, ok := known[key]
		if !ok {
			WriteError(w, http.StatusBadRequest, "未知的参数："+key)
			return
		}
		tag := "!!str"
		if v != nil && s.Type == "number" {
			f, err := strconv.ParseFloat(strings.TrimSpace(*v), 64)
			if err != nil || f < s.Min || (s.Max > 0 && f > s.Max) {
				WriteError(w, http.StatusBadRequest, fmt.Sprintf("「%s」应为 %g～%g 之间的数字", s.Label, s.Min, s.Max))
				return
			}
			val := strconv.FormatFloat(f, 'f', -1, 64)
			v, tag = &val, "!!float"
			if s.Step == 1 || !strings.Contains(val, ".") {
				tag = "!!int"
			}
		}
		if v != nil && s.Type != "number" && s.Min > 0 && *v != "" && float64(len([]rune(*v))) < s.Min {
			WriteError(w, http.StatusBadRequest, fmt.Sprintf("「%s」至少 %g 个字符", s.Label, s.Min))
			return
		}
		if v != nil && s.Type == "password" && *v == "" {
			v = nil // 清空密码即删除该项
		}
		setScalar(doc.Content[0], key, v, tag)
	}
	h.writeDocAndRestart(w, doc)
}

func (h *Hub) writeDocAndRestart(w http.ResponseWriter, doc *yaml.Node) {
	text, err := encodeDoc(doc)
	if err == nil {
		err = h.applyConfig(text)
	}
	if err != nil {
		WriteError(w, http.StatusBadRequest, err.Error())
		return
	}
	WriteJSON(w, map[string]string{"status": "已保存，hub 正在重启"})
	h.restartSoon()
}

// trustNetwork 把当前 Wi-Fi 加入可信网络，并记下名称（network.names）。
func (h *Hub) trustNetwork(w http.ResponseWriter, r *http.Request) {
	var req struct {
		Name string `json:"name"`
	}
	json.NewDecoder(http.MaxBytesReader(w, r.Body, 4<<10)).Decode(&req)
	if h.cfg.Network.Iface == "" {
		WriteError(w, http.StatusBadRequest, "配置中没有 network.iface，hub 监听所有地址，不区分网络")
		return
	}
	st, err := h.gate.probe()
	if err == nil && st.Fingerprint == "" {
		err = errors.New("未连接 Wi-Fi，或读不到路由器标识，请稍后再试")
	}
	if err != nil {
		WriteError(w, http.StatusBadRequest, err.Error())
		return
	}
	doc, _, err := h.loadDoc()
	if err != nil {
		WriteError(w, http.StatusInternalServerError, err.Error())
		return
	}
	network := mapChild(doc.Content[0], "network")
	trusted := mapChild(network, "trusted")
	trusted.Kind, trusted.Tag, trusted.Style = yaml.SequenceNode, "!!seq", yaml.FlowStyle
	if !slices.ContainsFunc(trusted.Content, func(n *yaml.Node) bool { return n.Value == st.Fingerprint }) {
		trusted.Content = append(trusted.Content, &yaml.Node{Kind: yaml.ScalarNode, Tag: "!!str", Value: st.Fingerprint})
	}
	if name := strings.TrimSpace(req.Name); name != "" {
		setScalar(network, "names."+st.Fingerprint, &name, "!!str")
	}
	h.writeDocAndRestart(w, doc)
}

// forgetNetwork 从可信网络中移除一个指纹（不能移除当前所在的网络，以免保存后立即断开）。
func (h *Hub) forgetNetwork(w http.ResponseWriter, r *http.Request) {
	var req struct {
		Fingerprint string `json:"fingerprint"`
	}
	json.NewDecoder(http.MaxBytesReader(w, r.Body, 4<<10)).Decode(&req)
	if st, err := h.gate.probe(); err == nil && st.Fingerprint == req.Fingerprint {
		WriteError(w, http.StatusBadRequest, "这是当前所在的网络，移除后 hub 会立即停止对外服务；请在其他网络下再移除")
		return
	}
	doc, _, err := h.loadDoc()
	if err != nil {
		WriteError(w, http.StatusInternalServerError, err.Error())
		return
	}
	network := mapChild(doc.Content[0], "network")
	if t := lookup(network, "trusted"); t != nil {
		t.Content = slices.DeleteFunc(t.Content, func(n *yaml.Node) bool { return n.Value == req.Fingerprint })
	}
	setScalar(network, "names."+req.Fingerprint, nil, "")
	h.writeDocAndRestart(w, doc)
}
