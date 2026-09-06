package core

import (
	"encoding/json"
	"fmt"
	"os"
	"strings"
	"aerowrt/internal/model"
)

type Generator struct{}

func NewGenerator() *Generator {
	return &Generator{}
}

func (g *Generator) GenerateSingboxConfig(settings model.SystemSettings, nodes []model.Node) (string, error) {
	dnsPort := 5335
	if settings.DNSMode == model.DNSModeMosDNS && settings.MosDNSPort > 0 {
		dnsPort = settings.MosDNSPort
	}

	cfg := map[string]interface{}{
		"log": map[string]interface{}{
			"level":     "info",
			"timestamp": true,
		},
		"experimental": map[string]interface{}{
			"clash_api": map[string]interface{}{
				"external_controller": "127.0.0.1:9090",
				"default_mode":        "rule",
			},
		},
		"dns": map[string]interface{}{
			"servers": []map[string]interface{}{
				{
					"type":        "udp",
					"tag":         "dns-upstream",
					"server":      "127.0.0.1",
					"server_port": dnsPort,
				},
				{
					"type":        "udp",
					"tag":         "dns-fallback",
					"server":      "223.5.5.5",
					"server_port": 53,
				},
			},
			"strategy": "prefer_ipv4",
		},
		"inbounds": []map[string]interface{}{
			{
				"type":           "tun",
				"tag":            "tun-in",
				"interface_name": "tun0",
				"address":        []string{"172.19.0.1/30"},
				"auto_route":     true,
				"strict_route":   false,
				"stack":          "system",
				"route_exclude_address": []string{
					"192.168.0.0/16",
					"10.0.0.0/8",
					"172.16.0.0/12",
					"127.0.0.0/8",
				},
			},
			{
				"type":        "mixed",
				"tag":         "mixed-in",
				"listen":      "127.0.0.1",
				"listen_port": 2080,
			},
		},
		"outbounds": g.buildOutbounds(settings, nodes),
		"route": map[string]interface{}{
			"default_domain_resolver": "dns-upstream",
			"auto_detect_interface":   true,
			"final":                   "proxy",
			"rules":                   g.buildRouteRules(settings),
		},
	}

	data, err := json.MarshalIndent(cfg, "", "  ")
	if err != nil {
		return "", err
	}
	return string(data), nil
}

func (g *Generator) buildOutbounds(settings model.SystemSettings, nodes []model.Node) []map[string]interface{} {
	outbounds := []map[string]interface{}{
		{"type": "direct", "tag": "direct"},
	}

	// 标签唯一性处理，避免因重复标签导致 Sing-box 解析崩溃
	usedTags := make(map[string]int)
	processedNodes := make([]model.Node, len(nodes))
	copy(processedNodes, nodes)
	nodeTagMap := make(map[string]string)

	for i := range processedNodes {
		tag := strings.TrimSpace(processedNodes[i].Tag)
		if tag == "" {
			tag = fmt.Sprintf("node-%d", i+1)
		}
		if count, exists := usedTags[tag]; exists {
			usedTags[tag] = count + 1
			tag = fmt.Sprintf("%s-%d", tag, count+1)
		} else {
			usedTags[tag] = 1
		}
		processedNodes[i].Tag = tag
		nodeTagMap[processedNodes[i].ID] = tag
	}

	for _, n := range processedNodes {
		ob := map[string]interface{}{
			"tag":         n.Tag,
			"server":      n.Server,
			"server_port": n.Port,
		}

		if n.ChainNode != "" {
			if parentTag, exists := nodeTagMap[n.ChainNode]; exists {
				ob["detour"] = parentTag
			}
		}

		sni := n.SNI
		if sni == "" {
			sni = n.Server
		}

		switch n.Protocol {
		case model.ProtocolVLESS:
			ob["type"] = "vless"
			ob["uuid"] = n.UUID
			if n.Security == "reality" || n.PublicKey != "" {
				ob["tls"] = map[string]interface{}{
					"enabled":     true,
					"server_name": sni,
					"reality": map[string]interface{}{
						"enabled":    true,
						"public_key": n.PublicKey,
						"short_id":   n.ShortID,
					},
					"utls": map[string]interface{}{
						"enabled":     true,
						"fingerprint": "chrome",
					},
				}
			} else if n.Security == "tls" {
				ob["tls"] = map[string]interface{}{
					"enabled":     true,
					"server_name": sni,
				}
			}
			if n.Network == "ws" {
				path := n.Path
				if path == "" {
					path = "/"
				}
				ob["transport"] = map[string]interface{}{
					"type": "ws",
					"path": path,
					"headers": map[string]string{
						"Host": sni,
					},
				}
			} else if n.Network == "grpc" {
				ob["transport"] = map[string]interface{}{
					"type":         "grpc",
					"service_name": n.Path,
				}
			}

		case model.ProtocolTrojan:
			ob["type"] = "trojan"
			ob["password"] = n.Password
			// Sing-box 规范强制 Trojan 必须配置 tls
			ob["tls"] = map[string]interface{}{
				"enabled":     true,
				"server_name": sni,
			}
			if n.Network == "ws" {
				path := n.Path
				if path == "" {
					path = "/"
				}
				ob["transport"] = map[string]interface{}{
					"type": "ws",
					"path": path,
					"headers": map[string]string{
						"Host": sni,
					},
				}
			}

		case model.ProtocolVMess:
			ob["type"] = "vmess"
			ob["uuid"] = n.UUID
			ob["security"] = "auto"
			if n.Security == "tls" {
				ob["tls"] = map[string]interface{}{
					"enabled":     true,
					"server_name": sni,
				}
			}
			if n.Network == "ws" {
				path := n.Path
				if path == "" {
					path = "/"
				}
				ob["transport"] = map[string]interface{}{
					"type": "ws",
					"path": path,
					"headers": map[string]string{
						"Host": sni,
					},
				}
			}

		case model.ProtocolHysteria2:
			ob["type"] = "hysteria2"
			ob["password"] = n.Password
			ob["tls"] = map[string]interface{}{
				"enabled":     true,
				"server_name": sni,
			}

		case model.ProtocolSS:
			ob["type"] = "shadowsocks"
			ob["password"] = n.Password
			method := n.Method
			if method == "" {
				method = "aes-128-gcm"
			}
			ob["method"] = method

		default:
			ob["type"] = "vless"
			ob["uuid"] = n.UUID
		}
		outbounds = append(outbounds, ob)
	}

	allTags := make([]string, 0, len(processedNodes))
	for _, n := range processedNodes {
		allTags = append(allTags, n.Tag)
	}
	if len(allTags) == 0 {
		allTags = append(allTags, "direct")
	}

	activeTag := allTags[0]
	if mappedTag, ok := nodeTagMap[settings.ActiveNodeID]; ok {
		activeTag = mappedTag
	}

	outbounds = append(outbounds, map[string]interface{}{
		"type":      "selector",
		"tag":       "proxy",
		"outbounds": allTags,
		"default":   activeTag,
	})

	return outbounds
}

func (g *Generator) buildRouteRules(settings model.SystemSettings) []map[string]interface{} {
	mgmtPorts := []int{22, 53, 80, 443, 8080, 8443, 9090}
	if settings.HttpPort > 0 {
		mgmtPorts = append(mgmtPorts, settings.HttpPort)
	}
	if settings.MosDNSPort > 0 {
		mgmtPorts = append(mgmtPorts, settings.MosDNSPort)
	}

	rules := []map[string]interface{}{
		{"action": "sniff"},
		{"protocol": "dns", "action": "hijack-dns"},
		{"ip_is_private": true, "outbound": "direct"},
		{"source_port": mgmtPorts, "outbound": "direct"},
	}

	if settings.HttpPort > 0 {
		rules = append(rules, map[string]interface{}{
			"port":     []int{settings.HttpPort},
			"outbound": "direct",
		})
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
