package pinger

import (
	"encoding/json"
	"fmt"
	"net"
	"net/http"
	"net/url"
	"sync"
	"time"
	"aerowrt/internal/model"
)

type Pinger struct {
	Timeout   time.Duration
	ClashAddr string // 例如 "127.0.0.1:9090"
	TestURL   string // 测速目标，如 "http://cp.cloudflare.com/generate_204"
}

func NewPinger(timeout time.Duration) *Pinger {
	return &Pinger{
		Timeout:   timeout,
		ClashAddr: "127.0.0.1:9090",
		TestURL:   "http://cp.cloudflare.com/generate_204",
	}
}

// PingNode 测速逻辑：
// 1. 优先通过 Sing-box 内核的 Clash API 进行真实代理握手与端到端访问 (URL-Test)；
// 2. 如果内核 API 未就绪或测速报错，回退到 TCP 握手直连测试。
func (p *Pinger) PingNode(node model.Node) int64 {
	// 尝试真实 URL-Test (通过代理到目标网站的往返握手延迟)
	if p.ClashAddr != "" && node.Tag != "" {
		if delay := p.pingViaClash(node.Tag); delay > 0 {
			return delay
		}
	}

	// 备选回退：TCP 端口连通性握手测试
	return p.tcpPing(node)
}

func (p *Pinger) pingViaClash(tag string) int64 {
	testURL := p.TestURL
	if testURL == "" {
		testURL = "http://cp.cloudflare.com/generate_204"
	}

	reqURL := fmt.Sprintf("http://%s/proxies/%s/delay?timeout=%d&url=%s",
		p.ClashAddr,
		url.PathEscape(tag),
		p.Timeout.Milliseconds(),
		url.QueryEscape(testURL),
	)

	client := http.Client{Timeout: p.Timeout + 500*time.Millisecond}
	resp, err := client.Get(reqURL)
	if err != nil {
		return -1
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return -1
	}

	var data struct {
		Delay int64 `json:"delay"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&data); err != nil || data.Delay <= 0 {
		return -1
	}
	return data.Delay
}

func (p *Pinger) tcpPing(node model.Node) int64 {
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
