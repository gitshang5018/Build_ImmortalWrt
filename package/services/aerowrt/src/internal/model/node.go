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
	Network   string       `json:"network,omitempty"`
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
	ActiveNodeID string  `json:"active_node_id"`
	ActiveGroup  string  `json:"active_group"`
	RoutingMode  string  `json:"routing_mode"` // bypass_cn, global, direct
	DNSMode      DNSMode `json:"dns_mode"`
	MosDNSPort   int     `json:"mosdns_port"` // 默认 5335
	HttpPort     int     `json:"http_port"`   // 默认 9099
}

type Subscription struct {
	ID        string `json:"id"`
	Name      string `json:"name"`
	URL       string `json:"url"`
	UpdatedAt string `json:"updated_at"`
	NodeCount int    `json:"node_count"`
}

