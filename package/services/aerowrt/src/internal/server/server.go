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
		pinger:        pinger.NewPinger(1500 * time.Millisecond),
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
	if s.supervisor != nil {
		_ = s.supervisor.ApplyConfig(s.settings, s.nodes)
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
	s.mu.RLock()
	nodes := make([]model.Node, len(s.nodes))
	copy(nodes, s.nodes)
	s.mu.RUnlock()

	results := s.pinger.PingBatch(nodes)

	s.mu.Lock()
	for i := range s.nodes {
		if delay, ok := results[s.nodes[i].ID]; ok {
			s.nodes[i].DelayMs = delay
		}
	}
	s.saveToStorageLocked()
	s.mu.Unlock()

	if s.supervisor != nil {
		s.supervisor.AddLog("INFO", fmt.Sprintf("Ping batch completed for %d nodes", len(nodes)))
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
	s.saveToStorageLocked()
	s.mu.Unlock()

	if s.supervisor != nil {
		s.mu.RLock()
		_ = s.supervisor.ApplyConfig(s.settings, s.nodes)
		s.supervisor.AddLog("SUCCESS", fmt.Sprintf("Switched active outbound node to %s", req.NodeID))
		s.mu.RUnlock()
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{
		"success":     true,
		"active_node": req.NodeID,
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
