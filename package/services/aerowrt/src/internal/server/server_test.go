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

func TestSettingsAPI(t *testing.T) {
	srv := NewServer(model.SystemSettings{HttpPort: 9099, MosDNSPort: 5335, RoutingMode: "bypass_cn"})

	// 1. GET /api/settings
	reqGet := httptest.NewRequest("GET", "/api/settings", nil)
	wGet := httptest.NewRecorder()
	srv.Handler().ServeHTTP(wGet, reqGet)
	if wGet.Code != http.StatusOK {
		t.Fatalf("expected 200 on GET /api/settings, got %d", wGet.Code)
	}

	var settings model.SystemSettings
	if err := json.NewDecoder(wGet.Body).Decode(&settings); err != nil {
		t.Fatalf("decode settings failed: %v", err)
	}
	if settings.MosDNSPort != 5335 || settings.RoutingMode != "bypass_cn" {
		t.Errorf("unexpected settings: %+v", settings)
	}

	// 2. POST /api/settings
	updatePayload := model.SystemSettings{
		RoutingMode:         "global",
		MosDNSPort:          5353,
		TestURL:             "https://cp.cloudflare.com/generate_204",
		StrategyMode:        "urltest",
		UrlTestIntervalMins: 5,
		AutoUpdateSubHours:  12,
		DirectDomains:       []string{"baidu.com", "qq.com"},
		ProxyDomains:        []string{"google.com"},
		DirectIPs:           []string{"119.29.29.29/32"},
		ProxyIPs:            []string{"1.1.1.1/32"},
	}
	body, _ := json.Marshal(updatePayload)
	reqPost := httptest.NewRequest("POST", "/api/settings", bytes.NewReader(body))
	wPost := httptest.NewRecorder()
	srv.Handler().ServeHTTP(wPost, reqPost)
	if wPost.Code != http.StatusOK {
		t.Fatalf("expected 200 on POST /api/settings, got %d", wPost.Code)
	}

	saved := srv.GetSettings()
	if saved.RoutingMode != "global" || saved.MosDNSPort != 5353 || saved.StrategyMode != "urltest" || len(saved.DirectDomains) != 2 {
		t.Errorf("settings not updated correctly: %+v", saved)
	}
}

func TestChainProxyAPI(t *testing.T) {
	srv := NewServer(model.SystemSettings{HttpPort: 9099})
	srv.AddNode(model.Node{ID: "node-transit", Tag: "中转跳板", Server: "transit.com", Port: 443, Protocol: model.ProtocolSS})
	srv.AddNode(model.Node{ID: "node-exit", Tag: "落地出口", Server: "exit.com", Port: 443, Protocol: model.ProtocolVLESS})
	srv.AddNode(model.Node{ID: "node-3", Tag: "三级节点", Server: "node3.com", Port: 443, Protocol: model.ProtocolTrojan})

	// 1. 设置正常链式代理: node-exit -> node-transit
	chainBody, _ := json.Marshal(map[string]string{
		"node_id":       "node-exit",
		"chain_node_id": "node-transit",
	})
	reqChain := httptest.NewRequest("POST", "/api/nodes/chain", bytes.NewReader(chainBody))
	wChain := httptest.NewRecorder()
	srv.Handler().ServeHTTP(wChain, reqChain)
	if wChain.Code != http.StatusOK {
		t.Fatalf("expected 200 on chain set, got %d: %s", wChain.Code, wChain.Body.String())
	}

	// 验证链式代理已保存
	nodes := srv.GetNodes()
	var exitNode *model.Node
	for _, n := range nodes {
		if n.ID == "node-exit" {
			exitNode = &n
			break
		}
	}
	if exitNode == nil || exitNode.ChainNode != "node-transit" {
		t.Fatalf("expected node-exit to have ChainNode node-transit, got %+v", exitNode)
	}

	// 2. 自身引用拒绝测试: node-exit -> node-exit
	selfBody, _ := json.Marshal(map[string]string{
		"node_id":       "node-exit",
		"chain_node_id": "node-exit",
	})
	reqSelf := httptest.NewRequest("POST", "/api/nodes/chain", bytes.NewReader(selfBody))
	wSelf := httptest.NewRecorder()
	srv.Handler().ServeHTTP(wSelf, reqSelf)
	if wSelf.Code != http.StatusBadRequest {
		t.Errorf("expected 400 on self chain, got %d", wSelf.Code)
	}

	// 3. 循环依赖检测测试:
	// 当前 node-exit -> node-transit
	// 尝试设置 node-transit -> node-exit，应该被循环检测拦截并返回 400
	loopBody, _ := json.Marshal(map[string]string{
		"node_id":       "node-transit",
		"chain_node_id": "node-exit",
	})
	reqLoop := httptest.NewRequest("POST", "/api/nodes/chain", bytes.NewReader(loopBody))
	wLoop := httptest.NewRecorder()
	srv.Handler().ServeHTTP(wLoop, reqLoop)
	if wLoop.Code != http.StatusBadRequest {
		t.Errorf("expected 400 on loop chain dependency, got %d: %s", wLoop.Code, wLoop.Body.String())
	}

	// 4. 清除链式代理: chain_node_id = ""
	clearBody, _ := json.Marshal(map[string]string{
		"node_id":       "node-exit",
		"chain_node_id": "",
	})
	reqClear := httptest.NewRequest("POST", "/api/nodes/chain", bytes.NewReader(clearBody))
	wClear := httptest.NewRecorder()
	srv.Handler().ServeHTTP(wClear, reqClear)
	if wClear.Code != http.StatusOK {
		t.Fatalf("expected 200 on clear chain, got %d", wClear.Code)
	}
	for _, n := range srv.GetNodes() {
		if n.ID == "node-exit" && n.ChainNode != "" {
			t.Errorf("expected chain node to be cleared, got %s", n.ChainNode)
		}
	}
}

func TestManualNodeAddEditAPI(t *testing.T) {
	srv := NewServer(model.SystemSettings{HttpPort: 9099})

	// 1. POST /api/nodes/add
	newNode := model.Node{
		Tag:       "手动 Reality",
		Protocol:  model.ProtocolVLESS,
		Server:    "vless.custom.com",
		Port:      443,
		UUID:      "test-uuid-456",
		Security:  "reality",
		PublicKey: "pubkey-123",
		ShortID:   "short-123",
	}
	addBody, _ := json.Marshal(newNode)
	reqAdd := httptest.NewRequest("POST", "/api/nodes/add", bytes.NewReader(addBody))
	wAdd := httptest.NewRecorder()
	srv.Handler().ServeHTTP(wAdd, reqAdd)
	if wAdd.Code != http.StatusOK {
		t.Fatalf("expected 200 on add node, got %d: %s", wAdd.Code, wAdd.Body.String())
	}

	var addResp struct {
		Success bool       `json:"success"`
		Node    model.Node `json:"node"`
	}
	_ = json.NewDecoder(wAdd.Body).Decode(&addResp)
	if !addResp.Success || addResp.Node.ID == "" {
		t.Fatalf("invalid add response: %+v", addResp)
	}

	// 2. POST /api/nodes/edit
	addResp.Node.Tag = "手动 Reality (已改名)"
	addResp.Node.Port = 8443
	editBody, _ := json.Marshal(addResp.Node)
	reqEdit := httptest.NewRequest("POST", "/api/nodes/edit", bytes.NewReader(editBody))
	wEdit := httptest.NewRecorder()
	srv.Handler().ServeHTTP(wEdit, reqEdit)
	if wEdit.Code != http.StatusOK {
		t.Fatalf("expected 200 on edit node, got %d: %s", wEdit.Code, wEdit.Body.String())
	}

	nodes := srv.GetNodes()
	if len(nodes) != 1 || nodes[0].Tag != "手动 Reality (已改名)" || nodes[0].Port != 8443 {
		t.Errorf("unexpected nodes after edit: %+v", nodes)
	}
}


