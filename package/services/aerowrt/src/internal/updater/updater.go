package updater

import (
	"encoding/json"
	"fmt"
	"net/http"
	"runtime"
	"strings"
	"time"
)

type ReleaseInfo struct {
	Version     string `json:"version"`
	DownloadURL string `json:"download_url"`
	AssetName   string `json:"asset_name"`
	Arch        string `json:"arch"`
}

type Updater struct {
	client  *http.Client
	BaseURL string // 可覆盖默认的 GitHub Releases API 地址
}

func NewUpdater() *Updater {
	return &Updater{
		client: &http.Client{Timeout: 15 * time.Second},
	}
}

// ApplyProxy formats the download URL with the specified GitHub proxy prefix
func ApplyProxy(rawUrl string, proxy string) string {
	proxy = strings.TrimSpace(proxy)
	if proxy == "" || strings.EqualFold(proxy, "direct") {
		return rawUrl
	}
	if !strings.HasSuffix(proxy, "/") {
		proxy += "/"
	}
	return proxy + rawUrl
}

// DetectSystemArch maps Go runtime.GOARCH to typical GitHub release asset arch names
func DetectSystemArch() (string, string) {
	osName := runtime.GOOS
	if osName != "linux" {
		osName = "linux" // 默认目标是 OpenWrt Linux
	}

	arch := runtime.GOARCH
	switch arch {
	case "amd64":
		return osName, "amd64"
	case "arm64":
		return osName, "arm64"
	case "arm":
		return osName, "armv7"
	case "mipsle":
		return osName, "mipsle"
	default:
		return osName, arch
	}
}

// FetchReleaseFromURL queries a release API and picks the asset matching target OS and arch
func (u *Updater) FetchReleaseFromURL(apiURL, targetOS, targetArch, proxy string) (*ReleaseInfo, error) {
	req, err := http.NewRequest("GET", apiURL, nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("User-Agent", "aerowrt-updater")

	resp, err := u.client.Do(req)
	if err != nil {
		return nil, fmt.Errorf("request release failed: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("release API returned HTTP %d", resp.StatusCode)
	}

	var ghRelease struct {
		TagName string `json:"tag_name"`
		Assets  []struct {
			Name               string `json:"name"`
			BrowserDownloadURL string `json:"browser_download_url"`
		} `json:"assets"`
	}

	if err := json.NewDecoder(resp.Body).Decode(&ghRelease); err != nil {
		return nil, fmt.Errorf("decode release json failed: %w", err)
	}

	// 匹配最适合当前系统的 asset (如 sing-box-*-linux-amd64.tar.gz)
	var matchedURL, matchedName string
	for _, asset := range ghRelease.Assets {
		lower := strings.ToLower(asset.Name)
		if strings.Contains(lower, targetOS) && strings.Contains(lower, targetArch) {
			matchedURL = asset.BrowserDownloadURL
			matchedName = asset.Name
			break
		}
	}

	if matchedURL == "" && len(ghRelease.Assets) > 0 {
		matchedURL = ghRelease.Assets[0].BrowserDownloadURL
		matchedName = ghRelease.Assets[0].Name
	}

	if matchedURL == "" {
		return nil, fmt.Errorf("no suitable asset found for %s-%s", targetOS, targetArch)
	}

	return &ReleaseInfo{
		Version:     ghRelease.TagName,
		DownloadURL: ApplyProxy(matchedURL, proxy),
		AssetName:   matchedName,
		Arch:        fmt.Sprintf("%s-%s", targetOS, targetArch),
	}, nil
}

// CheckLatestRelease gets latest release for sing-box or xray-core
func (u *Updater) CheckLatestRelease(coreType, proxy string) (*ReleaseInfo, error) {
	apiURL := u.BaseURL
	if apiURL == "" {
		repo := "SagerNet/sing-box"
		if strings.ToLower(coreType) == "xray" || strings.ToLower(coreType) == "xray-core" {
			repo = "XTLS/Xray-core"
		}
		apiURL = fmt.Sprintf("https://api.github.com/repos/%s/releases/latest", repo)
	}
	osName, arch := DetectSystemArch()
	return u.FetchReleaseFromURL(apiURL, osName, arch, proxy)
}
