package pinger

import (
	"fmt"
	"net"
	"sync"
	"time"
	"aerowrt/internal/model"
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
