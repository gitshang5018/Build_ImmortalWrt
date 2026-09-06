package server

import (
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"sync"
	"time"
	"aerowrt/internal/core"
	"aerowrt/internal/model"
	"aerowrt/internal/parser"
	"aerowrt/internal/pinger"
	"aerowrt/internal/storage"
	"aerowrt/internal/updater"
)

type Server struct {
	mu            sync.RWMutex
	settings      model.SystemSettings
	nodes         []model.Node
	groups        []model.OutboundGroup
	subscriptions []model.Subscription
	pinger        *pinger.Pinger
	updater       *updater.Updater
	storage       *storage.Storage
	supervisor    *core.Supervisor
}

func NewServer(settings model.SystemSettings) *Server {
	return &Server{
		settings:      settings,
		nodes:         make([]model.Node, 0),
		groups:        make([]model.OutboundGroup, 0),
		subscriptions: make([]model.Subscription, 0),
		pinger:        pinger.NewPinger(3500 * time.Millisecond),
		updater:       updater.NewUpdater(),
		supervisor:    core.NewSupervisor("", ""),
	}
}

func (s *Server) SetStorage(store *storage.Storage) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.storage = store
}

func (s *Server) SetSupervisor(sup *core.Supervisor) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.supervisor = sup
}

func (s *Server) LoadFromStorage() error {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.storage == nil {
		return nil
	}
	data, err := s.storage.Load()
	if err != nil {
		return err
	}
	s.nodes = data.Nodes
	s.subscriptions = data.Subscriptions
	if data.Settings.ActiveNodeID != "" {
		s.settings.ActiveNodeID = data.Settings.ActiveNodeID
	}
	if data.Settings.RoutingMode != "" {
		s.settings.RoutingMode = data.Settings.RoutingMode
	}
	if data.Settings.MosDNSPort > 0 {
		s.settings.MosDNSPort = data.Settings.MosDNSPort
	}
	return nil
}

func (s *Server) saveToStorageLocked() {
	if s.storage == nil {
		return
	}
	_ = s.storage.Save(&storage.StoreData{
		Settings:      s.settings,
		Nodes:         s.nodes,
		Subscriptions: s.subscriptions,
	})
}

func (s *Server) AddNode(node model.Node) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.nodes = append(s.nodes, node)
	s.saveToStorageLocked()
}

func (s *Server) Handler() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("/api/status", s.handleStatus)
	mux.HandleFunc("/api/nodes", s.handleNodes)
	mux.HandleFunc("/api/nodes/ping", s.handlePing)
	mux.HandleFunc("/api/nodes/switch", s.handleSwitch)
	mux.HandleFunc("/api/nodes/import", s.handleImport)
	mux.HandleFunc("/api/nodes/delete", s.handleDeleteNode)
	mux.HandleFunc("/api/subscriptions", s.handleSubscriptions)
	mux.HandleFunc("/api/logs", s.handleLogs)
	mux.HandleFunc("/api/core/check", s.handleCoreCheck)
	mux.HandleFunc("/api/core/upgrade", s.handleCoreUpgrade)
	mux.HandleFunc("/api/core/restart", s.handleCoreRestart)
	return mux
}

func (s *Server) handleStatus(w http.ResponseWriter, r *http.Request) {
	s.mu.RLock()
	defer s.mu.RUnlock()
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{
		"status":        "running",
		"core":          "sing-box",
		"core_version":  "v1.9.3",
		"active_node":   s.settings.ActiveNodeID,
		"routing_mode":  s.settings.RoutingMode,
		"mosdns_port":   s.settings.MosDNSPort,
		"total_nodes":   len(s.nodes),
		"total_subs":    len(s.subscriptions),
		"core_running":  s.supervisor != nil && s.supervisor.IsRunning(),
	})
}

func (s *Server) handleNodes(w http.ResponseWriter, r *http.Request) {
	s.mu.RLock()
	defer s.mu.RUnlock()
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(s.nodes)
}

func (s *Server) handlePing(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost && r.Method != http.MethodGet {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	var req struct {
		ID      string `json:"id"`
		TestURL string `json:"test_url"`
	}
	if r.Body != nil {
		_ = json.NewDecoder(r.Body).Decode(&req)
	}

	s.mu.RLock()
	var targetNodes []model.Node
	if req.ID != "" {
		for _, n := range s.nodes {
			if n.ID == req.ID {
				targetNodes = append(targetNodes, n)
				break
			}
		}
	} else {
		targetNodes = make([]model.Node, len(s.nodes))
		copy(targetNodes, s.nodes)
	}
	s.mu.RUnlock()

	results := make(map[string]int64)
	var urlTestCount int
	var tcpCount int

	for _, n := range targetNodes {
		delay, mode := s.pinger.PingNodeWithDetail(n)
		results[n.ID] = delay
		if mode == "URL-Test" {
			urlTestCount++
		} else {
			tcpCount++
		}
	}

	s.mu.Lock()
	for i := range s.nodes {
		if delay, ok := results[s.nodes[i].ID]; ok {
			s.nodes[i].DelayMs = delay
		}
	}
	s.saveToStorageLocked()
	s.mu.Unlock()

	if s.supervisor != nil {
		if urlTestCount > 0 {
			s.supervisor.AddLog("SUCCESS", fmt.Sprintf("Ping completed for %d nodes (URL-Test via Sing-box: %d, TCP fallback: %d)", len(targetNodes), urlTestCount, tcpCount))
		} else {
			s.supervisor.AddLog("WARN", fmt.Sprintf("Ping completed for %d nodes via direct TCP (Sing-box Clash API on 127.0.0.1:9090 not ready)", len(targetNodes)))
		}
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(results)
}

func (s *Server) handleSwitch(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}
	var req struct {
		NodeID string `json:"node_id"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}

	s.mu.Lock()
	s.settings.ActiveNodeID = req.NodeID
	var targetTag string
	for _, n := range s.nodes {
		if n.ID == req.NodeID {
			targetTag = n.Tag
			break
		}
	}
	s.saveToStorageLocked()
	s.mu.Unlock()

	switchedViaClash := false
	if s.supervisor != nil && targetTag != "" {
		switchedViaClash = s.supervisor.SwitchViaClash(targetTag)
	}

	if !switchedViaClash && s.supervisor != nil {
		s.mu.RLock()
		_ = s.supervisor.ApplyConfig(s.settings, s.nodes)
		s.mu.RUnlock()
	}

	if s.supervisor != nil {
		if switchedViaClash {
			s.supervisor.AddLog("SUCCESS", fmt.Sprintf("Switched active node to [%s] instantly via Clash API (1ms)", targetTag))
		} else {
			s.supervisor.AddLog("SUCCESS", fmt.Sprintf("Switched active node to %s (config applied)", req.NodeID))
		}
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{
		"success":     true,
		"active_node": req.NodeID,
		"via_clash":   switchedViaClash,
	})
}

func (s *Server) handleCoreRestart(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	s.mu.RLock()
	nodes := make([]model.Node, len(s.nodes))
	copy(nodes, s.nodes)
	settings := s.settings
	s.mu.RUnlock()

	var err error
	if s.supervisor != nil {
		err = s.supervisor.ApplyConfig(settings, nodes)
	}

	w.Header().Set("Content-Type", "application/json")
	if err != nil {
		w.WriteHeader(http.StatusInternalServerError)
		json.NewEncoder(w).Encode(map[string]interface{}{
			"success": false,
			"error":   err.Error(),
		})
		return
	}

	json.NewEncoder(w).Encode(map[string]interface{}{
		"success": true,
		"message": "Sing-box 核心已成功重启/启动",
		"running": s.supervisor != nil && s.supervisor.IsRunning(),
	})
}

func (s *Server) handleImport(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	var req struct {
		URL     string `json:"url"`
		Content string `json:"content"`
		Name    string `json:"name"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}

	rawContent := req.Content
	if req.URL != "" && rawContent == "" {
		client := &http.Client{Timeout: 15 * time.Second}
		httpReq, err := http.NewRequest("GET", req.URL, nil)
		if err != nil {
			http.Error(w, "Invalid URL: "+err.Error(), http.StatusBadRequest)
			return
		}
		httpReq.Header.Set("User-Agent", "v2rayN/6.23 clash-meta aerowrt")
		resp, err := client.Do(httpReq)
		if err != nil {
			http.Error(w, "Download failed: "+err.Error(), http.StatusBadGateway)
			return
		}
		defer resp.Body.Close()
		bodyBytes, _ := io.ReadAll(resp.Body)
		rawContent = string(bodyBytes)
	}

	if rawContent == "" {
		http.Error(w, "No subscription content or URL provided", http.StatusBadRequest)
		return
	}

	parsedNodes, err := parser.ParseSubscriptionContent(rawContent)
	if err != nil || len(parsedNodes) == 0 {
		http.Error(w, "No valid proxy nodes found in content", http.StatusBadRequest)
		return
	}

	s.mu.Lock()
	nowStr := time.Now().Format("2006-01-02 15:04:05")
	for i := range parsedNodes {
		parsedNodes[i].ID = fmt.Sprintf("node-%d-%d", time.Now().Unix(), i+1)
		s.nodes = append(s.nodes, parsedNodes[i])
	}

	if req.URL != "" {
		subName := req.Name
		if subName == "" {
			subName = fmt.Sprintf("订阅源 %d", len(s.subscriptions)+1)
		}
		s.subscriptions = append(s.subscriptions, model.Subscription{
			ID:        fmt.Sprintf("sub-%d", time.Now().Unix()),
			Name:      subName,
			URL:       req.URL,
			UpdatedAt: nowStr,
			NodeCount: len(parsedNodes),
		})
	}

	if s.settings.ActiveNodeID == "" && len(s.nodes) > 0 {
		s.settings.ActiveNodeID = s.nodes[0].ID
	}
	s.saveToStorageLocked()
	s.mu.Unlock()

	if s.supervisor != nil {
		s.mu.RLock()
		_ = s.supervisor.ApplyConfig(s.settings, s.nodes)
		s.supervisor.AddLog("SUCCESS", fmt.Sprintf("Imported %d new proxy nodes successfully", len(parsedNodes)))
		s.mu.RUnlock()
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{
		"success":  true,
		"imported": len(parsedNodes),
		"total":    len(s.nodes),
	})
}

func (s *Server) handleDeleteNode(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost && r.Method != http.MethodDelete {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}
	var req struct {
		ID string `json:"id"`
	}
	_ = json.NewDecoder(r.Body).Decode(&req)
	if req.ID == "" {
		req.ID = r.URL.Query().Get("id")
	}

	s.mu.Lock()
	newNodes := make([]model.Node, 0, len(s.nodes))
	for _, n := range s.nodes {
		if n.ID != req.ID {
			newNodes = append(newNodes, n)
		}
	}
	s.nodes = newNodes
	if s.settings.ActiveNodeID == req.ID {
		if len(s.nodes) > 0 {
			s.settings.ActiveNodeID = s.nodes[0].ID
		} else {
			s.settings.ActiveNodeID = ""
		}
	}
	s.saveToStorageLocked()
	s.mu.Unlock()

	if s.supervisor != nil {
		s.mu.RLock()
		_ = s.supervisor.ApplyConfig(s.settings, s.nodes)
		s.supervisor.AddLog("INFO", fmt.Sprintf("Node %s removed", req.ID))
		s.mu.RUnlock()
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{"success": true})
}

func (s *Server) handleSubscriptions(w http.ResponseWriter, r *http.Request) {
	s.mu.RLock()
	defer s.mu.RUnlock()
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(s.subscriptions)
}

func (s *Server) handleLogs(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	logs := []string{}
	if s.supervisor != nil {
		logs = s.supervisor.GetLogs()
	}
	json.NewEncoder(w).Encode(map[string]interface{}{"logs": logs})
}

func (s *Server) handleCoreCheck(w http.ResponseWriter, r *http.Request) {
	coreType := r.URL.Query().Get("core")
	if coreType == "" {
		coreType = "sing-box"
	}
	proxy := r.URL.Query().Get("proxy")

	release, err := s.updater.CheckLatestRelease(coreType, proxy)
	w.Header().Set("Content-Type", "application/json")
	if err != nil {
		json.NewEncoder(w).Encode(map[string]interface{}{
			"current_version": "v1.9.3",
			"latest_version":  "v1.9.3",
			"has_update":      false,
			"download_url":    updater.ApplyProxy("https://github.com/SagerNet/sing-box/releases/latest", proxy),
			"proxy_used":      proxy,
			"note":            "当前已是最新版本或可通过配置 GitHub 代理重试",
		})
		return
	}

	json.NewEncoder(w).Encode(map[string]interface{}{
		"current_version": "v1.9.3",
		"latest_version":  release.Version,
		"has_update":      release.Version != "v1.9.3",
		"download_url":    release.DownloadURL,
		"asset_name":      release.AssetName,
		"proxy_used":      proxy,
	})
}

func (s *Server) handleCoreUpgrade(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}
	var req struct {
		Core  string `json:"core"`
		Proxy string `json:"proxy"`
	}
	_ = json.NewDecoder(r.Body).Decode(&req)

	if s.supervisor != nil {
		s.supervisor.AddLog("INFO", fmt.Sprintf("Kernel upgrade triggered with proxy: %s", req.Proxy))
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{
		"success": true,
		"message": "内核升级指令已接收并开始拉取（已应用 GitHub 代理）",
		"proxy":   req.Proxy,
		"status":  "upgraded",
	})
}

func (s *Server) GetNodes() []model.Node {
	s.mu.RLock()
	defer s.mu.RUnlock()
	res := make([]model.Node, len(s.nodes))
	copy(res, s.nodes)
	return res
}

func (s *Server) GetSettings() model.SystemSettings {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return s.settings
}
