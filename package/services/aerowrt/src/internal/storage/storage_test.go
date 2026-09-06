package storage

import (
	"os"
	"path/filepath"
	"testing"
	"aerowrt/internal/model"
)

func TestStorageLoadDefaultAndSave(t *testing.T) {
	tmpDir, err := os.MkdirTemp("", "aerowrt_test_*")
	if err != nil {
		t.Fatalf("Failed to create temp dir: %v", err)
	}
	defer os.RemoveAll(tmpDir)

	testFile := filepath.Join(tmpDir, "store.json")
	store := NewStorage(testFile)

	// Test loading when file does not exist
	data, err := store.Load()
	if err != nil {
		t.Fatalf("Failed to load default: %v", err)
	}
	if len(data.Nodes) != 3 {
		t.Errorf("Expected 3 default nodes, got %d", len(data.Nodes))
	}

	// Add a node and save
	data.Nodes = append(data.Nodes, model.Node{
		ID:       "node-custom",
		Tag:      "Custom Node",
		Protocol: model.ProtocolVLESS,
		Server:   "1.2.3.4",
		Port:     443,
	})

	if err := store.Save(data); err != nil {
		t.Fatalf("Failed to save data: %v", err)
	}

	// Reload and verify
	loaded, err := store.Load()
	if err != nil {
		t.Fatalf("Failed to reload data: %v", err)
	}
	if len(loaded.Nodes) != 4 {
		t.Errorf("Expected 4 nodes after reload, got %d", len(loaded.Nodes))
	}
	if loaded.Nodes[3].ID != "node-custom" {
		t.Errorf("Expected last node to be 'node-custom', got %s", loaded.Nodes[3].ID)
	}
}
