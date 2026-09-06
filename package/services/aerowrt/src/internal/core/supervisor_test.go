package core

import (
	"os"
	"path/filepath"
	"testing"
	"aerowrt/internal/model"
)

func TestSupervisorApplyConfigAndLogs(t *testing.T) {
	tmpDir, err := os.MkdirTemp("", "aerowrt_sup_test_*")
	if err != nil {
		t.Fatalf("Failed to create temp dir: %v", err)
	}
	defer os.RemoveAll(tmpDir)

	confFile := filepath.Join(tmpDir, "config.json")
	sup := NewSupervisor("/fake/sing-box", confFile)

	settings := model.SystemSettings{
		ActiveNodeID: "node-1",
		RoutingMode:  "bypass_cn",
		DNSMode:      model.DNSModeMosDNS,
		MosDNSPort:   5335,
	}

	nodes := []model.Node{
		{
			ID:       "node-1",
			Tag:      "Test Node",
			Protocol: model.ProtocolVLESS,
			Server:   "1.2.3.4",
			Port:     443,
			Security: "reality",
		},
	}

	if err := sup.ApplyConfig(settings, nodes); err != nil {
		t.Fatalf("ApplyConfig failed: %v", err)
	}

	if _, err := os.Stat(confFile); os.IsNotExist(err) {
		t.Fatalf("Expected config file %s to exist", confFile)
	}

	logs := sup.GetLogs()
	if len(logs) < 2 {
		t.Errorf("Expected at least 2 log entries, got %d", len(logs))
	}
}

func TestSupervisorNoDeadlock(t *testing.T) {
	tmpDir, err := os.MkdirTemp("", "aerowrt_deadlock_test_*")
	if err != nil {
		t.Fatalf("Failed to create temp dir: %v", err)
	}
	defer os.RemoveAll(tmpDir)

	confFile := filepath.Join(tmpDir, "config.json")
	sup := NewSupervisor("/bin/sh", confFile)

	settings := model.SystemSettings{ActiveNodeID: "node-1"}
	nodes := []model.Node{
		{ID: "node-1", Tag: "Test", Protocol: model.ProtocolVLESS, Server: "1.1.1.1", Port: 443},
	}

	// 连续多次快速 ApplyConfig 与 Start/Stop，确保绝对不发生死锁
	for i := 0; i < 5; i++ {
		_ = sup.ApplyConfig(settings, nodes)
		sup.Stop()
	}
}

