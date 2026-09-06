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
	if results["n1"] < 0 {
		t.Errorf("expected non-negative latency for n1, got %d", results["n1"])
	}
	if results["n2"] != -1 {
		t.Errorf("expected -1 (timeout/unreachable) for n2, got %d", results["n2"])
	}
}
