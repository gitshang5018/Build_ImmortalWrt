# 系统与无线性能深度优化实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 实现 ImmortalWrt 固件在 Wi-Fi 6 空口调度、802.11r/k/v 漫游、内存分级自适应、Go 语言后台服务防 OOM 内存控制以及 NAPI 网络数据包处理队列的全面优化。

**架构：** 通过增强 `wrt_core/patches/992_set-wifi-uci.sh` 实现 Wi-Fi 6 全特性释放与漫游域补齐；通过重构 `wrt_core/patches/991_custom_settings` 实现硬件内存动态分级自适应网络栈与 procd 全局 Go 环境变量注入；编写自动化仿真测试脚本确保全分支逻辑正确无误。

**技术栈：** Shell / Bash, OpenWrt UCI, Linux sysctl, Linux mac80211 / hostapd 802.11ax / 802.11kvr.

---

### 任务 1：升级无线射频与 802.11k/v/r 快速漫游补丁 (`992_set-wifi-uci.sh`)

**文件：**
- 修改：`wrt_core/patches/992_set-wifi-uci.sh`
- 测试：`wrt_core/patches/tests/test_wifi_uci.sh`

- [ ] **步骤 1：编写无线配置测试脚本**

创建测试脚本 `wrt_core/patches/tests/test_wifi_uci.sh`，用于模拟 UCI 环境并验证生成的无线配置项是否包含所有 Wi-Fi 6 特性、ATF、noscan 以及 802.11r 漫游参数。

```bash
#!/bin/bash
set -e

TMP_DIR=$(mktemp -d)
trap 'rm -rf "$TMP_DIR"' EXIT

mkdir -p "$TMP_DIR/tmp/sysinfo"
echo "jdcloud,ax1800-pro" > "$TMP_DIR/tmp/sysinfo/board_name"

# 模拟 uci 命令
cat <<'EOF' > "$TMP_DIR/uci"
#!/bin/bash
if [ "$1" = "get" ]; then
    echo "none"
    exit 0
fi
if [ "$1" = "-q" ] && [ "$2" = "batch" ]; then
    cat >> "$TMP_DIR/uci_out.txt"
    exit 0
fi
exit 0
EOF
chmod +x "$TMP_DIR/uci"

export PATH="$TMP_DIR:$PATH"
export UCI_TEST_DIR="$TMP_DIR"

# 运行测试
bash wrt_core/patches/992_set-wifi-uci.sh

# 验证核心无线配置项是否存在
grep -q "set wireless.radio0.he_dlofdma='1'" "$TMP_DIR/uci_out.txt"
grep -q "set wireless.radio0.he_ulofdma='1'" "$TMP_DIR/uci_out.txt"
grep -q "set wireless.radio0.airtime_fairness='1'" "$TMP_DIR/uci_out.txt"
grep -q "set wireless.default_radio0.mobility_domain='e4a1'" "$TMP_DIR/uci_out.txt"
grep -q "set wireless.default_radio0.nasid=" "$TMP_DIR/uci_out.txt"

echo "PASS: test_wifi_uci"
```

- [ ] **步骤 2：运行测试验证失败**

运行：`bash wrt_core/patches/tests/test_wifi_uci.sh`
预期：FAIL（缺少 `he_dlofdma`、`airtime_fairness`、`mobility_domain` 等配置）。

- [ ] **步骤 3：修改 `992_set-wifi-uci.sh` 补全无线特性**

更新 `wrt_core/patches/992_set-wifi-uci.sh` 中的 `configure_wifi` 函数：
1. 添加 Wi-Fi 6 下行/上行 OFDMA、MU-MIMO、单用户/多用户波束成形、TWT、ATF。
2. 添加 2.4G `noscan='1'` 和 `he_coext='0'`。
3. 补齐 `mobility_domain='e4a1'`、`r1_key_holder` 与动态 `nasid`。

- [ ] **步骤 4：运行测试验证通过**

运行：`bash wrt_core/patches/tests/test_wifi_uci.sh`
预期：PASS。

- [ ] **步骤 5：Commit**

```bash
git add wrt_core/patches/992_set-wifi-uci.sh wrt_core/patches/tests/test_wifi_uci.sh
git commit -m "feat(wifi): optimize wifi 6 ofdma, airtime fairness and 802.11r roaming"
```

---

### 任务 2：重构系统内存自适应分级与 Go 内存控制补丁 (`991_custom_settings`)

**文件：**
- 修改：`wrt_core/patches/991_custom_settings`
- 测试：`wrt_core/patches/tests/test_custom_settings.sh`

- [ ] **步骤 1：编写内存分级与网络栈测试脚本**

创建测试脚本 `wrt_core/patches/tests/test_custom_settings.sh`，分别传入 `128MB`、`1024MB`、`4096MB` 的模拟 `/proc/meminfo`，验证生成的三档 sysctl 参数、Go 环境变量注入与 Dnsmasq 缓存大小。

```bash
#!/bin/bash
set -e

TMP_DIR=$(mktemp -d)
trap 'rm -rf "$TMP_DIR"' EXIT

# 测试 1: 小内存设备 (< 300MB)
mkdir -p "$TMP_DIR/proc1" "$TMP_DIR/etc1"
echo "MemTotal:         128000 kB" > "$TMP_DIR/proc1/meminfo"
touch "$TMP_DIR/etc1/sysctl.conf"
touch "$TMP_DIR/etc1/profile"

MEMINFO_FILE="$TMP_DIR/proc1/meminfo" SYSCTL_CONF="$TMP_DIR/etc1/sysctl.conf" TARGET_ETC="$TMP_DIR/etc1" bash wrt_core/patches/991_custom_settings

grep -q "net.netfilter.nf_conntrack_max = 32768" "$TMP_DIR/etc1/sysctl.conf"
grep -q "GOMEMLIMIT=48MiB" "$TMP_DIR/etc1/environment"
grep -q "net.core.netdev_budget = 600" "$TMP_DIR/etc1/sysctl.conf"

# 测试 2: 中内存设备 (1024MB)
mkdir -p "$TMP_DIR/proc2" "$TMP_DIR/etc2"
echo "MemTotal:        1048576 kB" > "$TMP_DIR/proc2/meminfo"
touch "$TMP_DIR/etc2/sysctl.conf"
touch "$TMP_DIR/etc2/profile"

MEMINFO_FILE="$TMP_DIR/proc2/meminfo" SYSCTL_CONF="$TMP_DIR/etc2/sysctl.conf" TARGET_ETC="$TMP_DIR/etc2" bash wrt_core/patches/991_custom_settings

grep -q "net.netfilter.nf_conntrack_max = 65535" "$TMP_DIR/etc2/sysctl.conf"
grep -q "GOMEMLIMIT=128MiB" "$TMP_DIR/etc2/environment"

# 测试 3: 大内存设备 (4096MB)
mkdir -p "$TMP_DIR/proc3" "$TMP_DIR/etc3"
echo "MemTotal:        4194304 kB" > "$TMP_DIR/proc3/meminfo"
touch "$TMP_DIR/etc3/sysctl.conf"
touch "$TMP_DIR/etc3/profile"

MEMINFO_FILE="$TMP_DIR/proc3/meminfo" SYSCTL_CONF="$TMP_DIR/etc3/sysctl.conf" TARGET_ETC="$TMP_DIR/etc3" bash wrt_core/patches/991_custom_settings

grep -q "net.netfilter.nf_conntrack_max = 262144" "$TMP_DIR/etc3/sysctl.conf"
grep -q "GOMEMLIMIT=512MiB" "$TMP_DIR/etc3/environment"

echo "PASS: test_custom_settings"
```

- [ ] **步骤 2：运行测试验证失败**

运行：`bash wrt_core/patches/tests/test_custom_settings.sh`
预期：FAIL（缺少内存分级逻辑及全局 environment 注入）。

- [ ] **步骤 3：重构 `991_custom_settings` 实现自适应分级**

修改 `wrt_core/patches/991_custom_settings`：
1. 获取 `MemTotal`。
2. 动态下发小/中/大三档对应的 `conntrack_max`、`tcp_mem`、`tcp_rmem`、`tcp_wmem`、`somaxconn`、`vfs_cache_pressure`。
3. 下发通用的 `net.core.netdev_budget = 600`、`net.core.netdev_budget_usecs = 4000`、`BBR` + `FQ`。
4. 在 `/etc/environment`、`/etc/profile.d/gomem.sh`、`/etc/profile`、`/etc/rc.local` 全局注入自适应的 `GOMEMLIMIT` 与 `GOGC`。
5. 针对 Dnsmasq 缓存大小进行分级配置。

- [ ] **步骤 4：运行测试验证通过**

运行：`bash wrt_core/patches/tests/test_custom_settings.sh`
预期：PASS。

- [ ] **步骤 5：Commit**

```bash
git add wrt_core/patches/991_custom_settings wrt_core/patches/tests/test_custom_settings.sh
git commit -m "feat(system): add adaptive memory tiering, procd gomemlimit and napi tuning"
```

---

### 任务 3：端到端语法与构建流程校验

**文件：**
- 修改：`wrt_core/modules/system.sh`
- 测试：`wrt_core/patches/tests/`

- [ ] **步骤 1：全量语法与 shellcheck 检查**

运行：`bash -n wrt_core/patches/991_custom_settings wrt_core/patches/992_set-wifi-uci.sh wrt_core/modules/system.sh`
预期：无语法错误。

- [ ] **步骤 2：全量单元测试套件运行**

运行测试套件：
```bash
bash wrt_core/patches/tests/test_wifi_uci.sh
bash wrt_core/patches/tests/test_custom_settings.sh
```
预期：全部 PASS。

- [ ] **步骤 3：清理临时测试脚本并 Commit 最终成果**

```bash
git add wrt_core/patches/ wrt_core/modules/
git commit -m "chore(optimize): finalize system and wireless optimizations"
```
