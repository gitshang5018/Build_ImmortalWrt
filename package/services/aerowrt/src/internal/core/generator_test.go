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

	// 验证包含 MosDNS 端口与类型定义 (Sing-box 1.12+ 格式)
	if !strings.Contains(configJSON, `"server": "127.0.0.1"`) || !strings.Contains(configJSON, `"server_port": 5335`) {
		t.Errorf("expected MosDNS 127.0.0.1:5335 in DNS servers: %s", configJSON)
	}

	// 验证包含 hijack-dns 规则动作
	if !strings.Contains(configJSON, `"action": "hijack-dns"`) {
		t.Errorf("expected hijack-dns action in rules: %s", configJSON)
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

func TestGenerateSingboxCustomRulesAndStrategy(t *testing.T) {
	settings := model.SystemSettings{
		ActiveNodeID:        "n1",
		RoutingMode:         "bypass_cn",
		StrategyMode:        "urltest",
		TestURL:             "https://www.gstatic.com/generate_204",
		UrlTestIntervalMins: 5,
		DirectDomains:       []string{"direct.lan", "local.domain"},
		ProxyDomains:        []string{"openai.com", "github.com"},
		DirectIPs:           []string{"192.168.100.0/24"},
		ProxyIPs:            []string{"1.0.0.1/32"},
	}
	nodes := []model.Node{
		{ID: "n1", Tag: "Node-1", Protocol: model.ProtocolSS, Server: "s1.com", Port: 8388, Password: "p1"},
		{ID: "n2", Tag: "Node-2", Protocol: model.ProtocolSS, Server: "s2.com", Port: 8388, Password: "p2"},
	}

	gen := NewGenerator()
	configJSON, err := gen.GenerateSingboxConfig(settings, nodes)
	if err != nil {
		t.Fatalf("GenerateSingboxConfig error: %v", err)
	}

	// 1. 验证 urltest 分组存在
	if !strings.Contains(configJSON, `"type": "urltest"`) || !strings.Contains(configJSON, `"tag": "auto-best"`) {
		t.Errorf("expected auto-best urltest outbound in config: %s", configJSON)
	}
	if !strings.Contains(configJSON, `"url": "https://www.gstatic.com/generate_204"`) {
		t.Errorf("expected test url in urltest outbound: %s", configJSON)
	}
	if !strings.Contains(configJSON, `"interval": "5m"`) {
		t.Errorf("expected 5m interval in urltest outbound: %s", configJSON)
	}

	// 2. 验证 proxy selector 的 default 设为 auto-best
	if !strings.Contains(configJSON, `"default": "auto-best"`) {
		t.Errorf("expected proxy selector default to be auto-best in urltest mode: %s", configJSON)
	}

	// 3. 验证自定义直连域名与 IP
	if !strings.Contains(configJSON, "direct.lan") || !strings.Contains(configJSON, "192.168.100.0/24") {
		t.Errorf("expected direct domains and ips in route rules: %s", configJSON)
	}

	// 4. 验证自定义代理域名与 IP
	if !strings.Contains(configJSON, "openai.com") || !strings.Contains(configJSON, "1.0.0.1/32") {
		t.Errorf("expected proxy domains and ips in route rules: %s", configJSON)
	}

	// 5. 验证 direct 模式
	settingsDirect := settings
	settingsDirect.RoutingMode = "direct"
	directConfig, err := gen.GenerateSingboxConfig(settingsDirect, nodes)
	if err != nil {
		t.Fatalf("GenerateSingboxConfig direct error: %v", err)
	}
	if !strings.Contains(directConfig, `"final": "direct"`) {
		t.Errorf("expected final to be direct in direct routing mode: %s", directConfig)
	}
}


