package core

import (
	"encoding/json"
	"fmt"
	"os"
	"aerowrt/internal/model"
)

type Generator struct{}

func NewGenerator() *Generator {
	return &Generator{}
}

func (g *Generator) GenerateSingboxConfig(settings model.SystemSettings, nodes []model.Node) (string, error) {
	nodeMap := make(map[string]model.Node)
	for _, n := range nodes {
		nodeMap[n.ID] = n
	}

	dnsServerAddr := "127.0.0.1:5335"
	if settings.DNSMode == model.DNSModeMosDNS && settings.MosDNSPort > 0 {
		dnsServerAddr = fmt.Sprintf("127.0.0.1:%d", settings.MosDNSPort)
	}

	cfg := map[string]interface{}{
		"log": map[string]interface{}{
			"level":     "info",
			"timestamp": true,
		},
		"experimental": map[string]interface{}{
			"clash_api": map[string]interface{}{
				"external_controller": "127.0.0.1:9090",
			},
		},
		"dns": map[string]interface{}{
			"servers": []map[string]interface{}{
				{
					"tag":     "dns-upstream",
					"address": dnsServerAddr,
					"detour":  "direct",
				},
			},
		},
		"inbounds": []map[string]interface{}{
			{
				"type":           "tun",
				"tag":            "tun-in",
				"interface_name": "tun0",
				"inet4_address":  "172.19.0.1/30",
				"auto_route":     true,
				"strict_route":   false,
				"stack":          "system",
				"sniff":          true,
			},
		},
		"outbounds": g.buildOutbounds(settings, nodes, nodeMap),
		"route": map[string]interface{}{
			"auto_detect_interface": true,
			"final":                 "proxy",
			"rules":                 g.buildRouteRules(),
		},
	}

	data, err := json.MarshalIndent(cfg, "", "  ")
	if err != nil {
		return "", err
	}
	return string(data), nil
}

func (g *Generator) buildOutbounds(settings model.SystemSettings, nodes []model.Node, nodeMap map[string]model.Node) []map[string]interface{} {
	outbounds := []map[string]interface{}{
		{"type": "direct", "tag": "direct"},
		{"type": "dns", "tag": "dns-out"},
	}

	for _, n := range nodes {
		ob := map[string]interface{}{
			"tag":         n.Tag,
			"server":      n.Server,
			"server_port": n.Port,
		}

		if n.ChainNode != "" {
			if parent, exists := nodeMap[n.ChainNode]; exists {
				ob["detour"] = parent.Tag
			}
		}

		switch n.Protocol {
		case model.ProtocolVLESS:
			ob["type"] = "vless"
			ob["uuid"] = n.UUID
		case model.ProtocolTrojan:
			ob["type"] = "trojan"
			ob["password"] = n.Password
		case model.ProtocolSS:
			ob["type"] = "shadowsocks"
			ob["password"] = n.Password
			ob["method"] = "aes-128-gcm"
		default:
			ob["type"] = "vless"
		}
		outbounds = append(outbounds, ob)
	}

	// 组装所有节点标签供 selector 与 clash_api 调度使用
	allTags := make([]string, 0, len(nodes))
	for _, n := range nodes {
		allTags = append(allTags, n.Tag)
	}
	if len(allTags) == 0 {
		allTags = append(allTags, "direct")
	}

	activeTag := "direct"
	if active, ok := nodeMap[settings.ActiveNodeID]; ok {
		activeTag = active.Tag
	} else if len(allTags) > 0 {
		activeTag = allTags[0]
	}

	outbounds = append(outbounds, map[string]interface{}{
		"type":      "selector",
		"tag":       "proxy",
		"outbounds": allTags,
		"default":   activeTag,
	})

	return outbounds
}

func (g *Generator) buildRouteRules() []map[string]interface{} {
	rules := []map[string]interface{}{
		{"protocol": "dns", "outbound": "dns-out"},
		{"ip_is_private": true, "outbound": "direct"},
	}

	// 仅在路由器存在 geoip.db 时启用 geoip 规则，防止因缺少数据库文件导致 Sing-box 启动闪退
	if hasGeoIPDB() {
		rules = append(rules, map[string]interface{}{
			"geoip":    []string{"cn"},
			"outbound": "direct",
		})
	}

	return rules
}

func hasGeoIPDB() bool {
	paths := []string{
		"/var/run/sing-box/geoip.db",
		"/usr/share/sing-box/geoip.db",
		"/etc/sing-box/geoip.db",
		"/etc/aerowrt/geoip.db",
	}
	for _, p := range paths {
		if _, err := os.Stat(p); err == nil {
			return true
		}
	}
	return false
}
