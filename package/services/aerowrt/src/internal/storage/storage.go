package storage

import (
	"encoding/json"
	"os"
	"path/filepath"
	"sync"
	"aerowrt/internal/model"
)

type StoreData struct {
	Settings      model.SystemSettings `json:"settings"`
	Nodes         []model.Node         `json:"nodes"`
	Subscriptions []model.Subscription `json:"subscriptions"`
}

type Storage struct {
	mu       sync.RWMutex
	filePath string
}

func NewStorage(filePath string) *Storage {
	return &Storage{
		filePath: filePath,
	}
}

func (s *Storage) DefaultData() *StoreData {
	return &StoreData{
		Settings: model.SystemSettings{
			ActiveNodeID: "node-hk",
			ActiveGroup:  "auto",
			RoutingMode:  "bypass_cn",
			DNSMode:      model.DNSModeMosDNS,
			MosDNSPort:   5335,
			HttpPort:     9099,
		},
		Nodes: []model.Node{
			{
				ID:       "node-hk",
				Tag:      "🇭🇰 香港 01 [BGP专线]",
				Protocol: model.ProtocolVLESS,
				Server:   "hk.node.com",
				Port:     443,
				Security: "reality",
				DelayMs:  28,
			},
			{
				ID:       "node-jp",
				Tag:      "🇯🇵 日本 02 [原生流媒体]",
				Protocol: model.ProtocolSS,
				Server:   "jp.node.com",
				Port:     8388,
				DelayMs:  52,
			},
			{
				ID:       "node-sg",
				Tag:      "🇸🇬 新加坡 01 [优质线路]",
				Protocol: model.ProtocolTrojan,
				Server:   "sg.node.com",
				Port:     443,
				DelayMs:  64,
			},
		},
		Subscriptions: []model.Subscription{},
	}
}

func (s *Storage) Load() (*StoreData, error) {
	s.mu.RLock()
	defer s.mu.RUnlock()

	if _, err := os.Stat(s.filePath); os.IsNotExist(err) {
		def := s.DefaultData()
		return def, nil
	}

	bytes, err := os.ReadFile(s.filePath)
	if err != nil {
		return nil, err
	}

	var data StoreData
	if err := json.Unmarshal(bytes, &data); err != nil {
		return nil, err
	}

	return &data, nil
}

func (s *Storage) Save(data *StoreData) error {
	s.mu.Lock()
	defer s.mu.Unlock()

	dir := filepath.Dir(s.filePath)
	if err := os.MkdirAll(dir, 0755); err != nil {
		return err
	}

	bytes, err := json.MarshalIndent(data, "", "  ")
	if err != nil {
		return err
	}

	tmpFile := s.filePath + ".tmp"
	if err := os.WriteFile(tmpFile, bytes, 0644); err != nil {
		return err
	}

	return os.Rename(tmpFile, s.filePath)
}
