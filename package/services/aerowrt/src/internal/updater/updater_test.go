package updater

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestApplyProxy(t *testing.T) {
	rawUrl := "https://github.com/SagerNet/sing-box/releases/download/v1.9.3/sing-box-1.9.3-linux-amd64.tar.gz"

	// 1. 直连
	direct := ApplyProxy(rawUrl, "")
	if direct != rawUrl {
		t.Errorf("expected direct URL, got %s", direct)
	}

	// 2. 带 ghproxy 代理
	proxy := "https://ghproxy.net/"
	proxied := ApplyProxy(rawUrl, proxy)
	expected := "https://ghproxy.net/https://github.com/SagerNet/sing-box/releases/download/v1.9.3/sing-box-1.9.3-linux-amd64.tar.gz"
	if proxied != expected {
		t.Errorf("expected %s, got %s", expected, proxied)
	}

	// 3. 代理末尾无斜杠
	proxyNoSlash := "https://ghproxy.net"
	proxied2 := ApplyProxy(rawUrl, proxyNoSlash)
	if proxied2 != expected {
		t.Errorf("expected %s, got %s", expected, proxied2)
	}
}

func TestCheckRelease(t *testing.T) {
	mockRelease := map[string]interface{}{
		"tag_name": "v1.9.3",
		"assets": []map[string]interface{}{
			{
				"name":                 "sing-box-1.9.3-linux-amd64.tar.gz",
				"browser_download_url": "https://github.com/SagerNet/sing-box/releases/download/v1.9.3/sing-box-1.9.3-linux-amd64.tar.gz",
			},
		},
	}

	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		json.NewEncoder(w).Encode(mockRelease)
	}))
	defer ts.Close()

	u := NewUpdater()
	info, err := u.FetchReleaseFromURL(ts.URL, "linux", "amd64", "https://ghproxy.net/")
	if err != nil {
		t.Fatalf("FetchReleaseFromURL error: %v", err)
	}

	if info.Version != "v1.9.3" {
		t.Errorf("expected v1.9.3, got %s", info.Version)
	}
	if info.DownloadURL != "https://ghproxy.net/https://github.com/SagerNet/sing-box/releases/download/v1.9.3/sing-box-1.9.3-linux-amd64.tar.gz" {
		t.Errorf("unexpected proxied download url: %s", info.DownloadURL)
	}
}
