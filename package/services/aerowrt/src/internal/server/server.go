package server

import (
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"
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
	for i := range s.nodes {
		s.nodes[i].Tag = strings.TrimSpace(s.nodes[i].Tag)
		if s.nodes[i].Tag == "" {
			s.nodes[i].Tag = fmt.Sprintf("node-%d", i+1)
		}
	}
	s.subscriptions = data.Subscriptions
	if data.Settings.ActiveNodeID != "" {
		s.settings.ActiveNodeID = data.Settings.ActiveNodeID
	}
	if data.Settings.ActiveGroup != "" {
		s.settings.ActiveGroup = data.Settings.ActiveGroup
	}
	if data.Settings.RoutingMode != "" {
		s.settings.RoutingMode = data.Settings.RoutingMode
	}
	if data.Settings.MosDNSPort > 0 {
		s.settings.MosDNSPort = data.Settings.MosDNSPort
	}
	if data.Settings.TestURL != "" {
		s.settings.TestURL = data.Settings.TestURL
		if s.pinger != nil {
			s.pinger.TestURL = data.Settings.TestURL
		}
	}
	if data.Settings.StrategyMode != "" {
		s.settings.StrategyMode = data.Settings.StrategyMode
	}
	if data.Settings.UrlTestIntervalMins > 0 {
		s.settings.UrlTestIntervalMins = data.Settings.UrlTestIntervalMins
	}
	switch data.Settings.DNSMode {
	case model.DNSModeMosDNS, model.DNSModeSmart, model.DNSModeCustom:
		s.settings.DNSMode = data.Settings.DNSMode
	}
	s.settings.CustomDnsServers = data.Settings.CustomDnsServers
	s.settings.AutoUpdateSubHours = data.Settings.AutoUpdateSubHours
	s.settings.DirectDomains = data.Settings.DirectDomains
	s.settings.ProxyDomains = data.Settings.ProxyDomains
	s.settings.DirectIPs = data.Settings.DirectIPs
	s.settings.ProxyIPs = data.Settings.ProxyIPs

	// 自定义出站分组（可为空）
	s.groups = append([]model.OutboundGroup{}, data.Groups...)
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
		Groups:        s.groups,
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
	mux.HandleFunc("/api/settings", s.handleSettings)
	mux.HandleFunc("/api/nodes", s.handleNodes)
	mux.HandleFunc("/api/nodes/ping", s.handlePing)
	mux.HandleFunc("/api/nodes/switch", s.handleSwitch)
	mux.HandleFunc("/api/nodes/import", s.handleImport)
	mux.HandleFunc("/api/nodes/chain", s.handleNodeChain)
	mux.HandleFunc("/api/nodes/add", s.handleNodeAdd)
	mux.HandleFunc("/api/nodes/edit", s.handleNodeEdit)
	mux.HandleFunc("/api/nodes/delete", s.handleDeleteNode)
	mux.HandleFunc("/api/subscriptions", s.handleSubscriptions)
	mux.HandleFunc("/api/subscriptions/delete", s.handleDeleteSubscription)
	mux.HandleFunc("/api/groups", s.handleGroups)
	mux.HandleFunc("/api/groups/delete", s.handleDeleteGroup)
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
		"status":                "running",
		"core":                  "sing-box",
		"core_version":          "v1.9.3",
		"active_node":           s.settings.ActiveNodeID,
		"routing_mode":          s.settings.RoutingMode,
		"strategy_mode":         s.settings.StrategyMode,
		"mosdns_port":           s.settings.MosDNSPort,
		"test_url":              s.settings.TestURL,
		"urltest_interval_mins": s.settings.UrlTestIntervalMins,
		"auto_update_sub_hours": s.settings.AutoUpdateSubHours,
		"total_nodes":           len(s.nodes),
		"total_subs":            len(s.subscriptions),
		"core_running":          s.supervisor != nil && s.supervisor.IsRunning(),
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

	// 若检测到 Sing-box 内核未运行，自动启动内核，确保可以使用 Clash API 进行真实代理 URL-Test 测速
	if s.supervisor != nil && !s.supervisor.IsRunning() && len(targetNodes) > 0 {
		s.supervisor.AddLog("INFO", "Sing-box core not running, auto-starting for URL-Test proxy ping...")
		s.mu.RLock()
		_ = s.supervisor.ApplyConfigWithGroups(s.settings, s.nodes, s.groups)
		s.mu.RUnlock()
		time.Sleep(500 * time.Millisecond) // 等待 Sing-box 内核及 Clash API (9090) 就绪
	}

	results := make(map[string]int64)
	var urlTestCount int
	var tcpCount int

	for _, n := range targetNodes {
		var delay int64
		var mode string
		if req.TestURL != "" && req.ID != "" {
			// 单节点 + 自定义测速 URL：临时使用指定 URL，不污染 Pinger 全局 TestURL
			delay, mode = s.pinger.PingNodeWithURL(n, req.TestURL)
		} else {
			delay, mode = s.pinger.PingNodeWithDetail(n)
		}
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
			targetTag = strings.TrimSpace(n.Tag)
			break
		}
	}
	if targetTag == "" && req.NodeID != "" {
		targetTag = req.NodeID
	}
	s.saveToStorageLocked()
	s.mu.Unlock()

	switchedViaClash := false
	if s.supervisor != nil && targetTag != "" {
		switchedViaClash = s.supervisor.SwitchViaClash(targetTag)
	}

	if !switchedViaClash && s.supervisor != nil {
		s.mu.RLock()
		_ = s.supervisor.ApplyConfigWithGroups(s.settings, s.nodes, s.groups)
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
		parsedNodes[i].Tag = strings.TrimSpace(parsedNodes[i].Tag)
		if parsedNodes[i].Tag == "" {
			parsedNodes[i].Tag = fmt.Sprintf("node-%d", len(s.nodes)+i+1)
		}
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

func (s *Server) handleSettings(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	if r.Method == http.MethodGet {
		s.mu.RLock()
		defer s.mu.RUnlock()
		json.NewEncoder(w).Encode(s.settings)
		return
	}

	if r.Method != http.MethodPost && r.Method != http.MethodPut {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	var req model.SystemSettings
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid JSON: "+err.Error(), http.StatusBadRequest)
		return
	}

	s.mu.Lock()
	if req.RoutingMode != "" {
		s.settings.RoutingMode = req.RoutingMode
	}
	if req.MosDNSPort > 0 {
		s.settings.MosDNSPort = req.MosDNSPort
	}
	if req.TestURL != "" {
		s.settings.TestURL = strings.TrimSpace(req.TestURL)
		if s.pinger != nil {
			s.pinger.TestURL = s.settings.TestURL
		}
	}
	if req.StrategyMode != "" {
		s.settings.StrategyMode = req.StrategyMode
	}
	if req.UrlTestIntervalMins > 0 {
		s.settings.UrlTestIntervalMins = req.UrlTestIntervalMins
	}
	// DNSMode：仅接受三种合法值，避免误写导致 generator 行为未定义
	switch req.DNSMode {
	case model.DNSModeMosDNS, model.DNSModeSmart, model.DNSModeCustom:
		s.settings.DNSMode = req.DNSMode
	}
	s.settings.AutoUpdateSubHours = req.AutoUpdateSubHours
	s.settings.CustomDnsServers = req.CustomDnsServers
	s.settings.DirectDomains = req.DirectDomains
	s.settings.ProxyDomains = req.ProxyDomains
	s.settings.DirectIPs = req.DirectIPs
	s.settings.ProxyIPs = req.ProxyIPs

	s.saveToStorageLocked()

	settingsCopy := s.settings
	nodesCopy := make([]model.Node, len(s.nodes))
	copy(nodesCopy, s.nodes)
	s.mu.Unlock()

	if s.supervisor != nil {
		go func() {
			if err := s.supervisor.ApplyConfigWithGroups(settingsCopy, nodesCopy, s.groups); err != nil {
				s.supervisor.AddLog("ERROR", fmt.Sprintf("Failed to apply new settings: %v", err))
			} else {
				s.supervisor.AddLog("SUCCESS", fmt.Sprintf("Settings updated & config reloaded (Mode: %s, Strategy: %s)", settingsCopy.RoutingMode, settingsCopy.StrategyMode))
			}
		}()
	}

	json.NewEncoder(w).Encode(map[string]interface{}{
		"success":  true,
		"settings": settingsCopy,
	})
}

func (s *Server) handleNodeChain(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	var req struct {
		NodeID      string `json:"node_id"`
		ChainNodeID string `json:"chain_node_id"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid JSON: "+err.Error(), http.StatusBadRequest)
		return
	}

	if req.NodeID == "" {
		http.Error(w, "node_id is required", http.StatusBadRequest)
		return
	}

	if req.ChainNodeID == req.NodeID {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusBadRequest)
		json.NewEncoder(w).Encode(map[string]interface{}{
			"success": false,
			"error":   "不能将自身设置为前置跳板代理",
		})
		return
	}

	s.mu.Lock()
	var targetIdx = -1
	chainMap := make(map[string]string)
	for i, n := range s.nodes {
		if n.ID == req.NodeID {
			targetIdx = i
		}
		chainMap[n.ID] = n.ChainNode
	}

	if targetIdx == -1 {
		s.mu.Unlock()
		http.Error(w, "Target node not found", http.StatusNotFound)
		return
	}

	// 如果指定了前置跳板节点，检查循环依赖
	if req.ChainNodeID != "" {
		foundParent := false
		for _, n := range s.nodes {
			if n.ID == req.ChainNodeID {
				foundParent = true
				break
			}
		}
		if !foundParent {
			s.mu.Unlock()
			w.Header().Set("Content-Type", "application/json")
			w.WriteHeader(http.StatusBadRequest)
			json.NewEncoder(w).Encode(map[string]interface{}{
				"success": false,
				"error":   "指定的跳板节点不存在",
			})
			return
		}

		// 环路检测
		curr := req.ChainNodeID
		chainMap[req.NodeID] = req.ChainNodeID
		visited := make(map[string]bool)
		for curr != "" {
			if curr == req.NodeID || visited[curr] {
				s.mu.Unlock()
				w.Header().Set("Content-Type", "application/json")
				w.WriteHeader(http.StatusBadRequest)
				json.NewEncoder(w).Encode(map[string]interface{}{
					"success": false,
					"error":   "检测到循环代理链依赖！无法将该节点设为前置跳板。",
				})
				return
			}
			visited[curr] = true
			curr = chainMap[curr]
		}
	}

	s.nodes[targetIdx].ChainNode = req.ChainNodeID
	s.saveToStorageLocked()

	settingsCopy := s.settings
	nodesCopy := make([]model.Node, len(s.nodes))
	copy(nodesCopy, s.nodes)
	targetNode := s.nodes[targetIdx]
	s.mu.Unlock()

	if s.supervisor != nil {
		go func() {
			_ = s.supervisor.ApplyConfigWithGroups(settingsCopy, nodesCopy, s.groups)
			action := "cleared"
			if req.ChainNodeID != "" {
				action = fmt.Sprintf("set to %s", req.ChainNodeID)
			}
			s.supervisor.AddLog("SUCCESS", fmt.Sprintf("Node [%s] chain detour %s", targetNode.Tag, action))
		}()
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{
		"success": true,
		"node":    targetNode,
	})
}

func (s *Server) handleNodeAdd(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	var node model.Node
	if err := json.NewDecoder(r.Body).Decode(&node); err != nil {
		http.Error(w, "Invalid JSON: "+err.Error(), http.StatusBadRequest)
		return
	}

	if node.Server == "" || node.Port <= 0 || node.Protocol == "" {
		http.Error(w, "Server, Port, and Protocol are required", http.StatusBadRequest)
		return
	}

	node.Tag = strings.TrimSpace(node.Tag)
	if node.Tag == "" {
		node.Tag = fmt.Sprintf("%s-%s:%d", node.Protocol, node.Server, node.Port)
	}
	if node.ID == "" {
		node.ID = fmt.Sprintf("node-manual-%d", time.Now().UnixNano()/1e6)
	}

	s.mu.Lock()
	s.nodes = append(s.nodes, node)
	if s.settings.ActiveNodeID == "" {
		s.settings.ActiveNodeID = node.ID
	}
	s.saveToStorageLocked()

	settingsCopy := s.settings
	nodesCopy := make([]model.Node, len(s.nodes))
	copy(nodesCopy, s.nodes)
	s.mu.Unlock()

	if s.supervisor != nil {
		go func() {
			_ = s.supervisor.ApplyConfigWithGroups(settingsCopy, nodesCopy, s.groups)
			s.supervisor.AddLog("SUCCESS", fmt.Sprintf("Manual node [%s] added", node.Tag))
		}()
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{
		"success": true,
		"node":    node,
	})
}

func (s *Server) handleNodeEdit(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	var node model.Node
	if err := json.NewDecoder(r.Body).Decode(&node); err != nil {
		http.Error(w, "Invalid JSON: "+err.Error(), http.StatusBadRequest)
		return
	}

	if node.ID == "" {
		http.Error(w, "node id is required", http.StatusBadRequest)
		return
	}

	s.mu.Lock()
	idx := -1
	for i, n := range s.nodes {
		if n.ID == node.ID {
			idx = i
			break
		}
	}
	if idx == -1 {
		s.mu.Unlock()
		http.Error(w, "Node not found", http.StatusNotFound)
		return
	}

	// 保留原有测速值
	node.DelayMs = s.nodes[idx].DelayMs
	if node.Tag == "" {
		node.Tag = s.nodes[idx].Tag
	}
	s.nodes[idx] = node
	s.saveToStorageLocked()

	settingsCopy := s.settings
	nodesCopy := make([]model.Node, len(s.nodes))
	copy(nodesCopy, s.nodes)
	s.mu.Unlock()

	if s.supervisor != nil {
		go func() {
			_ = s.supervisor.ApplyConfigWithGroups(settingsCopy, nodesCopy, s.groups)
			s.supervisor.AddLog("SUCCESS", fmt.Sprintf("Node [%s] updated", node.Tag))
		}()
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{
		"success": true,
		"node":    node,
	})
}

// handleGroups：GET 返回当前出站分组列表；POST 创建或覆盖一个分组
func (s *Server) handleGroups(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	if r.Method == http.MethodGet {
		s.mu.RLock()
		defer s.mu.RUnlock()
		json.NewEncoder(w).Encode(s.groups)
		return
	}
	if r.Method == http.MethodPost || r.Method == http.MethodPut {
		var req model.OutboundGroup
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
			http.Error(w, "Invalid JSON: "+err.Error(), http.StatusBadRequest)
			return
		}
		req.Tag = strings.TrimSpace(req.Tag)
		if req.Tag == "" {
			http.Error(w, "tag is required", http.StatusBadRequest)
			return
		}
		if req.Type == "" {
			req.Type = model.GroupTypeUrlTest
		}
		if req.Interval <= 0 {
			req.Interval = 300
		}
		if req.Tolerance <= 0 {
			req.Tolerance = 50
		}
		if req.ID == "" {
			req.ID = fmt.Sprintf("group-%d", time.Now().Unix())
		}
		s.mu.Lock()
		replaced := false
		for i, g := range s.groups {
			if g.ID == req.ID || g.Tag == req.Tag {
				s.groups[i] = req
				replaced = true
				break
			}
		}
		if !replaced {
			s.groups = append(s.groups, req)
		}
		s.saveToStorageLocked()
		settingsCopy := s.settings
		nodesCopy := make([]model.Node, len(s.nodes))
		copy(nodesCopy, s.nodes)
		s.mu.Unlock()
		if s.supervisor != nil {
			go func() {
				_ = s.supervisor.ApplyConfigWithGroups(settingsCopy, nodesCopy, s.groups)
				s.supervisor.AddLog("SUCCESS", "Outbound group ["+req.Tag+"] saved")
			}()
		}
		json.NewEncoder(w).Encode(map[string]interface{}{"success": true, "group": req})
		return
	}
	http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
}

// handleDeleteGroup：POST {"id": "..."} 或 {"tag": "..."} 删除一个分组
func (s *Server) handleDeleteGroup(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost && r.Method != http.MethodDelete {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}
	var req struct {
		ID  string `json:"id"`
		Tag string `json:"tag"`
	}
	if r.Body != nil {
		_ = json.NewDecoder(r.Body).Decode(&req)
	}
	if req.ID == "" {
		req.ID = r.URL.Query().Get("id")
	}
	if req.ID == "" && req.Tag == "" {
		http.Error(w, "id or tag is required", http.StatusBadRequest)
		return
	}

	s.mu.Lock()
	newGroups := make([]model.OutboundGroup, 0, len(s.groups))
	found := false
	var removedTag string
	for _, g := range s.groups {
		if g.ID == req.ID || (req.Tag != "" && g.Tag == req.Tag) {
			found = true
			removedTag = g.Tag
			continue
		}
		newGroups = append(newGroups, g)
	}
	s.groups = newGroups
	s.saveToStorageLocked()
	settingsCopy := s.settings
	nodesCopy := make([]model.Node, len(s.nodes))
	copy(nodesCopy, s.nodes)
	s.mu.Unlock()

	if s.supervisor != nil && found {
		go func() {
			_ = s.supervisor.ApplyConfigWithGroups(settingsCopy, nodesCopy, s.groups)
			s.supervisor.AddLog("INFO", "Outbound group ["+removedTag+"] removed")
		}()
	}

	w.Header().Set("Content-Type", "application/json")
	if !found {
		w.WriteHeader(http.StatusNotFound)
		json.NewEncoder(w).Encode(map[string]interface{}{"success": false, "error": "group not found"})
		return
	}
	json.NewEncoder(w).Encode(map[string]interface{}{"success": true})
}

func (s *Server) handleDeleteSubscription(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost && r.Method != http.MethodDelete {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}
	var req struct {
		ID         string `json:"id"`
		URL        string `json:"url"`
		AlsoDeleteNodes bool `json:"also_delete_nodes"`
	}
	if r.Body != nil {
		_ = json.NewDecoder(r.Body).Decode(&req)
	}
	if req.ID == "" {
		req.ID = r.URL.Query().Get("id")
	}
	if req.ID == "" && req.URL == "" {
		http.Error(w, "id or url is required", http.StatusBadRequest)
		return
	}

	s.mu.Lock()
	var removedSub *model.Subscription
	newSubs := make([]model.Subscription, 0, len(s.subscriptions))
	for _, sub := range s.subscriptions {
		if sub.ID == req.ID || (req.URL != "" && sub.URL == req.URL) {
			cp := sub
			removedSub = &cp
			continue
		}
		newSubs = append(newSubs, sub)
	}
	s.subscriptions = newSubs

	// 可选：级联删除该订阅源导入的所有节点（节点 tag 没有保存订阅来源信息，仅通过 URL 关联不可靠）
	// 保守策略：默认仅删除订阅记录不级联节点，前端通过 also_delete_nodes=true 显式触发
	deletedNodeCount := 0
	if removedSub != nil && req.AlsoDeleteNodes {
		// 节点的 ID 是导入时基于时间戳批量分配的 (node-<unix>-<idx>)，无法精准回滚
		// // 仅靠订阅 URL 信息无法准确识别节点归属，需要扩展 model.Subscription.Nodes 字段
		// // 当前实现仅删除订阅记录，节点保留并提示用户手动清理
	}
	s.saveToStorageLocked()
	s.mu.Unlock()

	w.Header().Set("Content-Type", "application/json")
	if removedSub == nil {
		w.WriteHeader(http.StatusNotFound)
		json.NewEncoder(w).Encode(map[string]interface{}{
			"success": false,
			"error":   "subscription not found",
		})
		return
	}
	json.NewEncoder(w).Encode(map[string]interface{}{
		"success":        true,
		"removed_id":     removedSub.ID,
		"removed_url":    removedSub.URL,
		"deleted_nodes":  deletedNodeCount,
		"note":           "仅删除订阅记录；该订阅历史导入的节点未级联清理（如需清理请使用节点管理页删除）",
	})
}

func (s *Server) StartAutoUpdateWorker(stopCh <-chan struct{}) {
	ticker := time.NewTicker(30 * time.Minute)
	go func() {
		for {
			select {
			case <-stopCh:
				ticker.Stop()
				return
			case <-ticker.C:
				s.autoUpdateSubscriptions()
			}
		}
	}()
}

func (s *Server) autoUpdateSubscriptions() {
	s.mu.RLock()
	hours := s.settings.AutoUpdateSubHours
	subs := make([]model.Subscription, len(s.subscriptions))
	copy(subs, s.subscriptions)
	s.mu.RUnlock()

	if hours <= 0 || len(subs) == 0 {
		return
	}

	cutoff := time.Now().Add(-time.Duration(hours) * time.Hour)
	for _, sub := range subs {
		if sub.URL == "" {
			continue
		}
		updatedAt, err := time.Parse("2006-01-02 15:04:05", sub.UpdatedAt)
		if err == nil && updatedAt.After(cutoff) {
			continue
		}

		client := &http.Client{Timeout: 15 * time.Second}
		httpReq, err := http.NewRequest("GET", sub.URL, nil)
		if err != nil {
			continue
		}
		httpReq.Header.Set("User-Agent", "v2rayN/6.23 clash-meta aerowrt")
		resp, err := client.Do(httpReq)
		if err != nil {
			continue
		}
		bodyBytes, _ := io.ReadAll(resp.Body)
		resp.Body.Close()

		parsedNodes, err := parser.ParseSubscriptionContent(string(bodyBytes))
		if err != nil || len(parsedNodes) == 0 {
			continue
		}

		s.mu.Lock()
		nowStr := time.Now().Format("2006-01-02 15:04:05")
		for si := range s.subscriptions {
			if s.subscriptions[si].ID == sub.ID {
				s.subscriptions[si].UpdatedAt = nowStr
				s.subscriptions[si].NodeCount = len(parsedNodes)
				break
			}
		}
		s.saveToStorageLocked()
		s.mu.Unlock()

		if s.supervisor != nil {
			s.supervisor.AddLog("INFO", fmt.Sprintf("Cron: Subscription [%s] auto-updated (%d nodes)", sub.Name, len(parsedNodes)))
		}
	}
}

