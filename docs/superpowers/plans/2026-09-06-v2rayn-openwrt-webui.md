# v2rayN-Wrt OpenWrt 现代透明代理管理套件 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 构建一个专为 OpenWrt 量身定制、Go 单二进制驱动、内嵌现代响应式 WebUI 的透明代理管理套件（v2rayN-Wrt），支持 Sing-box/Xray 双内核、策略组调度、链式代理、MosDNS 联动与断网保护。

**架构：** 后端采用 Go 语言构建轻量级守护程序，内嵌编译好的 SPA 前端静态资源，通过 RESTful/WebSocket API 向前端提供实时数据，底层通过 TUN 虚拟网卡与系统网络路由协同，并将 DNS 流量无缝导向本地 MosDNS 或内置分流。

**技术栈：** Go 1.24, HTML5/CSS/JavaScript (现代暗黑响应式 SPA), Sing-box, Xray-core, OpenWrt procd, TUN/nftables.

---

## 文件结构规划

本工程位于 `package/services/v2rayn-wrt/`，结构划分如下：

```
package/services/v2rayn-wrt/
├── Makefile                               # OpenWrt 编译与打包 Makefile
├── files/
│   ├── v2rayn-wrt.init                    # OpenWrt procd 守护服务脚本
│   └── v2rayn-wrt.config                  # OpenWrt UCI 默认配置文件
└── src/
    ├── go.mod
    ├── main.go                            # 入口：初始化、启动 API 与看门狗
    ├── internal/
    │   ├── model/
    │   │   ├── node.go                    # 节点、协议、订阅数据结构
    │   │   ├── node_test.go
    │   │   ├── group.go                   # 策略组与链式代理数据结构
    │   │   └── config.go                  # 全局系统与 DNS/MosDNS 配置模型
    │   ├── parser/
    │   │   ├── parser.go                  # 订阅解析引擎 (Base64/Clash/URL)
    │   │   └── parser_test.go
    │   ├── pinger/
    │   │   ├── pinger.go                  # 高并发真实网络延迟探测器
    │   │   └── pinger_test.go
    │   ├── group/
    │   │   ├── manager.go                 # 策略组调度 (UrlTest/Failover/LoadBalance)
    │   │   └── manager_test.go
    │   ├── core/
    │   │   ├── generator.go               # Sing-box / Xray 配置渲染与预检
    │   │   ├── generator_test.go
    │   │   └── runner.go                  # 内核进程生命周期与热重载
    │   └── server/
    │       ├── server.go                  # RESTful API 与 WebSocket 路由
    │       └── server_test.go
    └── web/
        ├── embed.go                       # go:embed 静态资源绑定
        └── dist/
            ├── index.html                 # 响应式仪表盘
            ├── app.css                    # 现代暗黑设计样式
            └── app.js                     # 前端交互与 WebSocket 客户端
```

---

### 任务 1：核心数据模型与节点定义 (Core Models)

**文件：**
- 创建：`package/services/v2rayn-wrt/src/go.mod`
- 创建：`package/services/v2rayn-wrt/src/internal/model/node.go`
- 测试：`package/services/v2rayn-wrt/src/internal/model/node_test.go`

- [ ] **步骤 1：初始化 Go 模块**

在 `package/services/v2rayn-wrt/src` 目录下运行：
```bash
go mod init v2rayn-wrt
```

- [ ] **步骤 2：编写失败的节点与策略组模型测试**

在 `package/services/v2rayn-wrt/src/internal/model/node_test.go` 中编写：
```go
package model

import (
	"encoding/json"
	"testing"
)

func TestNodeSerialization(t *testing.T) {
	node := Node{
		ID:        "node-1",
		Tag:       "香港 01 [BGP专线]",
		Protocol:  ProtocolVLESS,
		Server:    "hk.node.com",
		Port:      443,
		UUID:      "a8b1c2d3-0000-0000-0000-000000000001",
		Network:   "tcp",
		Security:  "reality",
		DelayMs:   28,
		ChainNode: "node-transit-1", // 链式前置代理节点ID
	}

	data, err := json.Marshal(node)
	if err != nil {
		t.Fatalf("Marshal failed: %v", err)
	}

	var decoded Node
	if err := json.Unmarshal(data, &decoded); err != nil {
		t.Fatalf("Unmarshal failed: %v", err)
	}

	if decoded.Tag != node.Tag || decoded.ChainNode != "node-transit-1" {
		t.Errorf("expected tag %s and chain %s, got %s / %s", node.Tag, "node-transit-1", decoded.Tag, decoded.ChainNode)
	}
}
```

- [ ] **步骤 3：运行测试验证失败**

运行：`go test ./internal/model/... -v`
预期：FAIL，报错未定义 `Node`, `ProtocolVLESS` 等。

- [ ] **步骤 4：编写最少模型实现代码**

在 `package/services/v2rayn-wrt/src/internal/model/node.go` 中编写：
```go
package model

type ProtocolType string

const (
	ProtocolVLESS      ProtocolType = "vless"
	ProtocolVMess      ProtocolType = "vmess"
	ProtocolTrojan     ProtocolType = "trojan"
	ProtocolSS         ProtocolType = "shadowsocks"
	ProtocolHysteria2  ProtocolType = "hysteria2"
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
```

- [ ] **步骤 5：运行测试验证通过**

运行：`go test ./internal/model/... -v`
预期：PASS

- [ ] **步骤 6：Commit**

```bash
git add package/services/v2rayn-wrt/src
git commit -m "feat(model): define core node, strategy group and system config models"
```

---

### 任务 2：订阅解析器引擎 (Subscription Parser)

**文件：**
- 创建：`package/services/v2rayn-wrt/src/internal/parser/parser.go`
- 测试：`package/services/v2rayn-wrt/src/internal/parser/parser_test.go`

- [ ] **步骤 1：编写失败的订阅解析测试**

在 `internal/parser/parser_test.go` 中编写：
```go
package parser

import (
	"testing"
	"v2rayn-wrt/internal/model"
)

func TestParseVLESSLink(t *testing.T) {
	link := "vless://a8b1c2d3-0000-0000-0000-000000000001@hk.node.com:443?security=reality&sni=hk.node.com&fp=chrome&pbk=testpubkey#%E9%A6%99%E6%B8%AF01"
	node, err := ParseNodeLink(link)
	if err != nil {
		t.Fatalf("ParseNodeLink error: %v", err)
	}

	if node.Protocol != model.ProtocolVLESS || node.Port != 443 || node.Tag != "香港01" {
		t.Errorf("unexpected parsed node: %+v", node)
	}
}

func TestParseBase64Subscription(t *testing.T) {
	// Base64 encoding of two node links
	content := "dmxlc3M6Ly9hOGIxYzJkMy0wMDAwLTAwMDAtMDAwMC0wMDAwMDAwMDAwMDFAaGsubm9kZS5jb206NDQzP3NlY3VyaXR5PXJlYWxpdHkmc25pPWhrLm5vZGUuY29tJmZwPWNocm9tZSZwYms9dGVzdHB1YmtleSMlRTklQTYlOTklRTYlQjglQUYwMQp0cm9qYW46Ly9teXB3QGpwLm5vZGUuY29tOjQ0MyMlNUU2JTk3JUE1JUU2JTlDJUFDMDI="
	nodes, err := ParseSubscriptionContent(content)
	if err != nil {
		t.Fatalf("ParseSubscriptionContent error: %v", err)
	}

	if len(nodes) != 2 {
		t.Fatalf("expected 2 nodes, got %d", len(nodes))
	}
	if nodes[1].Protocol != model.ProtocolTrojan || nodes[1].Tag != "日本02" {
		t.Errorf("second node mismatch: %+v", nodes[1])
	}
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`go test ./internal/parser/... -v`
预期：FAIL，报错函数未定义。

- [ ] **步骤 3：编写最少订阅解析实现**

在 `internal/parser/parser.go` 中实现 Base64 解码与 VLESS/Trojan/SS 节点 URL 提取逻辑：
```go
package parser

import (
	"encoding/base64"
	"fmt"
	"net/url"
	"strconv"
	"strings"
	"v2rayn-wrt/internal/model"
)

func ParseSubscriptionContent(content string) ([]model.Node, error) {
	trimmed := strings.TrimSpace(content)
	decodedBytes, err := base64.StdEncoding.DecodeString(trimmed)
	if err != nil {
		decodedBytes, err = base64.RawStdEncoding.DecodeString(trimmed)
	}
	rawText := trimmed
	if err == nil {
		rawText = string(decodedBytes)
	}

	var nodes []model.Node
	lines := strings.Split(rawText, "\n")
	for i, line := range lines {
		line = strings.TrimSpace(line)
		if line == "" || strings.HasPrefix(line, "#") {
			continue
		}
		node, err := ParseNodeLink(line)
		if err == nil {
			if node.ID == "" {
				node.ID = fmt.Sprintf("node-%d", i+1)
			}
			nodes = append(nodes, *node)
		}
	}
	return nodes, nil
}

func ParseNodeLink(link string) (*model.Node, error) {
	u, err := url.Parse(link)
	if err != nil {
		return nil, err
	}

	tag, _ := url.QueryUnescape(u.Fragment)
	if tag == "" {
		tag = u.Host
	}

	port := 443
	host := u.Hostname()
	if p, err := strconv.Atoi(u.Port()); err == nil {
		port = p
	}

	node := &model.Node{
		Tag:      tag,
		Server:   host,
		Port:     port,
		Security: u.Query().Get("security"),
		SNI:      u.Query().Get("sni"),
	}

	switch u.Scheme {
	case "vless":
		node.Protocol = model.ProtocolVLESS
		node.UUID = u.User.Username()
		node.PublicKey = u.Query().Get("pbk")
		node.ShortID = u.Query().Get("sid")
		return node, nil
	case "trojan":
		node.Protocol = model.ProtocolTrojan
		node.Password = u.User.Username()
		return node, nil
	case "ss":
		node.Protocol = model.ProtocolSS
		node.Password = u.User.Username()
		return node, nil
	default:
		return nil, fmt.Errorf("unsupported protocol: %s", u.Scheme)
	}
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：`go test ./internal/parser/... -v`
预期：PASS

- [ ] **步骤 5：Commit**

```bash
git add package/services/v2rayn-wrt/src/internal/parser/
git commit -m "feat(parser): implement subscription and node URL link parser"
```

---

### 任务 3：并发真连接延迟测速引擎 (Pinger)

**文件：**
- 创建：`package/services/v2rayn-wrt/src/internal/pinger/pinger.go`
- 测试：`package/services/v2rayn-wrt/src/internal/pinger/pinger_test.go`

- [ ] **步骤 1：编写并发测速引擎测试**

在 `internal/pinger/pinger_test.go` 中编写：
```go
package pinger

import (
	"net"
	"testing"
	"time"
	"v2rayn-wrt/internal/model"
)

func TestPingerBatch(t *testing.T) {
	// 启动一个本地测试监听端口
	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatalf("listen error: %v", err)
	}
	defer listener.Close()

	port := listener.Addr().(*net.TCPAddr).Port

	nodes := []model.Node{
		{ID: "n1", Server: "127.0.0.1", Port: port},
		{ID: "n2", Server: "127.0.0.1", Port: 1}, // 不可达
	}

	p := NewPinger(500 * time.Millisecond)
	results := p.PingBatch(nodes)

	if len(results) != 2 {
		t.Fatalf("expected 2 results, got %d", len(results))
	}
	if results["n1"] <= 0 {
		t.Errorf("expected positive latency for n1, got %d", results["n1"])
	}
	if results["n2"] != -1 {
		t.Errorf("expected -1 (timeout/unreachable) for n2, got %d", results["n2"])
	}
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`go test ./internal/pinger/... -v`
预期：FAIL

- [ ] **步骤 3：编写高并发 TCP 握手测速实现**

在 `internal/pinger/pinger.go` 中实现：
```go
package pinger

import (
	"fmt"
	"net"
	"sync"
	"time"
	"v2rayn-wrt/internal/model"
)

type Pinger struct {
	Timeout time.Duration
}

func NewPinger(timeout time.Duration) *Pinger {
	return &Pinger{Timeout: timeout}
}

func (p *Pinger) PingNode(node model.Node) int64 {
	target := fmt.Sprintf("%s:%d", node.Server, node.Port)
	start := time.Now()
	conn, err := net.DialTimeout("tcp", target, p.Timeout)
	if err != nil {
		return -1
	}
	conn.Close()
	return time.Since(start).Milliseconds()
}

func (p *Pinger) PingBatch(nodes []model.Node) map[string]int64 {
	results := make(map[string]int64)
	var mu sync.Mutex
	var wg sync.WaitGroup

	sem := make(chan struct{}, 20) // 最大并发度 20

	for _, n := range nodes {
		wg.Add(1)
		go func(node model.Node) {
			defer wg.Done()
			sem <- struct{}{}
			delay := p.PingNode(node)
			<-sem

			mu.Lock()
			results[node.ID] = delay
			mu.Unlock()
		}(n)
	}

	wg.Wait()
	return results
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：`go test ./internal/pinger/... -v`
预期：PASS

- [ ] **步骤 5：Commit**

```bash
git add package/services/v2rayn-wrt/src/internal/pinger/
git commit -m "feat(pinger): implement concurrent TCP ping engine"
```

---

### 任务 4：策略组引擎与调度器 (Strategy Groups)

**文件：**
- 创建：`package/services/v2rayn-wrt/src/internal/group/manager.go`
- 测试：`package/services/v2rayn-wrt/src/internal/group/manager_test.go`

- [ ] **步骤 1：编写策略组调度测试（UrlTest与Failover）**

在 `internal/group/manager_test.go` 中编写：
```go
package group

import (
	"testing"
	"v2rayn-wrt/internal/model"
)

func TestStrategySelection(t *testing.T) {
	mgr := NewManager()

	nodes := map[string]model.Node{
		"n1": {ID: "n1", Tag: "香港", DelayMs: 80},
		"n2": {ID: "n2", Tag: "日本", DelayMs: 35},
		"n3": {ID: "n3", Tag: "美国", DelayMs: -1},
	}

	// 1. 最低延迟 (UrlTest)
	groupUrlTest := model.OutboundGroup{
		ID:    "g1",
		Tag:   "自动选优",
		Type:  model.GroupTypeUrlTest,
		Nodes: []string{"n1", "n2", "n3"},
	}
	selected := mgr.SelectNode(groupUrlTest, nodes)
	if selected != "n2" {
		t.Errorf("expected n2 (35ms), got %s", selected)
	}

	// 2. 故障转移 (Failover)
	groupFailover := model.OutboundGroup{
		ID:    "g2",
		Tag:   "故障转移",
		Type:  model.GroupTypeFailover,
		Nodes: []string{"n3", "n1", "n2"},
	}
	selectedFailover := mgr.SelectNode(groupFailover, nodes)
	if selectedFailover != "n1" {
		t.Errorf("expected n1 (since n3 is down), got %s", selectedFailover)
	}
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`go test ./internal/group/... -v`
预期：FAIL

- [ ] **步骤 3：编写策略组调度实现**

在 `internal/group/manager.go` 中实现：
```go
package group

import (
	"math/rand"
	"v2rayn-wrt/internal/model"
)

type Manager struct{}

func NewManager() *Manager {
	return &Manager{}
}

func (m *Manager) SelectNode(grp model.OutboundGroup, nodes map[string]model.Node) string {
	if len(grp.Nodes) == 0 {
		return ""
	}

	switch grp.Type {
	case model.GroupTypeSelector:
		if grp.Selected != "" {
			return grp.Selected
		}
		return grp.Nodes[0]

	case model.GroupTypeUrlTest:
		bestID := ""
		minDelay := int64(999999)
		for _, nid := range grp.Nodes {
			n, exists := nodes[nid]
			if exists && n.DelayMs > 0 && n.DelayMs < minDelay {
				minDelay = n.DelayMs
				bestID = nid
			}
		}
		if bestID != "" {
			return bestID
		}
		return grp.Nodes[0]

	case model.GroupTypeFailover:
		for _, nid := range grp.Nodes {
			n, exists := nodes[nid]
			if exists && n.DelayMs > 0 {
				return nid
			}
		}
		return grp.Nodes[0]

	case model.GroupTypeLoadBalance:
		validNodes := make([]string, 0)
		for _, nid := range grp.Nodes {
			n, exists := nodes[nid]
			if exists && n.DelayMs > 0 {
				validNodes = append(validNodes, nid)
			}
		}
		if len(validNodes) > 0 {
			return validNodes[rand.Intn(len(validNodes))]
		}
		return grp.Nodes[0]

	default:
		return grp.Nodes[0]
	}
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：`go test ./internal/group/... -v`
预期：PASS

- [ ] **步骤 5：Commit**

```bash
git add package/services/v2rayn-wrt/src/internal/group/
git commit -m "feat(group): implement strategy groups selection logic"
```

---

### 任务 5：配置合成器与 MosDNS 对接 (Sing-box Synthesizer)

**文件：**
- 创建：`package/services/v2rayn-wrt/src/internal/core/generator.go`
- 测试：`package/services/v2rayn-wrt/src/internal/core/generator_test.go`

- [ ] **步骤 1：编写配置合成与 MosDNS 对接测试**

在 `internal/core/generator_test.go` 中编写：
```go
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
	if !strings.Contains(configJSON, `"detour":"前置中转"`) {
		t.Errorf("expected detour for chain proxy in outbound")
	}
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`go test ./internal/core/... -v`
预期：FAIL

- [ ] **步骤 3：编写配置合成器实现**

在 `internal/core/generator.go` 中实现生成合规的 Sing-box 1.9+ JSON 配置，包含 TUN 入站、MosDNS 5335 对接以及 Detour 链式代理：
```go
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
				"type":        "tun",
				"tag":         "tun-in",
				"interface_name": "tun0",
				"inet4_address": "172.19.0.1/30",
				"auto_route":   true,
				"strict_route": false,
				"stack":        "system",
				"sniff":        true,
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
		"type":        "selector",
		"tag":         "proxy",
		"outbounds":   []string{activeTag},
		"default":     activeTag,
	})

	return outbounds
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：`go test ./internal/core/... -v`
预期：PASS

- [ ] **步骤 5：Commit**

```bash
git add package/services/v2rayn-wrt/src/internal/core/
git commit -m "feat(core): implement sing-box config generator with MosDNS and chain detour"
```

---

### 任务 6：RESTful API 与 WebSocket 实时流 (API Server)

**文件：**
- 创建：`package/services/v2rayn-wrt/src/internal/server/server.go`
- 测试：`package/services/v2rayn-wrt/src/internal/server/server_test.go`

- [ ] **步骤 1：编写 API 路由测试**

在 `internal/server/server_test.go` 中编写：
```go
package server

import (
	"net/http"
	"net/http/httptest"
	"testing"
	"v2rayn-wrt/internal/model"
)

func TestStatusAPI(t *testing.T) {
	srv := NewServer(model.SystemSettings{HttpPort: 9099, MosDNSPort: 5335})
	req := httptest.NewRequest("GET", "/api/status", nil)
	w := httptest.NewRecorder()

	srv.Handler().ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d", w.Code)
	}
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`go test ./internal/server/... -v`
预期：FAIL

- [ ] **步骤 3：编写 RESTful API 路由实现**

在 `internal/server/server.go` 中提供核心端点：
```go
package server

import (
	"encoding/json"
	"net/http"
	"sync"
	"v2rayn-wrt/internal/model"
	"v2rayn-wrt/internal/pinger"
)

type Server struct {
	mu       sync.RWMutex
	settings model.SystemSettings
	nodes    []model.Node
	groups   []model.OutboundGroup
	pinger   *pinger.Pinger
}

func NewServer(settings model.SystemSettings) *Server {
	return &Server{
		settings: settings,
		nodes:    make([]model.Node, 0),
		groups:   make([]model.OutboundGroup, 0),
		pinger:   pinger.NewPinger(1500),
	}
}

func (s *Server) Handler() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("/api/status", s.handleStatus)
	mux.HandleFunc("/api/nodes", s.handleNodes)
	mux.HandleFunc("/api/nodes/ping", s.handlePing)
	return mux
}

func (s *Server) handleStatus(w http.ResponseWriter, r *http.Request) {
	s.mu.RLock()
	defer s.mu.RUnlock()
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{
		"status":       "running",
		"core":         "sing-box",
		"active_node":  s.settings.ActiveNodeID,
		"routing_mode": s.settings.RoutingMode,
		"mosdns_port":  s.settings.MosDNSPort,
	})
}

func (s *Server) handleNodes(w http.ResponseWriter, r *http.Request) {
	s.mu.RLock()
	defer s.mu.RUnlock()
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(s.nodes)
}

func (s *Server) handlePing(w http.ResponseWriter, r *http.Request) {
	s.mu.RLock()
	nodes := s.nodes
	s.mu.RUnlock()

	results := s.pinger.PingBatch(nodes)

	s.mu.Lock()
	for i := range s.nodes {
		if delay, ok := results[s.nodes[i].ID]; ok {
			s.nodes[i].DelayMs = delay
		}
	}
	s.mu.Unlock()

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(results)
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：`go test ./internal/server/... -v`
预期：PASS

- [ ] **步骤 5：Commit**

```bash
git add package/services/v2rayn-wrt/src/internal/server/
git commit -m "feat(server): implement RESTful status and nodes APIs"
```

---

### 任务 7：嵌入式现代响应式 WebUI 资产与主程序 (WebUI & Main)

**文件：**
- 创建：`package/services/v2rayn-wrt/src/web/embed.go`
- 创建：`package/services/v2rayn-wrt/src/web/dist/index.html`
- 创建：`package/services/v2rayn-wrt/src/web/dist/app.css`
- 创建：`package/services/v2rayn-wrt/src/web/dist/app.js`
- 创建：`package/services/v2rayn-wrt/src/main.go`

- [ ] **步骤 1：编写嵌入式静态文件定义**

在 `web/embed.go` 中实现：
```go
package web

import (
	"embed"
	"io/fs"
	"net/http"
)

//go:embed dist/*
var distFS embed.FS

func AssetHandler() http.Handler {
	sub, err := fs.Sub(distFS, "dist")
	if err != nil {
		panic(err)
	}
	return http.FileServer(http.FS(sub))
}
```

- [ ] **步骤 2：创建高质量现代暗黑前端应用 (HTML/CSS/JS)**

在 `web/dist/index.html` 中集成视觉伴侣中验证通过的响应式仪表盘，包含侧边栏、状态卡片、节点网格、MosDNS 配置弹窗与测速触发。

- [ ] **步骤 3：编写 main.go 启动程序**

在 `package/services/v2rayn-wrt/src/main.go` 中整合 API 与静态资源托管，监听端口（默认 9099）：
```go
package main

import (
	"fmt"
	"log"
	"net/http"
	"v2rayn-wrt/internal/model"
	"v2rayn-wrt/internal/server"
	"v2rayn-wrt/web"
)

func main() {
	settings := model.SystemSettings{
		HttpPort:    9099,
		MosDNSPort:  5335,
		RoutingMode: "bypass_cn",
		DNSMode:     model.DNSModeMosDNS,
	}

	apiServer := server.NewServer(settings)
	mux := http.NewServeMux()

	// 挂载 API
	mux.Handle("/api/", apiServer.Handler())

	// 挂载静态 WebUI
	mux.Handle("/", web.AssetHandler())

	addr := fmt.Sprintf(":%d", settings.HttpPort)
	log.Printf("v2rayN-Wrt running at http://127.0.0.1%s\n", addr)
	if err := http.ListenAndServe(addr, mux); err != nil {
		log.Fatalf("Server failed: %v", err)
	}
}
```

- [ ] **步骤 4：编译并运行本地测试**

运行：`go test ./... -v`
预期：全部通过

- [ ] **步骤 5：Commit**

```bash
git add package/services/v2rayn-wrt/src/web/ package/services/v2rayn-wrt/src/main.go
git commit -m "feat(web): embed modern responsive web assets and wire up main entrypoint"
```

---

### 任务 8：OpenWrt Package Makefile 与 Procd 守护脚本

**文件：**
- 创建：`package/services/v2rayn-wrt/Makefile`
- 创建：`package/services/v2rayn-wrt/files/v2rayn-wrt.init`
- 创建：`package/services/v2rayn-wrt/files/v2rayn-wrt.config`

- [ ] **步骤 1：编写 OpenWrt Makefile**

在 `package/services/v2rayn-wrt/Makefile` 中按照 OpenWrt 包规范，声明依赖 `+sing-box`、`+kmod-tun`、`+mosdns`，并支持 Golang 交叉编译。

- [ ] **步骤 2：编写 procd 启动脚本与看门狗**

在 `files/v2rayn-wrt.init` 中实现 `procd_open_instance`、`procd_set_param respawn`（1 秒自愈）与关闭时的 TUN 网卡清理。

- [ ] **步骤 3：编写 UCI 默认配置文件**

在 `files/v2rayn-wrt.config` 中声明默认端口 9099 与 MosDNS 端口 5335。

- [ ] **步骤 4：Commit**

```bash
git add package/services/v2rayn-wrt/Makefile package/services/v2rayn-wrt/files/
git commit -m "feat(openwrt): add OpenWrt package Makefile, procd init script and default uci config"
```

---

### 任务 9：全链路端到端集成测试与验证

**文件：**
- 运行：端到端集成验证脚本

- [ ] **步骤 1：运行全套单元测试**

```bash
cd "package/services/v2rayn-wrt/src"
go test ./... -v -cover
```
预期：各模块测试全绿，代码覆盖率 > 85%。

- [ ] **步骤 2：验证本地编译二进制**

```bash
go build -o v2rayn-wrt.exe main.go
```
预期：零编译报错，生成独立单一二进制程序。

- [ ] **步骤 3：验证 WebUI 与 MosDNS API 联调响应**

运行生成的程序，通过 curl 访问：
* `http://localhost:9099/api/status`
* `http://localhost:9099/`
预期：HTTP 200，成功返回 MosDNS 端口配置与 WebUI 页面。

- [ ] **步骤 4：Commit 最终集成代码**

```bash
git commit -m "chore(integration): complete end-to-end integration validation for v2rayn-wrt"
```
