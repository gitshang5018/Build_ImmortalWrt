package main

import (
	"context"
	"flag"
	"fmt"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"aerowrt/internal/core"
	"aerowrt/internal/model"
	"aerowrt/internal/server"
	"aerowrt/internal/storage"
	"aerowrt/web"
)

func main() {
	httpPort := flag.Int("port", 9099, "HTTP port for WebUI and API")
	mosdnsPort := flag.Int("mosdns", 5335, "Local MosDNS port to forward DNS queries")
	storePath := flag.String("store", "/etc/aerowrt/store.json", "Path to JSON persistent storage")
	configPath := flag.String("config", "/etc/aerowrt/config.json", "Path to generated Sing-box config")
	binPath := flag.String("singbox", "/usr/bin/sing-box", "Path to Sing-box executable")
	flag.Parse()

	settings := model.SystemSettings{
		ActiveNodeID: "node-hk",
		HttpPort:     *httpPort,
		MosDNSPort:   *mosdnsPort,
		RoutingMode:  "bypass_cn",
		DNSMode:      model.DNSModeMosDNS,
	}

	apiServer := server.NewServer(settings)
	store := storage.NewStorage(*storePath)
	supervisor := core.NewSupervisor(*binPath, *configPath)

	apiServer.SetStorage(store)
	apiServer.SetSupervisor(supervisor)

	if err := apiServer.LoadFromStorage(); err != nil {
		log.Printf("[WARN] Failed to load from storage: %v, using defaults", err)
	}

	// 启动时如果已有保存的节点，自动拉起 Sing-box 核心进程与透明代理
	initialNodes := apiServer.GetNodes()
	if len(initialNodes) > 0 {
		if err := supervisor.ApplyConfig(apiServer.GetSettings(), initialNodes); err != nil {
			log.Printf("[WARN] Failed to start Sing-box core: %v", err)
		}
	}

	mux := http.NewServeMux()

	// 挂载 API 端点
	mux.Handle("/api/", apiServer.Handler())

	// 挂载嵌入式 WebUI 静态文件系统
	mux.Handle("/", web.AssetHandler())

	addr := fmt.Sprintf(":%d", settings.HttpPort)
	log.Printf("=====================================================")
	log.Printf("  AeroWrt OpenWrt Modern Suite running on port %d", settings.HttpPort)
	log.Printf("  WebUI Dashboard: http://127.0.0.1%s", addr)
	log.Printf("  MosDNS Linkage:  127.0.0.1:%d", settings.MosDNSPort)
	log.Printf("=====================================================")

	srv := &http.Server{
		Addr:    addr,
		Handler: mux,
	}

	// 监听系统信号平滑退出，确保退出时清理 Sing-box 子核心并释放 tun0 设备
	sigChan := make(chan os.Signal, 1)
	signal.Notify(sigChan, syscall.SIGINT, syscall.SIGTERM)
	go func() {
		sig := <-sigChan
		log.Printf("[INFO] Received signal %v, gracefully shutting down AeroWrt...", sig)
		supervisor.Stop()
		ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
		defer cancel()
		_ = srv.Shutdown(ctx)
		os.Exit(0)
	}()

	if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
		log.Fatalf("Server error: %v", err)
	}
}
