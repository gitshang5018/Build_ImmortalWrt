package parser

import (
	"encoding/base64"
	"encoding/json"
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
	link = strings.TrimSpace(link)
	if strings.HasPrefix(link, "vmess://") {
		return parseVMessLink(link)
	}

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

	network := u.Query().Get("type")
	if network == "" {
		network = u.Query().Get("network")
	}
	node.Network = network
	node.Path = u.Query().Get("path")

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
			rawInfo := u.User.Username()
			if pwd, ok := u.User.Password(); ok && pwd != "" {
				rawInfo = fmt.Sprintf("%s:%s", u.User.Username(), pwd)
			}
			if strings.Contains(rawInfo, ":") {
				parts := strings.SplitN(rawInfo, ":", 2)
				node.Method = parts[0]
				node.Password = parts[1]
			} else if dec, err := base64.StdEncoding.DecodeString(rawInfo); err == nil && strings.Contains(string(dec), ":") {
				parts := strings.SplitN(string(dec), ":", 2)
				node.Method = parts[0]
				node.Password = parts[1]
			} else if dec, err := base64.RawStdEncoding.DecodeString(rawInfo); err == nil && strings.Contains(string(dec), ":") {
				parts := strings.SplitN(string(dec), ":", 2)
				node.Method = parts[0]
				node.Password = parts[1]
			} else {
				node.Password = rawInfo
				node.Method = "aes-128-gcm"
			}
		}
		if node.Method == "" {
			node.Method = "aes-128-gcm"
		}
		return node, nil
	case "hy2", "hysteria2":
		node.Protocol = model.ProtocolHysteria2
		if u.User != nil {
			node.Password = u.User.Username()
		}
		return node, nil
	default:
		return nil, fmt.Errorf("unsupported protocol: %s", u.Scheme)
	}
}

func parseVMessLink(link string) (*model.Node, error) {
	b64 := strings.TrimPrefix(link, "vmess://")
	decoded, err := base64.StdEncoding.DecodeString(b64)
	if err != nil {
		decoded, err = base64.RawStdEncoding.DecodeString(b64)
		if err != nil {
			return nil, err
		}
	}
	var data struct {
		V    interface{} `json:"v"`
		PS   string      `json:"ps"`
		Add  string      `json:"add"`
		Port interface{} `json:"port"`
		ID   string      `json:"id"`
		Net  string      `json:"net"`
		Type string      `json:"type"`
		Host string      `json:"host"`
		Path string      `json:"path"`
		TLS  string      `json:"tls"`
		SNI  string      `json:"sni"`
	}
	if err := json.Unmarshal(decoded, &data); err != nil {
		return nil, err
	}
	port := 443
	switch v := data.Port.(type) {
	case float64:
		port = int(v)
	case string:
		if p, err := strconv.Atoi(v); err == nil {
			port = p
		}
	}
	tag := data.PS
	if tag == "" {
		tag = data.Add
	}
	sni := data.SNI
	if sni == "" {
		sni = data.Host
	}
	sec := data.TLS
	if sec == "" && data.Host != "" {
		sec = "tls"
	}
	return &model.Node{
		Tag:      tag,
		Protocol: model.ProtocolVMess,
		Server:   data.Add,
		Port:     port,
		UUID:     data.ID,
		Network:  data.Net,
		Path:     data.Path,
		Security: sec,
		SNI:      sni,
	}, nil
}
