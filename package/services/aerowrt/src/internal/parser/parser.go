package parser

import (
	"encoding/base64"
	"fmt"
	"net/url"
	"strconv"
	"strings"
	"aerowrt/internal/model"
)

func ParseSubscriptionContent(content string) ([]model.Node, error) {
	trimmed := strings.TrimSpace(content)
	decodedBytes, err := base64.StdEncoding.DecodeString(trimmed)
	if err != nil {
		decodedBytes, err = base64.RawStdEncoding.DecodeString(trimmed)
	}
	rawText := trimmed
	if err == nil {
		rawText = string(decodedBytes)
	}

	var nodes []model.Node
	lines := strings.Split(rawText, "\n")
	for i, line := range lines {
		line = strings.TrimSpace(line)
		if line == "" || strings.HasPrefix(line, "#") {
			continue
		}
		node, err := ParseNodeLink(line)
		if err == nil {
			if node.ID == "" {
				node.ID = fmt.Sprintf("node-%d", i+1)
			}
			nodes = append(nodes, *node)
		}
	}
	return nodes, nil
}

func ParseNodeLink(link string) (*model.Node, error) {
	u, err := url.Parse(link)
	if err != nil {
		return nil, err
	}

	tag, _ := url.QueryUnescape(u.Fragment)
	if tag == "" {
		tag = u.Host
	}

	port := 443
	host := u.Hostname()
	if p, err := strconv.Atoi(u.Port()); err == nil {
		port = p
	}

	node := &model.Node{
		Tag:      tag,
		Server:   host,
		Port:     port,
		Security: u.Query().Get("security"),
		SNI:      u.Query().Get("sni"),
	}

	switch u.Scheme {
	case "vless":
		node.Protocol = model.ProtocolVLESS
		if u.User != nil {
			node.UUID = u.User.Username()
		}
		node.PublicKey = u.Query().Get("pbk")
		node.ShortID = u.Query().Get("sid")
		return node, nil
	case "trojan":
		node.Protocol = model.ProtocolTrojan
		if u.User != nil {
			node.Password = u.User.Username()
		}
		return node, nil
	case "ss":
		node.Protocol = model.ProtocolSS
		if u.User != nil {
			node.Password = u.User.Username()
		}
		return node, nil
	default:
		return nil, fmt.Errorf("unsupported protocol: %s", u.Scheme)
	}
}
