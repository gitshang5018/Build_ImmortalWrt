package core

import (
	"bufio"
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os"
	"os/exec"
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
	cmd        *exec.Cmd
	cancel     context.CancelFunc
	isRunning  bool
}

func NewSupervisor(binPath, configPath string) *Supervisor {
	if binPath == "" {
		binPath = findSingboxBinary()
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

func findSingboxBinary() string {
	candidates := []string{
		"/usr/bin/sing-box",
		"/usr/local/bin/sing-box",
		"/bin/sing-box",
	}
	for _, p := range candidates {
		if _, err := os.Stat(p); err == nil {
			return p
		}
	}
	return "/usr/bin/sing-box"
}

func (s *Supervisor) AddLog(level, msg string) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.addLogLocked(level, msg)
}

func (s *Supervisor) addLogLocked(level, msg string) {
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

	s.AddLog("SUCCESS", fmt.Sprintf("Sing-box configuration generated (%d nodes, active: %s)", len(nodes), settings.ActiveNodeID))

	// 如果有可用节点，尝试启动或平滑重载 Sing-box 核心
	if len(nodes) > 0 {
		if err := s.Start(); err != nil {
			s.AddLog("WARN", fmt.Sprintf("Could not start Sing-box core process: %v", err))
		}
	}
	return nil
}

// Start 启动或重启 Sing-box 核心进程
func (s *Supervisor) Start() error {
	s.mu.Lock()
	defer s.mu.Unlock()

	// 1. 检查二进制文件是否存在
	if _, err := os.Stat(s.binPath); err != nil {
		s.isRunning = false
		s.addLogLocked("ERROR", fmt.Sprintf("Sing-box binary not found at %s. Please install with: opkg install sing-box", s.binPath))
		return fmt.Errorf("sing-box binary not found: %w", err)
	}

	// 2. 检查配置文件是否存在
	if _, err := os.Stat(s.configPath); err != nil {
		s.isRunning = false
		s.addLogLocked("WARN", fmt.Sprintf("Sing-box config file not found at %s yet.", s.configPath))
		return fmt.Errorf("config not found: %w", err)
	}

	// 3. 停止已在运行的旧实例
	s.stopProcessLocked()

	// 4. 创建子进程并启动
	ctx, cancel := context.WithCancel(context.Background())
	s.cancel = cancel

	cmd := exec.CommandContext(ctx, s.binPath, "run", "-c", s.configPath)

	stdoutPipe, err := cmd.StdoutPipe()
	if err != nil {
		s.addLogLocked("ERROR", fmt.Sprintf("Failed to create stdout pipe: %v", err))
		return err
	}
	stderrPipe, err := cmd.StderrPipe()
	if err != nil {
		s.addLogLocked("ERROR", fmt.Sprintf("Failed to create stderr pipe: %v", err))
		return err
	}

	if err := cmd.Start(); err != nil {
		s.isRunning = false
		s.addLogLocked("ERROR", fmt.Sprintf("Failed to start Sing-box: %v", err))
		return err
	}

	s.cmd = cmd
	s.isRunning = true
	if cmd.Process != nil {
		s.addLogLocked("SUCCESS", fmt.Sprintf("Sing-box core process started (PID: %d)", cmd.Process.Pid))
	}

	// 异步读取标准输出与错误日志流
	go s.streamLogs(stdoutPipe, "CORE")
	go s.streamLogs(stderrPipe, "CORE")

	// 监控进程退出
	go func() {
		waitErr := cmd.Wait()
		s.mu.Lock()
		s.isRunning = false
		if waitErr != nil {
			s.addLogLocked("WARN", fmt.Sprintf("Sing-box process exited: %v", waitErr))
		} else {
			s.addLogLocked("INFO", "Sing-box process exited cleanly.")
		}
		s.mu.Unlock()
	}()

	return nil
}

func (s *Supervisor) streamLogs(r io.Reader, level string) {
	scanner := bufio.NewScanner(r)
	for scanner.Scan() {
		text := scanner.Text()
		if text != "" {
			s.AddLog(level, text)
		}
	}
}

func (s *Supervisor) stopProcessLocked() {
	if s.cancel != nil {
		s.cancel()
		s.cancel = nil
	}
	if s.cmd != nil && s.cmd.Process != nil {
		_ = s.cmd.Process.Kill()
		_ = s.cmd.Wait()
		s.cmd = nil
	}
	s.isRunning = false
}

func (s *Supervisor) Stop() {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.stopProcessLocked()
	s.addLogLocked("INFO", "Sing-box core process stopped.")
}

func (s *Supervisor) Restart() error {
	return s.Start()
}

// SwitchViaClash 尝试通过 Sing-box 的 Clash API 动态切换当前代理节点 (1毫秒无感切换)
func (s *Supervisor) SwitchViaClash(nodeTag string) bool {
	if !s.IsRunning() || nodeTag == "" {
		return false
	}

	url := "http://127.0.0.1:9090/proxies/proxy"
	payload, _ := json.Marshal(map[string]string{"name": nodeTag})
	req, err := http.NewRequest(http.MethodPut, url, bytes.NewBuffer(payload))
	if err != nil {
		return false
	}
	req.Header.Set("Content-Type", "application/json")

	client := &http.Client{Timeout: 1 * time.Second}
	resp, err := client.Do(req)
	if err != nil {
		return false
	}
	defer resp.Body.Close()

	if resp.StatusCode == http.StatusOK || resp.StatusCode == http.StatusNoContent {
		s.AddLog("SUCCESS", fmt.Sprintf("Switched active proxy outbound to [%s] via Clash API", nodeTag))
		return true
	}
	return false
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
