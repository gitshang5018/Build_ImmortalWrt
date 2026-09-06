package core

import (
	"encoding/json"
	"fmt"
	"v2rayn-wrt/internal/model"
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
			"rules": []map[string]interface{}{
				{"protocol": "dns", "outbound": "dns-out"},
				{"ip_is_private": true, "outbound": "direct"},
				{"geoip": []string{"cn"}, "outbound": "direct"},
			},
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

	// 默认出站代理标签
	activeTag := "direct"
	if active, ok := nodeMap[settings.ActiveNodeID]; ok {
		activeTag = active.Tag
	}
	outbounds = append(outbounds, map[string]interface{}{
		"type":      "selector",
		"tag":       "proxy",
		"outbounds": []string{activeTag},
		"default":   activeTag,
	})

	return outbounds
}
