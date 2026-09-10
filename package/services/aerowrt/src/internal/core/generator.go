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

func (g *Generator) GenerateSingboxConfig(settings model.SystemSettings, nodes []model.Node, groups ...[]model.OutboundGroup) (string, error) {
	var effectiveGroups []model.OutboundGroup
	if len(groups) > 0 && groups[0] != nil {
		effectiveGroups = groups[0]
	}
	dnsBlock := g.buildDNS(settings)

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
		"dns": dnsBlock,
		"inbounds": []map[string]interface{}{
			{
				"type":                  "tun",
				"tag":                   "tun-in",
				"interface_name":        "tun0",
				"address":               []string{"172.19.0.1/30"},
				"auto_route":            true,
				"strict_route":          false,
				// gvisor 在路由器上比 system 更稳定：
				// system 模式下从 tun 发出的回环流量会再被路由表查回 tun0 形成回环黑洞
				// （默认路由已被 auto_route 改成 tun0），gvisor 自带 TCP/IP 栈不会再入 tun。
				"stack":                 "gvisor",
				"route_exclude_address": []string{
					"192.168.0.0/16",
					"10.0.0.0/8",
					"172.16.0.0/12",
					"127.0.0.0/8",
					"100.64.0.0/10",
					"169.254.0.0/16",
					"224.0.0.0/4",
				},
				// 不使用 include_interface 限定 br-lan：会把到路由器自身的管理流量也拉进 TUN，
				// 导致 LuCI/SSH 断连。auto_route + route_exclude_address 已足够隔离私网/本机流量。
			},
			{
				"type":        "mixed",
				"tag":         "mixed-in",
				"listen":      "127.0.0.1",
				"listen_port": 2080,
			},
		},
		"outbounds": g.buildOutbounds(settings, nodes, effectiveGroups),
		"route": map[string]interface{}{
			"default_domain_resolver": defaultDomainResolver(settings),
			// 关闭 auto_detect_interface：在路由器+TUN 模式下，sing-box 自动检测出口
			// 会查回到 tun0（默认路由已被 auto_route 指向 tun0），导致出站数据包自循环。
			// 让 outbound 通过系统默认路由正常选 eth0/wan 离开，这是家用路由器透明代理的标准做法。
			"auto_detect_interface":   false,
			"final":                   finalOutbound(settings),
			"rules":                   g.buildRouteRules(settings),
		},
	}

	data, err := json.MarshalIndent(cfg, "", "  ")
	if err != nil {
		return "", err
	}
	return string(data), nil
}

func (g *Generator) buildOutbounds(settings model.SystemSettings, nodes []model.Node, groups []model.OutboundGroup) []map[string]interface{} {
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

		if n.ChainNode != "" && n.ChainNode != n.ID {
			if parentTag, exists := nodeTagMap[n.ChainNode]; exists && parentTag != n.Tag {
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

	testURL := settings.TestURL
	if testURL == "" {
		testURL = "https://www.gstatic.com/generate_204"
	}
	intervalMins := settings.UrlTestIntervalMins
	if intervalMins <= 0 {
		intervalMins = 10
	}
	intervalStr := fmt.Sprintf("%dm", intervalMins)

	// 自动测速优选分组 (auto-best)
	if len(processedNodes) > 0 {
		outbounds = append(outbounds, map[string]interface{}{
			"type":      "urltest",
			"tag":       "auto-best",
			"outbounds": allTags,
			"url":       testURL,
			"interval":  intervalStr,
			"tolerance": 50,
		})
	}

	// 用户自定义出站分组：可基于 ID 引用具体节点；类型映射到 sing-box 的 selector/urltest/loadbalance
	// 注意 group.Nodes 存的是 Node.ID，需要在本函数中映射回 nodeTagMap
	for _, grp := range groups {
		if grp.Tag == "" {
			continue
		}
		perGroupTags := make([]string, 0, len(grp.Nodes))
		for _, nid := range grp.Nodes {
			if tag, ok := nodeTagMap[nid]; ok {
				perGroupTags = append(perGroupTags, tag)
			}
		}
		if len(perGroupTags) == 0 {
			continue
		}
		grpTag := "group-" + grp.Tag
		switch grp.Type {
		case model.GroupTypeSelector:
			def := ""
			if grp.Selected != "" {
				if tag, ok := nodeTagMap[grp.Selected]; ok {
					def = tag
				}
			}
			outbounds = append(outbounds, map[string]interface{}{
				"type":      "selector",
				"tag":       grpTag,
				"outbounds": perGroupTags,
				"default":   def,
			})
		case model.GroupTypeLoadBalance:
			outbounds = append(outbounds, map[string]interface{}{
				"type":      "loadbalance",
				"tag":       grpTag,
				"outbounds": perGroupTags,
				"url":       testURL,
				"interval":  fmt.Sprintf("%ds", grp.Interval),
			})
		case model.GroupTypeFailover, model.GroupTypeUrlTest:
			fallthrough
		default:
			// sing-box 没有显式 failover，urltest 在低 tolerance 下行为近似 failover
			outbounds = append(outbounds, map[string]interface{}{
				"type":      "urltest",
				"tag":       grpTag,
				"outbounds": perGroupTags,
				"url":       testURL,
				"interval":  fmt.Sprintf("%ds", grp.Interval),
				"tolerance": grp.Tolerance,
			})
		}
	}

	proxyOutbounds := make([]string, 0, len(allTags)+len(groups)+1)
	if len(processedNodes) > 0 {
		proxyOutbounds = append(proxyOutbounds, "auto-best")
	}
	// 自定义分组的 tag 也加入主 selector 的可选项，方便用户在 Clash/Web UI 中直接选分组
	for _, grp := range groups {
		if grp.Tag != "" {
			proxyOutbounds = append(proxyOutbounds, "group-"+grp.Tag)
		}
	}
	proxyOutbounds = append(proxyOutbounds, allTags...)

	defaultTag := activeTag
	if settings.StrategyMode == "urltest" || settings.ActiveNodeID == "auto" {
		defaultTag = "auto-best"
	}

	outbounds = append(outbounds, map[string]interface{}{
		"type":      "selector",
		"tag":       "proxy",
		"outbounds": proxyOutbounds,
		"default":   defaultTag,
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
		{"port": mgmtPorts, "outbound": "direct"},
		{"source_port": mgmtPorts, "outbound": "direct"},
	}

	// 1. 用户自定义强制直连域名规则 (优先级高于代理)
	directDomains := cleanRuleList(settings.DirectDomains)
	if len(directDomains) > 0 {
		rules = append(rules, map[string]interface{}{
			"domain_suffix": directDomains,
			"outbound":      "direct",
		})
	}

	// 2. 用户自定义强制直连 IP / CIDR 规则
	directIPs := cleanRuleList(settings.DirectIPs)
	if len(directIPs) > 0 {
		rules = append(rules, map[string]interface{}{
			"ip_cidr":  directIPs,
			"outbound": "direct",
		})
	}

	// 3. 用户自定义强制代理域名规则
	proxyDomains := cleanRuleList(settings.ProxyDomains)
	if len(proxyDomains) > 0 {
		rules = append(rules, map[string]interface{}{
			"domain_suffix": proxyDomains,
			"outbound":      "proxy",
		})
	}

	// 4. 用户自定义强制代理 IP / CIDR 规则
	proxyIPs := cleanRuleList(settings.ProxyIPs)
	if len(proxyIPs) > 0 {
		rules = append(rules, map[string]interface{}{
			"ip_cidr":  proxyIPs,
			"outbound": "proxy",
		})
	}

	// 5. 路由分流模式
	switch settings.RoutingMode {
	case "direct":
		// 全局直连：所有剩余流量均直连
		rules = append(rules, map[string]interface{}{
			"outbound": "direct",
		})
	case "global":
		// 全局代理：不配置 CN 绕过规则，剩余流量由 final: proxy 转发
	case "bypass_cn":
		fallthrough
	default:
		// 绕过中国大陆：仅在路由器存在 geoip.db 时启用 CN 直连
		if hasGeoIPDB() {
			rules = append(rules, map[string]interface{}{
				"geoip":    []string{"cn"},
				"outbound": "direct",
			})
		}
	}

	return rules
}

func finalOutbound(settings model.SystemSettings) string {
	if settings.RoutingMode == "direct" {
		return "direct"
	}
	return "proxy"
}

// defaultDomainResolver 根据 DNS 模式返回 sing-box route.default_domain_resolver 应指向的 DNS tag
func defaultDomainResolver(settings model.SystemSettings) string {
	switch settings.DNSMode {
	case model.DNSModeSmart:
		return "dns-global"
	case model.DNSModeCustom:
		if len(settings.CustomDnsServers) > 0 && strings.TrimSpace(settings.CustomDnsServers[0]) != "" {
			return "dns-custom-0"
		}
		return "dns-custom-0" // buildDNS 内部会回填默认 8.8.8.8
	default:
		return "dns-upstream"
	}
}

func cleanRuleList(items []string) []string {
	result := make([]string, 0, len(items))
	for _, it := range items {
		trimmed := strings.TrimSpace(it)
		if trimmed != "" && !strings.HasPrefix(trimmed, "#") {
			result = append(result, trimmed)
		}
	}
	return result
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

// buildDNS 依据 DNSMode 生成 sing-box DNS 配置：
//   - mosdns: 全部 UDP 请求走本机 127.0.0.1:mosdns_port（防污染，由 mosdns 智能分流）
//   - smart:  由 sing-box 内部按域名规则判定（国内走 223.5.5.5，海外走 1.1.1.1 代理）
//   - custom: 用户使用自定义上游服务器（udp:// IP、tls://、https:// DoT/DoH 均可）
func (g *Generator) buildDNS(settings model.SystemSettings) map[string]interface{} {
	mode := string(settings.DNSMode)
	if mode == "" {
		mode = string(model.DNSModeMosDNS)
	}

	servers := []map[string]interface{}{}
	defaultDnsTag := "dns-upstream"

	switch mode {
	case string(model.DNSModeSmart):
		servers = append(servers,
			map[string]interface{}{
				"type":        "udp",
				"tag":         "dns-cn",
				"server":      "223.5.5.5",
				"server_port": 53,
			},
			map[string]interface{}{
				"type":    "https",
				"tag":     "dns-global",
				"server":  "1.1.1.1",
				"detour":  "proxy",
				"domain_resolver": "dns-cn",
			},
		)
		defaultDnsTag = "dns-global"
		return map[string]interface{}{
			"servers": servers,
			"rules": []map[string]interface{}{
				{"geoip": []string{"cn"}, "server": "dns-cn"},
			},
			"strategy":          "prefer_ipv4",
			"default_dns":       defaultDnsTag,
			"independent_cache": true,
		}

	case string(model.DNSModeCustom):
		customServers := []string{}
		for _, s := range settings.CustomDnsServers {
			trimmed := strings.TrimSpace(s)
			if trimmed != "" {
				customServers = append(customServers, trimmed)
			}
		}
		if len(customServers) == 0 {
			customServers = []string{"8.8.8.8", "1.1.1.1"}
		}
		for i, sv := range customServers {
			var server map[string]interface{}
			tag := fmt.Sprintf("dns-custom-%d", i)
			if strings.HasPrefix(sv, "https://") {
				server = map[string]interface{}{
					"type":   "https",
					"tag":    tag,
					"server": sv,
				}
			} else if strings.HasPrefix(sv, "tls://") {
				server = map[string]interface{}{
					"type":   "tls",
					"tag":    tag,
					"server": strings.TrimPrefix(sv, "tls://"),
				}
			} else if strings.HasPrefix(sv, "udp://") {
				server = map[string]interface{}{
					"type":   "udp",
					"tag":    tag,
					"server": strings.TrimPrefix(sv, "udp://"),
				}
			} else {
				// 纯 IP 默认 udp 53
				server = map[string]interface{}{
					"type":        "udp",
					"tag":         tag,
					"server":      sv,
					"server_port": 53,
				}
			}
			servers = append(servers, server)
		}
		defaultDnsTag = fmt.Sprintf("dns-custom-%d", 0)
		return map[string]interface{}{
			"servers":           servers,
			"strategy":          "prefer_ipv4",
			"default_dns":       defaultDnsTag,
			"independent_cache": true,
		}

	default: // DNSModeMosDNS
		dnsPort := 5335
		if settings.MosDNSPort > 0 {
			dnsPort = settings.MosDNSPort
		}
		servers = append(servers,
			map[string]interface{}{
				"type":        "udp",
				"tag":         "dns-upstream",
				"server":      "127.0.0.1",
				"server_port": dnsPort,
			},
			map[string]interface{}{
				"type":        "udp",
				"tag":         "dns-fallback",
				"server":      "223.5.5.5",
				"server_port": 53,
			},
		)
		return map[string]interface{}{
			"servers":  servers,
			"strategy": "prefer_ipv4",
		}
	}
}
