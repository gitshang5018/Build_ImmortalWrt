package server

import (
	"encoding/json"
	"net/http"
	"sync"
	"time"
	"v2rayn-wrt/internal/model"
	"v2rayn-wrt/internal/pinger"
)

type Server struct {
	mu       sync.RWMutex
	settings model.SystemSettings
	nodes    []model.Node
	groups   []model.OutboundGroup
	pinger   *pinger.Pinger
}

func NewServer(settings model.SystemSettings) *Server {
	return &Server{
		settings: settings,
		nodes:    make([]model.Node, 0),
		groups:   make([]model.OutboundGroup, 0),
		pinger:   pinger.NewPinger(1500 * time.Millisecond),
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
	return mux
}

func (s *Server) handleStatus(w http.ResponseWriter, r *http.Request) {
	s.mu.RLock()
	defer s.mu.RUnlock()
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{
		"status":       "running",
		"core":         "sing-box",
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
