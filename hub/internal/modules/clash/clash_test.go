package clash

import (
	"strings"
	"testing"
)

func TestWithWhitelist(t *testing.T) {
	src := "# 订阅注释\nmixed-port: 7890\nproxies:\n  - {name: a, type: ss, server: x, port: 1}\nrules:\n  - GEOSITE,CN,REJECT\n  - MATCH,PROXY\n"
	out, n, err := withWhitelist([]byte(src), []string{"mirrors.tuna.tsinghua.edu.cn", " ", "# 注释", "10.0.0.0/8", "1.2.3.4"}, true)
	if err != nil || n != 2 {
		t.Fatalf("%v %d", err, n)
	}
	s := string(out)
	want := []string{"log-level: info", "DOMAIN-SUFFIX,mirrors.tuna.tsinghua.edu.cn,DIRECT", "IP-CIDR,10.0.0.0/8,DIRECT,no-resolve", "IP-CIDR,1.2.3.4/32,DIRECT,no-resolve", "GEOSITE,CN,REJECT", "mixed-port: 7890", "# 订阅注释"}
	for _, w := range want {
		if !strings.Contains(s, w) {
			t.Errorf("缺少 %q：\n%s", w, s)
		}
	}
	if strings.Index(s, "tuna") > strings.Index(s, "GEOSITE") {
		t.Error("白名单应在原规则之前")
	}
	if _, _, err := withWhitelist([]byte("c3M6Ly9ub2RlcyBsaXN0"), nil, false); err == nil {
		t.Error("节点列表格式应报错")
	}
}

func TestRejectLine(t *testing.T) {
	m := &Module{rej: map[string]*rejected{}, st: state{Whitelist: []string{"tsinghua.edu.cn"}}}
	for _, l := range []string{"[TCP] 127.0.0.1:41234 --> api.bilibili.com:443 match GeoSite(cn) using REJECT",
		"[TCP] 127.0.0.1:41235 --> mirrors.tuna.tsinghua.edu.cn:443 match DomainSuffix(cn) using REJECT",
		"[TCP] 127.0.0.1:41236 --> github.com:443 match Match using PROXY[节点]"} {
		if mm := rejectLine.FindStringSubmatch(l); mm != nil {
			m.recordReject(mm[1], mm[2])
		}
	}
	if len(m.rej) != 1 || m.rej["api.bilibili.com"] == nil || m.rej["api.bilibili.com"].Rule != "GeoSite(cn)" {
		t.Fatalf("应只记下被拒绝且不在白名单中的域名：%+v", m.rej)
	}
}
