package server

import (
	"bytes"
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

func TestCoreCheckAndUpgradeAPI(t *testing.T) {
	mockRelease := map[string]interface{}{
		"tag_name": "v1.9.4",
		"assets": []map[string]interface{}{
			{
				"name":                 "sing-box-1.9.4-linux-amd64.tar.gz",
				"browser_download_url": "https://github.com/SagerNet/sing-box/releases/download/v1.9.4/sing-box-1.9.4-linux-amd64.tar.gz",
			},
		},
	}
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		json.NewEncoder(w).Encode(mockRelease)
	}))
	defer ts.Close()

	srv := NewServer(model.SystemSettings{HttpPort: 9099})
	srv.updater.BaseURL = ts.URL

	// 1. GET /api/core/check
	reqCheck := httptest.NewRequest("GET", "/api/core/check?core=sing-box&proxy=https://ghproxy.net/", nil)
	wCheck := httptest.NewRecorder()
	srv.Handler().ServeHTTP(wCheck, reqCheck)

	if wCheck.Code != http.StatusOK {
		t.Fatalf("expected 200 on check, got %d", wCheck.Code)
	}

	var checkResp map[string]interface{}
	if err := json.NewDecoder(wCheck.Body).Decode(&checkResp); err != nil {
		t.Fatalf("decode check failed: %v", err)
	}
	if checkResp["latest_version"] != "v1.9.4" {
		t.Errorf("expected latest version v1.9.4, got %v", checkResp["latest_version"])
	}

	// 2. POST /api/core/upgrade
	body, _ := json.Marshal(map[string]string{
		"core":  "sing-box",
		"proxy": "https://ghproxy.net/",
	})
	reqUpgrade := httptest.NewRequest("POST", "/api/core/upgrade", bytes.NewReader(body))
	wUpgrade := httptest.NewRecorder()
	srv.Handler().ServeHTTP(wUpgrade, reqUpgrade)

	if wUpgrade.Code != http.StatusOK {
		t.Fatalf("expected 200 on upgrade, got %d", wUpgrade.Code)
	}
}
