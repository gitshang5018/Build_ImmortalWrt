#!/usr/bin/env bash

set -e

# Determine wrt_core path
if [ -d "wrt_core" ]; then
    WRT_CORE_PATH="wrt_core"
elif [ -d "../wrt_core" ]; then
    WRT_CORE_PATH="../wrt_core"
else
    echo "Error: wrt_core directory not found!"
    exit 1
fi

BASE_PATH=$(cd "$WRT_CORE_PATH" && pwd)
source "$BASE_PATH/modules/general.sh"

Dev=$1
Build_Mod=$2

SUPPORTED_DEVS=()

collect_supported_devs() {
    local ini_file
    local dev_key
    local IFS

    SUPPORTED_DEVS=()

    for ini_file in "$BASE_PATH"/compilecfg/*.ini; do
        [[ -f "$ini_file" ]] || continue

        dev_key=$(basename "$ini_file" .ini)
        if [[ -f "$BASE_PATH/deconfig/$dev_key.config" ]]; then
            SUPPORTED_DEVS+=("$dev_key")
        fi
    done

    if [[ ${#SUPPORTED_DEVS[@]} -eq 0 ]]; then
        return
    fi

    IFS=$'\n' SUPPORTED_DEVS=($(printf '%s\n' "${SUPPORTED_DEVS[@]}" | LC_ALL=C sort))
}

print_usage() {
    echo "Usage: $0 <device> [debug]"
}

print_supported_devs() {
    local index

    echo "Supported devices:"
    for ((index = 0; index < ${#SUPPORTED_DEVS[@]}; index++)); do
        printf "  %d) %s\n" "$((index + 1))" "${SUPPORTED_DEVS[index]}"
    done
}

prompt_select_dev() {
    local input
    local selected_index

    while true; do
        print_supported_devs
        printf "Select device by number (q to quit): "

        if ! read -r input; then
            echo
            echo "Cancelled."
            exit 1
        fi

        if [[ "$input" =~ ^[[:space:]]*[qQ][[:space:]]*$ ]]; then
            echo "Cancelled."
            exit 1
        fi

        if [[ "$input" =~ ^[[:space:]]*([0-9]+)[[:space:]]*$ ]]; then
            selected_index=${BASH_REMATCH[1]}
            if ((selected_index >= 1 && selected_index <= ${#SUPPORTED_DEVS[@]})); then
                Dev=${SUPPORTED_DEVS[selected_index - 1]}
                return
            fi
        fi

        echo "Invalid selection. Please enter a number between 1 and ${#SUPPORTED_DEVS[@]}."
    done
}

prompt_select_build_mode() {
    local input

    while true; do
        echo "Build mode:"
        echo "  1) normal"
        echo "  2) debug"
        printf "Select build mode (1-2, q to quit): "

        if ! read -r input; then
            echo
            echo "Cancelled."
            exit 1
        fi

        if [[ "$input" =~ ^[[:space:]]*[qQ][[:space:]]*$ ]]; then
            echo "Cancelled."
            exit 1
        fi

        if [[ "$input" =~ ^[[:space:]]*1[[:space:]]*$ ]]; then
            Build_Mod=""
            return
        fi

        if [[ "$input" =~ ^[[:space:]]*2[[:space:]]*$ ]]; then
            Build_Mod="debug"
            return
        fi

        echo "Invalid selection. Please enter 1 or 2."
    done
}

is_interactive_terminal() {
    [[ -t 0 && -t 1 ]]
}

if [[ $# -eq 0 ]]; then
    collect_supported_devs

    if [[ ${#SUPPORTED_DEVS[@]} -eq 0 ]]; then
        log_error "错误：未发现支持的设备。"
        exit 1
    fi

    if ! is_interactive_terminal; then
        print_usage
        print_supported_devs
        exit 1
    fi

    prompt_select_dev

    if [[ -z $Build_Mod ]]; then
        prompt_select_build_mode
    fi
fi

log_info "目标设备: ${GREEN}$Dev${NC}"
log_info "构建模式: ${GREEN}${Build_Mod:-normal}${NC}"

CONFIG_FILE="$BASE_PATH/deconfig/$Dev.config"
INI_FILE="$BASE_PATH/compilecfg/$Dev.ini"

if [[ ! -f $CONFIG_FILE ]]; then
    echo "Config not found: $CONFIG_FILE"
    exit 1
fi

if [[ ! -f $INI_FILE ]]; then
    echo "INI file not found: $INI_FILE"
    exit 1
fi

read_ini_by_key() {
    local key=$1
    awk -F"=" -v key="$key" '$1 == key {print $2}' "$INI_FILE"
}

remove_uhttpd_dependency() {
    local config_path="$BASE_PATH/../$BUILD_DIR/.config"
    local luci_makefile_path="$BASE_PATH/../$BUILD_DIR/feeds/luci/collections/luci/Makefile"

    #if grep -q "CONFIG_PACKAGE_luci-app-quickfile=y" "$config_path"; then
    #    if [ -f "$luci_makefile_path" ]; then
    #        sed -i '/luci-light/d' "$luci_makefile_path"
    #        echo "Removed uhttpd (luci-light) dependency as luci-app-quickfile (nginx) is enabled."
    #    fi
    #fi
}

apply_config() {
    \cp -f "$CONFIG_FILE" "$BASE_PATH/../$BUILD_DIR/.config"
    
    if grep -qE "(ipq60xx|ipq807x)" "$BASE_PATH/../$BUILD_DIR/.config" &&
        ! grep -q "CONFIG_GIT_MIRROR" "$BASE_PATH/../$BUILD_DIR/.config"; then
        cat "$BASE_PATH/deconfig/nss.config" >> "$BASE_PATH/../$BUILD_DIR/.config"
    fi

    cat "$BASE_PATH/deconfig/compile_base.config" >> "$BASE_PATH/../$BUILD_DIR/.config"

    if [[ "$Dev" != "jdcloud_ipq60xx_immwrt" ]]; then
        cat "$BASE_PATH/deconfig/docker_deps.config" >> "$BASE_PATH/../$BUILD_DIR/.config"
    fi

    cat "$BASE_PATH/deconfig/proxy.config" >> "$BASE_PATH/../$BUILD_DIR/.config"
    
# Remove heavy plugins for low-flash devices
    if [[ "$Dev" == "p2w_r619ac-128m_immwrt" || "$Dev" == "gehua_ghl-r-001_immwrt" ]]; then
        echo "Detect low-flash device ($Dev), removing heavy packages..."
        
        # 两款设备共同需要移除的 5 项插件
        sed -i 's/CONFIG_PACKAGE_luci-app-smartdns=y/# CONFIG_PACKAGE_luci-app-smartdns is not set/g' "$BASE_PATH/../$BUILD_DIR/.config"
        sed -i 's/CONFIG_PACKAGE_luci-app-passwall=y/# CONFIG_PACKAGE_luci-app-passwall is not set/g' "$BASE_PATH/../$BUILD_DIR/.config"
        sed -i 's/CONFIG_PACKAGE_luci-app-adguardhome=y/# CONFIG_PACKAGE_luci-app-adguardhome is not set/g' "$BASE_PATH/../$BUILD_DIR/.config"
        sed -i 's/CONFIG_PACKAGE_luci-app-dockerman=y/# CONFIG_PACKAGE_luci-app-dockerman is not set/g' "$BASE_PATH/../$BUILD_DIR/.config"
        sed -i 's/CONFIG_PACKAGE_luci-app-samba4=y/# CONFIG_PACKAGE_luci-app-samba4 is not set/g' "$BASE_PATH/../$BUILD_DIR/.config"

        # 仅 gehua 需要额外移除的插件 (32M Flash 极端精简)
        if [[ "$Dev" == "gehua_ghl-r-001_immwrt" ]]; then
            sed -i 's/CONFIG_PACKAGE_luci-app-lucky=y/# CONFIG_PACKAGE_luci-app-lucky is not set/g' "$BASE_PATH/../$BUILD_DIR/.config"
            sed -i 's/CONFIG_PACKAGE_luci-app-oaf=y/# CONFIG_PACKAGE_luci-app-oaf is not set/g' "$BASE_PATH/../$BUILD_DIR/.config"
            sed -i 's/CONFIG_PACKAGE_luci-app-easytier=y/# CONFIG_PACKAGE_luci-app-easytier is not set/g' "$BASE_PATH/../$BUILD_DIR/.config"
            sed -i 's/CONFIG_PACKAGE_luci-app-openclash=y/# CONFIG_PACKAGE_luci-app-openclash is not set/g' "$BASE_PATH/../$BUILD_DIR/.config"
            sed -i 's/CONFIG_PACKAGE_luci-app-ssr-plus=y/# CONFIG_PACKAGE_luci-app-ssr-plus is not set/g' "$BASE_PATH/../$BUILD_DIR/.config"
            sed -i 's/CONFIG_PACKAGE_luci-app-pbr=y/# CONFIG_PACKAGE_luci-app-pbr is not set/g' "$BASE_PATH/../$BUILD_DIR/.config"
        fi
    fi
}

REPO_URL=$(read_ini_by_key "REPO_URL")
REPO_BRANCH=$(read_ini_by_key "REPO_BRANCH")
REPO_BRANCH=${REPO_BRANCH:-main}
BUILD_DIR=$(read_ini_by_key "BUILD_DIR")
COMMIT_HASH=$(read_ini_by_key "COMMIT_HASH")
COMMIT_HASH=${COMMIT_HASH:-none}

if [[ -d action_build ]]; then
    BUILD_DIR="action_build"
fi

"$BASE_PATH/update.sh" "$REPO_URL" "$REPO_BRANCH" "$BUILD_DIR" "$COMMIT_HASH"

group_start "正在生成配置文件"
apply_config
remove_uhttpd_dependency

cd "$BASE_PATH/../$BUILD_DIR"
log_info "执行 make defconfig..."
make defconfig > /dev/null
group_end

if grep -qE "^CONFIG_TARGET_x86_64=y" "$CONFIG_FILE"; then
    DISTFEEDS_PATH="$BASE_PATH/../$BUILD_DIR/package/emortal/default-settings/files/99-distfeeds.conf"
    if [ -d "${DISTFEEDS_PATH%/*}" ] && [ -f "$DISTFEEDS_PATH" ]; then
        sed -i 's/aarch64_cortex-a53/x86_64/g' "$DISTFEEDS_PATH"
    fi
fi

if [[ $Build_Mod == "debug" ]]; then
    exit 0
fi

TARGET_DIR="$BASE_PATH/../$BUILD_DIR/bin/targets"
if [[ -d $TARGET_DIR ]]; then
    find "$TARGET_DIR" -type f \( -name "*.bin" -o -name "*.manifest" -o -name "*efi.img.gz" -o -name "*.itb" -o -name "*.fip" -o -name "*.ubi" -o -name "*rootfs.tar.gz" \) -exec rm -f {} +
fi

group_start "正在下载依赖包"
log_info "执行 make download..."
make download -j$(($(nproc) * 2))
group_end

group_start "正在编译固件 (这可能需要较长时间)"
log_info "使用多核编译: -j$(($(nproc) + 1))"
if make -j$(($(nproc) + 1)); then
    log_success "固件编译成功！"
else
    log_warn "多核编译失败，尝试单核详细输出模式..."
    make -j1 V=s
fi
group_end

FIRMWARE_DIR="$BASE_PATH/../firmware"
\rm -rf "$FIRMWARE_DIR"
mkdir -p "$FIRMWARE_DIR"
find "$TARGET_DIR" -type f \( -name "*.bin" -o -name "*.manifest" -o -name "*efi.img.gz" -o -name "*.itb" -o -name "*.fip" -o -name "*.ubi" -o -name "*rootfs.tar.gz" \) -exec cp -f {} "$FIRMWARE_DIR/" \;
\rm -f "$BASE_PATH/../firmware/Packages.manifest" 2>/dev/null

if [[ -d action_build ]]; then
    make clean
fi
