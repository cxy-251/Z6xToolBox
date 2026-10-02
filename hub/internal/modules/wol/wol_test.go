package wol

import (
	"bytes"
	"testing"
)

func TestMagicPacket(t *testing.T) {
	mac, err := parseMAC("aa-bb-cc-dd-ee-ff")
	if err != nil {
		t.Fatal(err)
	}
	p := MagicPacket(mac)
	if len(p) != 102 || !bytes.Equal(p[:6], []byte{0xff, 0xff, 0xff, 0xff, 0xff, 0xff}) {
		t.Fatalf("魔术包头部或长度不正确：%d", len(p))
	}
	for i := 0; i < 16; i++ {
		if !bytes.Equal(p[6+i*6:12+i*6], mac) {
			t.Fatalf("第 %d 次重复的 MAC 不正确", i+1)
		}
	}
}

func TestParseMACFormats(t *testing.T) {
	for _, s := range []string{"AA:BB:CC:DD:EE:FF", "aabbccddeeff", "AA-BB-CC-DD-EE-FF"} {
		if _, err := parseMAC(s); err != nil {
			t.Errorf("%s 应能解析：%v", s, err)
		}
	}
	for _, s := range []string{"AA:BB:CC", "zz:bb:cc:dd:ee:ff", ""} {
		if _, err := parseMAC(s); err == nil {
			t.Errorf("%s 应被拒绝", s)
		}
	}
}
