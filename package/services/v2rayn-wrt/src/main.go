package main

import (
	"flag"
	"fmt"
	"log"
	"net/http"
	"v2rayn-wrt/internal/model"
	"v2rayn-wrt/internal/server"
	"v2rayn-wrt/web"
)

func main() {
	httpPort := flag.Int("port", 9099, "HTTP port for WebUI and API")
	mosdnsPort := flag.Int("mosdns", 5335, "Local MosDNS port to forward DNS queries")
	flag.Parse()

	settings := model.SystemSettings{
		ActiveNodeID: "node-hk",
		HttpPort:     *httpPort,
		MosDNSPort:   *mosdnsPort,
		RoutingMode:  "bypass_cn",
		DNSMode:      model.DNSModeMosDNS,
	}

	apiServer := server.NewServer(settings)

	// 添加预置体验节点
	apiServer.AddNode(model.Node{
		ID:       "node-hk",
		Tag:      "🇭🇰 香港 01 [BGP专线]",
		Protocol: model.ProtocolVLESS,
		Server:   "hk.node.com",
		Port:     443,
		Security: "reality",
		DelayMs:  28,
	})
	apiServer.AddNode(model.Node{
		ID:       "node-jp",
		Tag:      "🇯🇵 日本 02 [原生流媒体]",
		Protocol: model.ProtocolSS,
		Server:   "jp.node.com",
		Port:     8388,
		DelayMs:  52,
	})
	apiServer.AddNode(model.Node{
		ID:       "node-sg",
		Tag:      "🇸🇬 新加坡 01 [优质线路]",
		Protocol: model.ProtocolTrojan,
		Server:   "sg.node.com",
		Port:     443,
		DelayMs:  64,
	})

	mux := http.NewServeMux()

	// 挂载 API 端点
	mux.Handle("/api/", apiServer.Handler())

	// 挂载嵌入式 WebUI 静态文件系统
	mux.Handle("/", web.AssetHandler())

	addr := fmt.Sprintf(":%d", settings.HttpPort)
	log.Printf("=====================================================")
	log.Printf("  v2rayN-Wrt OpenWrt Modern Suite running on port %d", settings.HttpPort)
	log.Printf("  WebUI Dashboard: http://127.0.0.1%s", addr)
	log.Printf("  MosDNS Linkage:  127.0.0.1:%d", settings.MosDNSPort)
	log.Printf("=====================================================")

	if err := http.ListenAndServe(addr, mux); err != nil {
		log.Fatalf("Server error: %v", err)
	}
}
