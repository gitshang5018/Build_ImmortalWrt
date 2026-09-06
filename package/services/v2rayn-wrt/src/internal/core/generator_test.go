package core

import (
	"strings"
	"testing"
	"v2rayn-wrt/internal/model"
)

func TestGenerateSingboxConfigWithMosDNS(t *testing.T) {
	settings := model.SystemSettings{
		ActiveNodeID: "n-exit",
		RoutingMode:  "bypass_cn",
		DNSMode:      model.DNSModeMosDNS,
		MosDNSPort:   5335,
	}

	nodes := []model.Node{
		{ID: "n-transit", Tag: "前置中转", Protocol: model.ProtocolSS, Server: "transit.com", Port: 8388, Password: "pw"},
		{ID: "n-exit", Tag: "落地出口", Protocol: model.ProtocolVLESS, Server: "exit.com", Port: 443, UUID: "uuid", ChainNode: "n-transit"},
	}

	gen := NewGenerator()
	configJSON, err := gen.GenerateSingboxConfig(settings, nodes)
	if err != nil {
		t.Fatalf("GenerateSingboxConfig error: %v", err)
	}

	// 验证包含 MosDNS 端口
	if !strings.Contains(configJSON, "127.0.0.1:5335") {
		t.Errorf("expected MosDNS 127.0.0.1:5335 in DNS servers")
	}

	// 验证链式代理 detour 设置
	if !strings.Contains(configJSON, `"detour": "前置中转"`) {
		t.Errorf("expected detour for chain proxy in outbound: %s", configJSON)
	}
}
