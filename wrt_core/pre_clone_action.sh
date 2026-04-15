#!/usr/bin/env bash

# Determine wrt_core path
if [ -d "wrt_core" ]; then
    WRT_CORE_PATH="wrt_core"
elif [ -d "../wrt_core" ]; then
    WRT_CORE_PATH="../wrt_core"
else
    # Fallback to script directory if wrt_core is current dir or relative
    WRT_CORE_PATH=$(dirname "$0")
fi

BASE_PATH=$(cd "$WRT_CORE_PATH" && pwd)

Dev=$1

INI_FILE="$BASE_PATH/compilecfg/$Dev.ini"

if [[ ! -f $INI_FILE ]]; then
    echo "INI file not found: $INI_FILE"
    exit 1
fi

read_ini_by_key() {
    local key=$1
    awk -F"=" -v key="$key" '$1 == key {print $2}' "$INI_FILE"
}

REPO_URL=$(read_ini_by_key "REPO_URL")
REPO_BRANCH=$(read_ini_by_key "REPO_BRANCH")
REPO_BRANCH=${REPO_BRANCH:-main}
# GitHub Actions usually runs in root of repo, so build dir should be relative to repo root
# We need to construct absolute path or ensure context is correct.
# Assuming this script is run from repo root or wrt_core.
# Let's use relative path "action_build" next to wrt_core if possible or just use what works.
# Original script used BASE_PATH/action_build.
BUILD_DIR="$BASE_PATH/../action_build"

echo $REPO_URL $REPO_BRANCH
# Write flag one level up from wrt_core (repo root usually)
echo "$REPO_URL/$REPO_BRANCH" >"$BASE_PATH/../repo_flag"
git clone --depth 1 -b $REPO_BRANCH $REPO_URL $BUILD_DIR

# GitHub Action 移除国内下载源
PROJECT_MIRRORS_FILE="$BUILD_DIR/scripts/projectsmirrors.json"

if [ -f "$PROJECT_MIRRORS_FILE" ]; then
    sed -i '/.cn\//d; /tencent/d; /aliyun/d' "$PROJECT_MIRRORS_FILE"
fi

# 修复 lucky 插件补丁文件名兼容性 (解决 Makefile ARCH 变量丢失导致的文件名匹配失败)
PATCH_PATH="$BASE_PATH/patches"
if [ -d "$PATCH_PATH" ]; then
    echo "Processing lucky patches for compatibility..."
    case "$Dev" in
        *ipq60xx*|*ipq807x*|*aarch64*)
            L_ARCH="arm64"
            ;;
        *x64*|*wyse_3040*|*x86_64*)
            L_ARCH="x86_64"
            ;;
        *armv7*|*p2w_r619ac*)
            L_ARCH="armv7"
            ;;
        *)
            L_ARCH="" # 默认不处理
            ;;
    esac

    if [ -n "$L_ARCH" ]; then
        echo "Linking lucky patch for $Dev (arch: $L_ARCH)"
        # 建立链接：将带架构名的文件链接到 Makefile 寻找的空架构名路径 lucky_..._Linux__wanji.tar.gz
        ln -sf "lucky_2.27.2_Linux_${L_ARCH}_wanji.tar.gz" "$PATCH_PATH/lucky_2.27.2_Linux__wanji.tar.gz"
    fi
fi
