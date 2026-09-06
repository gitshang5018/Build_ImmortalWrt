package core

import (
	"strings"
	"testing"
	"aerowrt/internal/model"
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

	// 验证 TUN 网卡地址为数组形式
	if !strings.Contains(configJSON, `"address": [`) || !strings.Contains(configJSON, "172.19.0.1/30") {
		t.Errorf("expected TUN address array in config: %s", configJSON)
	}
}

func TestGenerateSingboxTrojanAndReality(t *testing.T) {
	settings := model.SystemSettings{ActiveNodeID: "n-reality"}
	nodes := []model.Node{
		{
			ID:       "n-trojan",
			Tag:      "Trojan-Node",
			Protocol: model.ProtocolTrojan,
			Server:   "tr.example.com",
			Port:     443,
			Password: "pass",
		},
		{
			ID:        "n-reality",
			Tag:       "Trojan-Node", // 故意同名测试去重
			Protocol:  model.ProtocolVLESS,
			Server:    "vl.example.com",
			Port:      443,
			UUID:      "v-uuid",
			Security:  "reality",
			PublicKey: "pbk123",
			ShortID:   "sid123",
		},
	}

	gen := NewGenerator()
	configJSON, err := gen.GenerateSingboxConfig(settings, nodes)
	if err != nil {
		t.Fatalf("GenerateSingboxConfig error: %v", err)
	}

	// Trojan 必须包含 tls
	if !strings.Contains(configJSON, `"server_name": "tr.example.com"`) {
		t.Errorf("expected trojan to have tls with server_name: %s", configJSON)
	}

	// Reality 必须包含 reality 结构
	if !strings.Contains(configJSON, `"public_key": "pbk123"`) || !strings.Contains(configJSON, `"short_id": "sid123"`) {
		t.Errorf("expected reality block in vless outbound: %s", configJSON)
	}

	// 验证重名标签被去重为 Trojan-Node-2
	if !strings.Contains(configJSON, "Trojan-Node-2") {
		t.Errorf("expected duplicate tag to be deduplicated to Trojan-Node-2: %s", configJSON)
	}
}

