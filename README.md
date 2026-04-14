# ImmortalWrt 固件编译项目

本项目基于 **ImmortalWrt** 源码，针对特定硬件平台（如京东云、歌华链等）进行深度定制与优化，旨在提供轻量、快速、功能丰富的固件。

## 🚀 核心特性

- **默认后台地址**: `10.10.10.1` (账户: `root` / 密码: `无` 或 `password`)
- **主流科学套件**: 精心集成了 **PassWall 2**、**SSR-Plus** 和 **OpenClash**，支持主流协议及订阅迁移。
- **美化界面**: 默认采用 **luci-theme-design (JS 版本)**，提供流畅的现代 UI 交互。
- **硬件加速**: 针对 MT7621 (歌华链) 和 IPQ60XX (雅典娜/亚瑟) 开启了 HNAT/NSS 加速。
- **精简极致**: 歌华链 (32M Flash) 版本经过深度瘦身，仅保留 PassWall 2 和 MosDNS，确保系统稳定。

## 🛠️ 环境准备

建议使用 **Ubuntu 20.04 LTS / 22.04 LTS** 环境进行编译。

### 安装依赖
```bash
sudo apt -y update
sudo apt -y full-upgrade
sudo apt install -y dos2unix libfuse-dev
sudo bash -c 'bash <(curl -sL https://build-scripts.immortalwrt.org/init_build_environment.sh)'
```

## 📦 编译步骤

1. **克隆仓库**:
   ```bash
   git clone https://github.com/gitshang5018/Build_ImmortalWrt.git
   cd Build_ImmortalWrt
   ```

2. **本地编译**:
   使用 `./build.sh` 脚本并指定目标名称：
   
   - **京东云 雅典娜 (AX6600)**: `./build.sh jdcloud_ax6000_immwrt`
   - **京东云 亚瑟 (AX1800)**: `./build.sh jdcloud_ipq60xx_immwrt`
   - **歌华链 (GHL-R-001)**: `./build.sh gehua_ghl-r-001_immwrt`
   - **竞斗云 2.0 (R619AC)**: `./build.sh p2w_r619ac-128m_immwrt`
   - **DELL Wyse 3040**: `./build.sh dell_wyse_3040_immwrt`
   - **通用 X86 (64位)**: `./build.sh x64_immwrt`

## 📂 项目结构

- **wrt_core/**: 核心配置库
  - **deconfig/**: 存放插件选中的碎片配置（如 `proxy.config` 管理代理插件）。
  - **compilecfg/**: 定义不同设备的系统级 INI 映射。
  - **modules/**: 模块化脚本集合（`packages.sh` 处理插件增删，`system.sh` 处理系统微调）。
  - **update.sh**: 构建流程的逻辑主入口。
- **build.sh**: 顶层一键编译脚本。

## ⚠️ 开发注意事项

- **插件管理**: 增加或删除插件请修改 `wrt_core/modules/packages.sh` 中的安装列表，并在 `deconfig/` 下同步开启/关闭对应的 `CONFIG_PACKAGE_` 选项。
- **自定义设置**: 默认 IP、主题及地区设置集中在 `wrt_core/update.sh` 的变量定义区。

---
*Modified from ZqinKing's original source.*
