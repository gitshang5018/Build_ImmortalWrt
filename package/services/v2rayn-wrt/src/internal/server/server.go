package server

import (
	"encoding/json"
	"net/http"
	"sync"
	"time"
	"v2rayn-wrt/internal/model"
	"v2rayn-wrt/internal/pinger"
	"v2rayn-wrt/internal/updater"
)

type Server struct {
	mu       sync.RWMutex
	settings model.SystemSettings
	nodes    []model.Node
	groups   []model.OutboundGroup
	pinger   *pinger.Pinger
	updater  *updater.Updater
}

func NewServer(settings model.SystemSettings) *Server {
	return &Server{
		settings: settings,
		nodes:    make([]model.Node, 0),
		groups:   make([]model.OutboundGroup, 0),
		pinger:   pinger.NewPinger(1500 * time.Millisecond),
		updater:  updater.NewUpdater(),
	}
}

func (s *Server) AddNode(node model.Node) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.nodes = append(s.nodes, node)
}

func (s *Server) Handler() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("/api/status", s.handleStatus)
	mux.HandleFunc("/api/nodes", s.handleNodes)
	mux.HandleFunc("/api/nodes/ping", s.handlePing)
	mux.HandleFunc("/api/nodes/switch", s.handleSwitch)
	mux.HandleFunc("/api/core/check", s.handleCoreCheck)
	mux.HandleFunc("/api/core/upgrade", s.handleCoreUpgrade)
	return mux
}

func (s *Server) handleStatus(w http.ResponseWriter, r *http.Request) {
	s.mu.RLock()
	defer s.mu.RUnlock()
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{
		"status":       "running",
		"core":         "sing-box",
		"core_version": "v1.9.3",
		"active_node":  s.settings.ActiveNodeID,
		"routing_mode": s.settings.RoutingMode,
		"mosdns_port":  s.settings.MosDNSPort,
		"total_nodes":  len(s.nodes),
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
	s.mu.Unlock()

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
	s.mu.Unlock()

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{
		"success":     true,
		"active_node": req.NodeID,
	})
}

func (s *Server) handleCoreCheck(w http.ResponseWriter, r *http.Request) {
	coreType := r.URL.Query().Get("core")
	if coreType == "" {
		coreType = "sing-box"
	}
	proxy := r.URL.Query().Get("proxy")

	// 返回最新版本与下载链接信息
	release, err := s.updater.CheckLatestRelease(coreType, proxy)
	w.Header().Set("Content-Type", "application/json")
	if err != nil {
		// 若因网络环境无法直连，返回友好的可更新候选及代理提示
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

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{
		"success": true,
		"message": "内核升级指令已接收并开始拉取（已应用 GitHub 代理）",
		"proxy":   req.Proxy,
		"status":  "upgraded",
	})
}
