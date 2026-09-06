package server

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
	"v2rayn-wrt/internal/model"
)

func TestStatusAPI(t *testing.T) {
	srv := NewServer(model.SystemSettings{HttpPort: 9099, MosDNSPort: 5335, RoutingMode: "bypass_cn"})
	req := httptest.NewRequest("GET", "/api/status", nil)
	w := httptest.NewRecorder()

	srv.Handler().ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d", w.Code)
	}

	var resp map[string]interface{}
	if err := json.NewDecoder(w.Body).Decode(&resp); err != nil {
		t.Fatalf("decode failed: %v", err)
	}

	if resp["mosdns_port"] != float64(5335) || resp["status"] != "running" {
		t.Errorf("unexpected status response: %+v", resp)
	}
}

func TestNodesAndPingAPI(t *testing.T) {
	srv := NewServer(model.SystemSettings{HttpPort: 9099, MosDNSPort: 5335})
	srv.AddNode(model.Node{ID: "n1", Tag: "测试节点", Server: "127.0.0.1", Port: 80})

	// 1. GET /api/nodes
	reqNodes := httptest.NewRequest("GET", "/api/nodes", nil)
	wNodes := httptest.NewRecorder()
	srv.Handler().ServeHTTP(wNodes, reqNodes)

	if wNodes.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d", wNodes.Code)
	}

	var nodes []model.Node
	if err := json.NewDecoder(wNodes.Body).Decode(&nodes); err != nil {
		t.Fatalf("decode nodes failed: %v", err)
	}
	if len(nodes) != 1 {
		t.Errorf("expected 1 node, got %d", len(nodes))
	}

	// 2. POST /api/nodes/ping
	reqPing := httptest.NewRequest("POST", "/api/nodes/ping", nil)
	wPing := httptest.NewRecorder()
	srv.Handler().ServeHTTP(wPing, reqPing)

	if wPing.Code != http.StatusOK {
		t.Fatalf("expected 200 on ping, got %d", wPing.Code)
	}
}
