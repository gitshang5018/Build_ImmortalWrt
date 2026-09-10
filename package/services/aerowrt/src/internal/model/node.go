package model

type ProtocolType string

const (
	ProtocolVLESS     ProtocolType = "vless"
	ProtocolVMess     ProtocolType = "vmess"
	ProtocolTrojan    ProtocolType = "trojan"
	ProtocolSS        ProtocolType = "shadowsocks"
	ProtocolHysteria2 ProtocolType = "hysteria2"
)

type Node struct {
	ID        string       `json:"id"`
	Tag       string       `json:"tag"`
	Protocol  ProtocolType `json:"protocol"`
	Server    string       `json:"server"`
	Port      int          `json:"port"`
	UUID      string       `json:"uuid,omitempty"`
	Password  string       `json:"password,omitempty"`
	Method    string       `json:"method,omitempty"`
	Network   string       `json:"network,omitempty"`
	Path      string       `json:"path,omitempty"`
	Security  string       `json:"security,omitempty"`
	SNI       string       `json:"sni,omitempty"`
	PublicKey string       `json:"public_key,omitempty"`
	ShortID   string       `json:"short_id,omitempty"`
	DelayMs   int64        `json:"delay_ms"`
	ChainNode string       `json:"chain_node,omitempty"` // 前置链式代理节点ID
}

type GroupType string

const (
	GroupTypeUrlTest     GroupType = "urltest"
	GroupTypeFailover    GroupType = "failover"
	GroupTypeLoadBalance GroupType = "loadbalance"
	GroupTypeSelector    GroupType = "selector"
)

type OutboundGroup struct {
	ID        string    `json:"id"`
	Tag       string    `json:"tag"`
	Type      GroupType `json:"type"`
	Nodes     []string  `json:"nodes"` // 包含的 Node ID 列表
	Selected  string    `json:"selected,omitempty"`
	Interval  int       `json:"interval,omitempty"` // 测速间隔(秒)
	Tolerance int       `json:"tolerance,omitempty"`
}

type DNSMode string

const (
	DNSModeMosDNS DNSMode = "mosdns" // 接入本地 MosDNS 端口
	DNSModeSmart  DNSMode = "smart"  // 内置智能分流
	DNSModeCustom DNSMode = "custom" // 自定义上游 DNS
)

type SystemSettings struct {
	ActiveNodeID        string   `json:"active_node_id"`
	ActiveGroup         string   `json:"active_group"`
	RoutingMode         string   `json:"routing_mode"` // bypass_cn, global, direct
	DNSMode             DNSMode  `json:"dns_mode"`
	MosDNSPort          int      `json:"mosdns_port"`           // 默认 5335
	HttpPort            int      `json:"http_port"`             // 默认 9099
	TestURL             string   `json:"test_url"`              // 测速URL
	StrategyMode        string   `json:"strategy_mode"`         // manual, urltest
	UrlTestIntervalMins int      `json:"urltest_interval_mins"` // 自动优选测速周期 (分钟)
	AutoUpdateSubHours  int      `json:"auto_update_sub_hours"` // 订阅自动更新周期 (小时，0为禁用)
	CustomDnsServers    []string `json:"custom_dns_servers"`    // DNSMode=custom 时使用的上游 DNS（如 8.8.8.8 / tls://1.1.1.1 / https://dns.google/dns-query）
	DirectDomains       []string `json:"direct_domains"`        // 自定义直连域名后缀
	ProxyDomains        []string `json:"proxy_domains"`         // 自定义代理域名后缀
	DirectIPs           []string `json:"direct_ips"`            // 自定义直连 IP/CIDR
	ProxyIPs            []string `json:"proxy_ips"`             // 自定义代理 IP/CIDR
}

type Subscription struct {
	ID        string `json:"id"`
	Name      string `json:"name"`
	URL       string `json:"url"`
	UpdatedAt string `json:"updated_at"`
	NodeCount int    `json:"node_count"`
}

