package model

import (
	"encoding/json"
	"testing"
)

func TestNodeSerialization(t *testing.T) {
	node := Node{
		ID:        "node-1",
		Tag:       "香港 01 [BGP专线]",
		Protocol:  ProtocolVLESS,
		Server:    "hk.node.com",
		Port:      443,
		UUID:      "a8b1c2d3-0000-0000-0000-000000000001",
		Network:   "tcp",
		Security:  "reality",
		DelayMs:   28,
		ChainNode: "node-transit-1", // 链式前置代理节点ID
	}

	data, err := json.Marshal(node)
	if err != nil {
		t.Fatalf("Marshal failed: %v", err)
	}

	var decoded Node
	if err := json.Unmarshal(data, &decoded); err != nil {
		t.Fatalf("Unmarshal failed: %v", err)
	}

	if decoded.Tag != node.Tag || decoded.ChainNode != "node-transit-1" {
		t.Errorf("expected tag %s and chain %s, got %s / %s", node.Tag, "node-transit-1", decoded.Tag, decoded.ChainNode)
	}
}

func TestGroupAndSettingsSerialization(t *testing.T) {
	group := OutboundGroup{
		ID:        "grp-urltest",
		Tag:       "自动最低延迟",
		Type:      GroupTypeUrlTest,
		Nodes:     []string{"node-1", "node-2"},
		Tolerance: 20,
	}

	data, err := json.Marshal(group)
	if err != nil {
		t.Fatalf("Marshal group failed: %v", err)
	}

	var decodedGroup OutboundGroup
	if err := json.Unmarshal(data, &decodedGroup); err != nil {
		t.Fatalf("Unmarshal group failed: %v", err)
	}

	if decodedGroup.Type != GroupTypeUrlTest {
		t.Errorf("expected group type urltest, got %v", decodedGroup.Type)
	}

	settings := SystemSettings{
		ActiveNodeID: "node-1",
		RoutingMode:  "bypass_cn",
		DNSMode:      DNSModeMosDNS,
		MosDNSPort:   5335,
		HttpPort:     9099,
	}

	if settings.MosDNSPort != 5335 || settings.DNSMode != DNSModeMosDNS {
		t.Errorf("unexpected settings: %+v", settings)
	}
}
