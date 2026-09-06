package server

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
	"aerowrt/internal/model"
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

func TestImportAndManageNodesAPI(t *testing.T) {
	srv := NewServer(model.SystemSettings{HttpPort: 9099})

	// 1. POST /api/nodes/import with direct link content
	rawVless := "vless://uuid-123@hk.server.com:443?security=reality&sni=yahoo.com&pbk=pubkey#HK-Reality"
	importBody, _ := json.Marshal(map[string]string{
		"content": rawVless,
	})
	reqImport := httptest.NewRequest("POST", "/api/nodes/import", bytes.NewReader(importBody))
	wImport := httptest.NewRecorder()
	srv.Handler().ServeHTTP(wImport, reqImport)

	if wImport.Code != http.StatusOK {
		t.Fatalf("expected 200 on import, got %d: %s", wImport.Code, wImport.Body.String())
	}

	var importResp map[string]interface{}
	if err := json.NewDecoder(wImport.Body).Decode(&importResp); err != nil {
		t.Fatalf("decode import failed: %v", err)
	}
	if importResp["imported"] != float64(1) {
		t.Errorf("expected 1 imported node, got %v", importResp["imported"])
	}

	// 2. GET /api/nodes
	reqNodes := httptest.NewRequest("GET", "/api/nodes", nil)
	wNodes := httptest.NewRecorder()
	srv.Handler().ServeHTTP(wNodes, reqNodes)

	var nodes []model.Node
	_ = json.NewDecoder(wNodes.Body).Decode(&nodes)
	if len(nodes) != 1 || nodes[0].Server != "hk.server.com" {
		t.Errorf("expected imported node in list, got %+v", nodes)
	}

	// 3. DELETE /api/nodes/delete
	delBody, _ := json.Marshal(map[string]string{
		"id": nodes[0].ID,
	})
	reqDel := httptest.NewRequest("POST", "/api/nodes/delete", bytes.NewReader(delBody))
	wDel := httptest.NewRecorder()
	srv.Handler().ServeHTTP(wDel, reqDel)

	if wDel.Code != http.StatusOK {
		t.Fatalf("expected 200 on delete, got %d", wDel.Code)
	}

	// 4. GET /api/logs
	reqLogs := httptest.NewRequest("GET", "/api/logs", nil)
	wLogs := httptest.NewRecorder()
	srv.Handler().ServeHTTP(wLogs, reqLogs)

	if wLogs.Code != http.StatusOK {
		t.Fatalf("expected 200 on logs, got %d", wLogs.Code)
	}
	var logsResp map[string][]string
	_ = json.NewDecoder(wLogs.Body).Decode(&logsResp)
	if len(logsResp["logs"]) == 0 {
		t.Errorf("expected logs to be non-empty")
	}
}

