package group

import (
	"testing"
	"v2rayn-wrt/internal/model"
)

func TestStrategySelection(t *testing.T) {
	mgr := NewManager()

	nodes := map[string]model.Node{
		"n1": {ID: "n1", Tag: "香港", DelayMs: 80},
		"n2": {ID: "n2", Tag: "日本", DelayMs: 35},
		"n3": {ID: "n3", Tag: "美国", DelayMs: -1},
	}

	// 1. 最低延迟 (UrlTest)
	groupUrlTest := model.OutboundGroup{
		ID:    "g1",
		Tag:   "自动选优",
		Type:  model.GroupTypeUrlTest,
		Nodes: []string{"n1", "n2", "n3"},
	}
	selected := mgr.SelectNode(groupUrlTest, nodes)
	if selected != "n2" {
		t.Errorf("expected n2 (35ms), got %s", selected)
	}

	// 2. 故障转移 (Failover)
	groupFailover := model.OutboundGroup{
		ID:    "g2",
		Tag:   "故障转移",
		Type:  model.GroupTypeFailover,
		Nodes: []string{"n3", "n1", "n2"},
	}
	selectedFailover := mgr.SelectNode(groupFailover, nodes)
	if selectedFailover != "n1" {
		t.Errorf("expected n1 (since n3 is down), got %s", selectedFailover)
	}

	// 3. 手动选择 (Selector)
	groupSelector := model.OutboundGroup{
		ID:       "g3",
		Tag:      "手动选择",
		Type:     model.GroupTypeSelector,
		Nodes:    []string{"n1", "n2", "n3"},
		Selected: "n3",
	}
	selectedSelector := mgr.SelectNode(groupSelector, nodes)
	if selectedSelector != "n3" {
		t.Errorf("expected n3, got %s", selectedSelector)
	}
}
