package core

import (
	"fmt"
	"os"
	"path/filepath"
	"sync"
	"time"
	"aerowrt/internal/model"
)

type Supervisor struct {
	mu         sync.RWMutex
	binPath    string
	configPath string
	generator  *Generator
	logs       []string
	maxLogs    int
	isRunning  bool
}

func NewSupervisor(binPath, configPath string) *Supervisor {
	if binPath == "" {
		binPath = "/usr/bin/sing-box"
	}
	if configPath == "" {
		configPath = "/etc/aerowrt/config.json"
	}
	s := &Supervisor{
		binPath:    binPath,
		configPath: configPath,
		generator:  NewGenerator(),
		logs:       make([]string, 0, 200),
		maxLogs:    200,
		isRunning:  false,
	}
	s.AddLog("INFO", "AeroWrt Core Supervisor initialized.")
	return s
}

func (s *Supervisor) AddLog(level, msg string) {
	s.mu.Lock()
	defer s.mu.Unlock()
	timestamp := time.Now().Format("15:04:05")
	entry := fmt.Sprintf("[%s] [%s] %s", timestamp, level, msg)
	if len(s.logs) >= s.maxLogs {
		s.logs = s.logs[1:]
	}
	s.logs = append(s.logs, entry)
}

func (s *Supervisor) GetLogs() []string {
	s.mu.RLock()
	defer s.mu.RUnlock()
	res := make([]string, len(s.logs))
	copy(res, s.logs)
	return res
}

func (s *Supervisor) ApplyConfig(settings model.SystemSettings, nodes []model.Node) error {
	configJSON, err := s.generator.GenerateSingboxConfig(settings, nodes)
	if err != nil {
		s.AddLog("ERROR", fmt.Sprintf("Failed to generate Sing-box config: %v", err))
		return err
	}

	dir := filepath.Dir(s.configPath)
	if err := os.MkdirAll(dir, 0755); err != nil {
		s.AddLog("ERROR", fmt.Sprintf("Failed to create config dir: %v", err))
		return err
	}

	if err := os.WriteFile(s.configPath, []byte(configJSON), 0644); err != nil {
		s.AddLog("ERROR", fmt.Sprintf("Failed to write config file %s: %v", s.configPath, err))
		return err
	}

	s.AddLog("SUCCESS", fmt.Sprintf("Sing-box configuration generated and saved to %s", s.configPath))
	return nil
}

func (s *Supervisor) IsRunning() bool {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return s.isRunning
}

func (s *Supervisor) SetRunning(r bool) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.isRunning = r
}
