package parser

import (
	"encoding/base64"
	"testing"
	"v2rayn-wrt/internal/model"
)

func TestParseVLESSLink(t *testing.T) {
	link := "vless://a8b1c2d3-0000-0000-0000-000000000001@hk.node.com:443?security=reality&sni=hk.node.com&fp=chrome&pbk=testpubkey#%E9%A6%99%E6%B8%AF01"
	node, err := ParseNodeLink(link)
	if err != nil {
		t.Fatalf("ParseNodeLink error: %v", err)
	}

	if node.Protocol != model.ProtocolVLESS || node.Port != 443 || node.Tag != "香港01" {
		t.Errorf("unexpected parsed node: %+v", node)
	}
}

func TestParseBase64Subscription(t *testing.T) {
	raw := "vless://a8b1c2d3-0000-0000-0000-000000000001@hk.node.com:443?security=reality&sni=hk.node.com&fp=chrome&pbk=testpubkey#%E9%A6%99%E6%B8%AF01\ntrojan://mypw@jp.node.com:443#%E6%97%A5%E6%9C%AC02"
	content := base64.StdEncoding.EncodeToString([]byte(raw))

	nodes, err := ParseSubscriptionContent(content)
	if err != nil {
		t.Fatalf("ParseSubscriptionContent error: %v", err)
	}

	if len(nodes) != 2 {
		t.Fatalf("expected 2 nodes, got %d", len(nodes))
	}
	if nodes[1].Protocol != model.ProtocolTrojan || nodes[1].Tag != "日本02" {
		t.Errorf("second node mismatch: %+v", nodes[1])
	}
}
