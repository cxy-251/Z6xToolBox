package lanscan

import "testing"

func TestParseReply(t *testing.T) {
	raw := "HTTP/1.1 200 OK\r\nCACHE-CONTROL: max-age=1800\r\nLOCATION: http://192.168.0.1:1900/desc.xml\r\nSERVER: Linux UPnP/1.0 Router\r\nST: upnp:rootdevice\r\n\r\n"
	rp, ok := parseReply([]byte(raw), "192.168.0.1")
	if !ok || rp.location != "http://192.168.0.1:1900/desc.xml" || rp.st != "upnp:rootdevice" || rp.server != "Linux UPnP/1.0 Router" {
		t.Fatalf("解析结果不正确：%+v", rp)
	}
	if _, ok := parseReply([]byte("M-SEARCH * HTTP/1.1\r\n\r\n"), "1.2.3.4"); ok {
		t.Error("其他设备发出的搜索请求不应被当作回复")
	}
}

func TestSameHost(t *testing.T) {
	cases := map[string]bool{
		"http://192.168.0.1:1900/desc.xml": true,
		"http://192.168.0.2/desc.xml":      false, // 指向其他主机
		"file:///etc/passwd":               false,
		"http://192.168.0.1.evil.com/x":    false,
	}
	for loc, want := range cases {
		if got := sameHost(loc, "192.168.0.1"); got != want {
			t.Errorf("%s：期望 %v，实际 %v", loc, want, got)
		}
	}
}

func TestShortType(t *testing.T) {
	if got := shortType("urn:schemas-upnp-org:device:MediaRenderer:1"); got != "MediaRenderer" {
		t.Errorf("实际 %s", got)
	}
}
