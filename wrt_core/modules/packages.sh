#!/usr/bin/env bash

remove_unwanted_packages() {
    local luci_packages=(
        "luci-app-passwall" "luci-app-ddns-go" "luci-app-rclone"
        "luci-app-vssr" "luci-app-daed" "luci-app-dae" "luci-app-alist" "luci-app-homeproxy"
        "luci-app-haproxy-tcp" "luci-app-mihomo" "luci-app-appfilter"
        "luci-app-msd_lite" "luci-app-adguardhome"
    )
    local packages_net=(
        "haproxy" "xray-core" "xray-plugin" "dns2socks" "alist" "hysteria"
        "mosdns" "adguardhome" "ddns-go" "naiveproxy" "shadowsocks-rust"
        "sing-box" "v2ray-core" "v2ray-geodata" "v2ray-plugin" "tuic-client"
        "chinadns-ng" "ipt2socks" "tcping" "trojan-plus" "simple-obfs" "shadowsocksr-libev"
        "dae" "daed" "mihomo" "geoview" "tailscale" "open-app-filter" "msd_lite"
    )
    local packages_utils=(
        "cups"
    )
    local small8_packages=(
        "ppp" "firewall" "dae" "daed" "daed-next" "libnftnl" "nftables" "dnsmasq" "luci-app-alist"
        "alist" "opkg" "smartdns" "luci-app-smartdns" "easytier"
        "v2ray-geodata" "v2dat" "mosdns" "luci-app-mosdns"
    )

    for pkg in "${luci_packages[@]}"; do
        if [[ -d ./feeds/luci/applications/$pkg ]]; then
            \rm -rf ./feeds/luci/applications/$pkg
        fi
        if [[ -d ./feeds/luci/themes/$pkg ]]; then
            \rm -rf ./feeds/luci/themes/$pkg
        fi
    done

    for pkg in "${packages_net[@]}"; do
        if [[ -d ./feeds/packages/net/$pkg ]]; then
            \rm -rf ./feeds/packages/net/$pkg
        fi
    done

    for pkg in "${packages_utils[@]}"; do
        if [[ -d ./feeds/packages/utils/$pkg ]]; then
            \rm -rf ./feeds/packages/utils/$pkg
        fi
    done

    for pkg in "${small8_packages[@]}"; do
        if [[ -d ./feeds/small8/$pkg ]]; then
            \rm -rf ./feeds/small8/$pkg
        fi
    done

    if [[ -d ./package/istore ]]; then
        \rm -rf ./package/istore
    fi

    if [ -d "$BUILD_DIR/target/linux/qualcommax/base-files/etc/uci-defaults" ]; then
        find "$BUILD_DIR/target/linux/qualcommax/base-files/etc/uci-defaults/" -type f -name "99*.sh" -exec rm -f {} +
    fi
}

update_golang() {
    if [[ -d ./feeds/packages/lang/golang ]]; then
        echo "正在更新 golang 软件包..."
        \rm -rf ./feeds/packages/lang/golang
        if ! git clone --depth 1 -b $GOLANG_BRANCH $GOLANG_REPO ./feeds/packages/lang/golang; then
            echo "错误：克隆 golang 仓库 $GOLANG_REPO 失败" >&2
            exit 1
        fi
    fi
}

update_node() {
    if [[ -d ./feeds/packages/lang/node ]]; then
        echo "正在集成预编译 Node.js 软件包 (锁定为原生 ipk 格式)..."
        \rm -rf ./feeds/packages/lang/node
        # 强制使用 packages-24.10 分支。该分支使用标准的 .ipk 格式，原生兼容 opkg 及标准 tar，
        # 彻底避免了 packages-25.12 分支中 Alpine ADB v3 格式 (.apk) 导致的无法解压问题。
        if ! git clone --depth 1 -b packages-24.10 $NODE_PREBUILT_REPO ./feeds/packages/lang/node; then
            echo "错误：克隆预编译 Node.js 仓库 $NODE_PREBUILT_REPO 失败" >&2
            exit 1
        fi
        echo "Node.js 预编译包已成功切换至兼容模式 (packages-24.10 分支)"
    fi
}

update_mosdns() {
    local mosdns_repo="https://github.com/sbwml/luci-app-mosdns"
    local mosdns_branch="v5"
    local geodata_repo="https://github.com/sbwml/v2ray-geodata"
    local pkg_path
    local search_dir
    local remove_paths=(
        "./package/mosdns"
        "./package/v2ray-geodata"
        "./package/feeds/small8/mosdns"
        "./package/feeds/small8/luci-app-mosdns"
        "./package/feeds/small8/v2ray-geodata"
        "./package/feeds/small8/v2dat"
        "./feeds/small8/mosdns"
        "./feeds/small8/luci-app-mosdns"
        "./feeds/small8/v2ray-geodata"
        "./feeds/small8/v2dat"
        "./feeds/packages/net/mosdns"
        "./feeds/packages/net/v2ray-geodata"
    )

    log_info "切换 MosDNS 到 sbwml/luci-app-mosdns v5..."

    for pkg_path in "${remove_paths[@]}"; do
        if [[ -e "$pkg_path" || -L "$pkg_path" ]]; then
            \rm -rf "$pkg_path"
        fi
    done

    for search_dir in ./feeds ./package; do
        if [[ -d "$search_dir" ]]; then
            find "$search_dir" -type f \( \
                -path "*/v2ray-geodata/Makefile" -o \
                -path "*/v2dat/Makefile" -o \
                -path "*/mosdns/Makefile" -o \
                -path "*/luci-app-mosdns/Makefile" \
            \) -exec \rm -f {} +
        fi
    done

    if ! git clone --depth 1 -b "$mosdns_branch" "$mosdns_repo" ./package/mosdns; then
        log_error "错误：克隆 MosDNS 仓库 $mosdns_repo 失败"
        exit 1
    fi

    if [[ "${BUILD_DEVICE:-}" == "gehua_ghl-r-001_immwrt" ]]; then
        sed -i -E 's/[[:space:]]+\+curl//g; s/[[:space:]]+\+v2ray-geoip//g; s/[[:space:]]+\+v2ray-geosite//g; s/[[:space:]]+\+v2dat//g' ./package/mosdns/luci-app-mosdns/Makefile
        log_info "Gehua 32M profile: stripped MosDNS geodata dependencies."
        log_success "MosDNS 宸插垏鎹㈠埌 sbwml/luci-app-mosdns v5"
        return 0
    fi

    if ! git clone --depth 1 "$geodata_repo" ./package/v2ray-geodata; then
        log_error "错误：克隆 v2ray-geodata 仓库 $geodata_repo 失败"
        exit 1
    fi

    log_success "MosDNS 已切换到 sbwml/luci-app-mosdns v5"
}

install_small8() {
    ./scripts/feeds install -p small8 -f xray-core xray-plugin dns2tcp dns2socks haproxy hysteria \
        naiveproxy shadowsocks-rust sing-box v2ray-core geoview v2ray-plugin \
        tuic-client chinadns-ng ipt2socks tcping trojan-plus simple-obfs shadowsocksr-libev \
        adguardhome luci-app-adguardhome ddns-go \
        luci-app-ddns-go taskd luci-lib-xterm luci-lib-taskd luci-app-cloudflarespeedtest netdata luci-app-netdata \
        lucky luci-app-lucky luci-app-openclash luci-app-ssr-plus luci-app-homeproxy luci-app-unblockneteasemusic luci-app-amlogic \
        tailscale luci-app-tailscale oaf open-app-filter luci-app-oaf easytier luci-app-easytier \
        msd_lite luci-app-msd_lite cups luci-app-cupsd
}

install_passwall() {
    echo "正在从官方仓库安装 luci-app-passwall..."
    ./scripts/feeds install -p passwall -f luci-app-passwall
}

install_nikki() {
    echo "正在从官方仓库安装 nikki..."
    ./scripts/feeds install -p nikki -f nikki luci-app-nikki
}

install_fullconenat() {
    if [ ! -d $BUILD_DIR/package/network/utils/fullconenat-nft ]; then
        ./scripts/feeds install -p small8 -f fullconenat-nft
    fi
    if [ ! -d $BUILD_DIR/package/network/utils/fullconenat ]; then
        ./scripts/feeds install -p small8 -f fullconenat
    fi
}

check_default_settings() {
    local settings_dir="$BUILD_DIR/package/emortal/default-settings"
    if [ -z "$(find "$BUILD_DIR/package" -type d -name "default-settings" -print -quit 2>/dev/null)" ]; then
        echo "在 $BUILD_DIR/package 中未找到 default-settings 目录，正在从 immortalwrt 仓库克隆..."
        local tmp_dir
        tmp_dir=$(mktemp -d)
        if git clone --depth 1 --filter=blob:none --sparse https://github.com/immortalwrt/immortalwrt.git "$tmp_dir"; then
            pushd "$tmp_dir" >/dev/null
            git sparse-checkout set package/emortal/default-settings
            mkdir -p "$(dirname "$settings_dir")"
            mv package/emortal/default-settings "$settings_dir"
            popd >/dev/null
            rm -rf "$tmp_dir"
            echo "default-settings 克隆并移动成功。"
        else
            echo "错误：克隆 immortalwrt 仓库失败" >&2
            rm -rf "$tmp_dir"
            exit 1
        fi
    fi
}

add_ax6600_led() {
    local athena_led_dir="$BUILD_DIR/package/emortal/luci-app-athena-led"
    local repo_url="https://github.com/NONGFAH/luci-app-athena-led.git"

    echo "正在添加 luci-app-athena-led..."
    rm -rf "$athena_led_dir" 2>/dev/null

    if ! git clone --depth=1 "$repo_url" "$athena_led_dir"; then
        echo "错误：从 $repo_url 克隆 luci-app-athena-led 仓库失败" >&2
        exit 1
    fi

    if [ -d "$athena_led_dir" ]; then
        chmod +x "$athena_led_dir/root/usr/sbin/athena-led"
        chmod +x "$athena_led_dir/root/etc/init.d/athena_led"
    else
        echo "错误：克隆操作后未找到目录 $athena_led_dir" >&2
        exit 1
    fi
}

update_homeproxy() {
    local repo_url="https://github.com/immortalwrt/homeproxy.git"
    local target_dir="$BUILD_DIR/feeds/small8/luci-app-homeproxy"

    if [ -d "$target_dir" ]; then
        echo "正在更新 homeproxy..."
        rm -rf "$target_dir"
        if ! git clone --depth 1 "$repo_url" "$target_dir"; then
            echo "错误：从 $repo_url 克隆 homeproxy 仓库失败" >&2
            exit 1
        fi
    fi
}

add_timecontrol() {
    if is_build_device "gehua_ghl-r-001_immwrt"; then
        log_info "Gehua 32M profile: skip luci-app-timecontrol update."
        return 0
    fi

    local timecontrol_dir="$BUILD_DIR/package/luci-app-timecontrol"
    local repo_url="https://github.com/sirpdboy/luci-app-timecontrol.git"
    rm -rf "$timecontrol_dir" 2>/dev/null
    echo "正在添加 luci-app-timecontrol..."
    if ! git clone --depth 1 "$repo_url" "$timecontrol_dir"; then
        echo "错误：从 $repo_url 克隆 luci-app-timecontrol 仓库失败" >&2
        exit 1
    fi
}

update_adguardhome() {
    if is_build_device "gehua_ghl-r-001_immwrt"; then
        log_info "Gehua 32M profile: skip luci-app-adguardhome update."
        return 0
    fi

    local adguardhome_dir="$BUILD_DIR/package/feeds/small8/luci-app-adguardhome"
    local repo_url="https://github.com/ZqinKing/luci-app-adguardhome.git"

    echo "正在更新 luci-app-adguardhome..."
    rm -rf "$adguardhome_dir" 2>/dev/null

    if ! git clone --depth 1 "$repo_url" "$adguardhome_dir"; then
        echo "错误：从 $repo_url 克隆 luci-app-adguardhome 仓库失败" >&2
        exit 1
    fi
}

update_lucky() {
    if is_build_device "gehua_ghl-r-001_immwrt"; then
        log_info "Gehua 32M profile: skip luci-app-lucky update."
        return 0
    fi

    local lucky_repo_url="https://github.com/gdy666/luci-app-lucky.git"
    local target_small8_dir="$BUILD_DIR/feeds/small8"
    local lucky_dir="$target_small8_dir/lucky"
    local luci_app_lucky_dir="$target_small8_dir/luci-app-lucky"

    if [ ! -d "$lucky_dir" ] || [ ! -d "$luci_app_lucky_dir" ]; then
        echo "Warning: $lucky_dir 或 $luci_app_lucky_dir 不存在，跳过 lucky 源代码更新。" >&2
    else
        local tmp_dir
        tmp_dir=$(mktemp -d)

        echo "正在从 $lucky_repo_url 稀疏检出 luci-app-lucky 和 lucky..."

        if ! git clone --depth 1 --filter=blob:none --no-checkout "$lucky_repo_url" "$tmp_dir"; then
            echo "错误：从 $lucky_repo_url 克隆仓库失败" >&2
            rm -rf "$tmp_dir"
            return 0
        fi

        pushd "$tmp_dir" >/dev/null
        git sparse-checkout init --cone
        git sparse-checkout set luci-app-lucky lucky || {
            echo "错误：稀疏检出 luci-app-lucky 或 lucky 失败" >&2
            popd >/dev/null
            rm -rf "$tmp_dir"
            return 0
        }
        git checkout --quiet

        \cp -rf "$tmp_dir/luci-app-lucky/." "$luci_app_lucky_dir/"
        \cp -rf "$tmp_dir/lucky/." "$lucky_dir/"

        popd >/dev/null
        rm -rf "$tmp_dir"
        echo "luci-app-lucky 和 lucky 源代码更新完成。"
    fi

    local lucky_conf="$BUILD_DIR/feeds/small8/lucky/files/luckyuci"
    if [ -f "$lucky_conf" ]; then
        sed -i "s/option enabled '1'/option enabled '0'/g" "$lucky_conf"
        sed -i "s/option logger '1'/option logger '0'/g" "$lucky_conf"
    fi

    local version
    version=$(find "$BASE_PATH/patches" -name "lucky_*.tar.gz" -printf "%f\n" | head -n 1 | sed -n 's/^lucky_\(.*\)_Linux.*$/\1/p')
    if [ -z "$version" ]; then
        echo "Warning: 未找到 lucky 补丁文件，跳过更新。" >&2
        return 0
    fi

    local makefile_path="$BUILD_DIR/feeds/small8/lucky/Makefile"
    if [ ! -f "$makefile_path" ]; then
        echo "Warning: lucky Makefile not found. Skipping." >&2
        return 0
    fi

    echo "正在更新 lucky Makefile..."
    local arch_fix="LUCKY_ARCH_FIXED=\$(LUCKY_ARCH); [ \"\$(LUCKY_ARCH)\" = \"aarch64\" ] \&\& LUCKY_ARCH_FIXED=\"arm64\";"
    local patch_line="\\t$arch_fix [ -f \$(TOPDIR)/../wrt_core/patches/lucky_${version}_Linux_\${LUCKY_ARCH_FIXED}_wanji.tar.gz ] && install -Dm644 \$(TOPDIR)/../wrt_core/patches/lucky_${version}_Linux_\${LUCKY_ARCH_FIXED}_wanji.tar.gz \$(PKG_BUILD_DIR)/\$(PKG_NAME)_\$(PKG_VERSION)_Linux_\$(LUCKY_ARCH).tar.gz"

    if grep -q "Build/Prepare" "$makefile_path"; then
        sed -i "/Build\\/Prepare/a\\$patch_line" "$makefile_path"
        sed -i '/wget/d' "$makefile_path"
        echo "lucky Makefile 更新完成。"
    else
        echo "Warning: lucky Makefile 中未找到 'Build/Prepare'。跳过。" >&2
    fi
}

update_smartdns() {
    if is_build_device "gehua_ghl-r-001_immwrt"; then
        log_info "Gehua 32M profile: skip smartdns update."
        return 0
    fi

    local SMARTDNS_REPO="https://github.com/ZqinKing/openwrt-smartdns.git"
    local SMARTDNS_DIR="$BUILD_DIR/feeds/packages/net/smartdns"
    local LUCI_APP_SMARTDNS_REPO="https://github.com/pymumu/luci-app-smartdns.git"
    local LUCI_APP_SMARTDNS_DIR="$BUILD_DIR/feeds/luci/applications/luci-app-smartdns"

    echo "正在更新 smartdns..."
    rm -rf "$SMARTDNS_DIR"
    if ! git clone --depth=1 "$SMARTDNS_REPO" "$SMARTDNS_DIR"; then
        echo "错误：从 $SMARTDNS_REPO 克隆 smartdns 仓库失败" >&2
        exit 1
    fi

    install -Dm644 "$BASE_PATH/patches/100-smartdns-optimize.patch" "$SMARTDNS_DIR/patches/100-smartdns-optimize.patch"
    sed -i '/define Build\/Compile\/smartdns-ui/,/endef/s/CC=\$(TARGET_CC)/CC="\$(TARGET_CC_NOCACHE)"/' "$SMARTDNS_DIR/Makefile"

    # 强制跳过 hash 检查，解决 make download 失败的问题
    sed -i 's/PKG_MIRROR_HASH:=.*/PKG_MIRROR_HASH:=skip/g' "$SMARTDNS_DIR/Makefile"
    sed -i 's/MIRROR_HASH:=.*/MIRROR_HASH:=skip/g' "$SMARTDNS_DIR/Makefile"
    sed -i 's/PKG_HASH:=.*/PKG_HASH:=skip/g' "$SMARTDNS_DIR/Makefile"
    sed -i 's/HASH:=.*/HASH:=skip/g' "$SMARTDNS_DIR/Makefile"

    echo "正在更新 luci-app-smartdns..."
    rm -rf "$LUCI_APP_SMARTDNS_DIR"
    if ! git clone --depth=1 "$LUCI_APP_SMARTDNS_REPO" "$LUCI_APP_SMARTDNS_DIR"; then
        echo "错误：从 $LUCI_APP_SMARTDNS_REPO 克隆 luci-app-smartdns 仓库失败" >&2
        exit 1
    fi
}

update_diskman() {
    if is_build_device "gehua_ghl-r-001_immwrt"; then
        log_info "Gehua 32M profile: skip diskman update."
        return 0
    fi

    local path="$BUILD_DIR/feeds/luci/applications/luci-app-diskman"
    local repo_url="https://github.com/lisaac/luci-app-diskman.git"
    if [ -d "$path" ]; then
        echo "正在更新 diskman..."
        cd "$BUILD_DIR/feeds/luci/applications" || return
        \rm -rf "luci-app-diskman"

        if ! git clone --filter=blob:none --no-checkout "$repo_url" diskman; then
            echo "错误：从 $repo_url 克隆 diskman 仓库失败" >&2
            exit 1
        fi
        cd diskman || return

        git sparse-checkout init --cone
        git sparse-checkout set applications/luci-app-diskman || return

        git checkout --quiet

        mv applications/luci-app-diskman ../luci-app-diskman || return
        cd .. || return
        \rm -rf diskman
        cd "$BUILD_DIR"

        sed -i 's/fs-ntfs /fs-ntfs3 /g' "$path/Makefile"
        sed -i '/ntfs-3g-utils /d' "$path/Makefile"
    fi
}

_sync_luci_lib_docker() {
    local lib_path="$BUILD_DIR/feeds/luci/libs/luci-lib-docker"
    local repo_url="https://github.com/lisaac/luci-lib-docker.git"
    
    if [ ! -d "$lib_path" ]; then
        echo "正在同步 luci-lib-docker..."
        mkdir -p "$BUILD_DIR/feeds/luci/libs" || return
        cd "$BUILD_DIR/feeds/luci/libs" || return
        
        if ! git clone --filter=blob:none --no-checkout "$repo_url" luci-lib-docker-tmp; then
            echo "错误：从 $repo_url 克隆 luci-lib-docker 仓库失败" >&2
            exit 1
        fi
        cd luci-lib-docker-tmp || return
        
        git sparse-checkout init --cone
        git sparse-checkout set collections/luci-lib-docker || return
        
        git checkout --quiet
        
        mv collections/luci-lib-docker ../luci-lib-docker || return
        cd .. || return
        \rm -rf luci-lib-docker-tmp
        cd "$BUILD_DIR"
        echo "luci-lib-docker 同步完成"
    fi
}

update_dockerman() {
    local path="$BUILD_DIR/feeds/luci/applications/luci-app-dockerman"
    local repo_url="https://github.com/lisaac/luci-app-dockerman.git"

    if [ -d "$path" ]; then
        echo "正在更新 dockerman..."
        _sync_luci_lib_docker || return
        
        cd "$BUILD_DIR/feeds/luci/applications" || return
        \rm -rf "luci-app-dockerman"

        if ! git clone --filter=blob:none --no-checkout "$repo_url" dockerman; then
            echo "错误：从 $repo_url 克隆 dockerman 仓库失败" >&2
            exit 1
        fi
        cd dockerman || return

        git sparse-checkout init --cone
        git sparse-checkout set applications/luci-app-dockerman || return

        git checkout --quiet

        mv applications/luci-app-dockerman ../luci-app-dockerman || return
        cd .. || return
        \rm -rf dockerman
        cd "$BUILD_DIR"

        if declare -F docker_stack_sync_dockerman_nftables_compat >/dev/null 2>&1; then
            docker_stack_sync_dockerman_nftables_compat "$BUILD_DIR" "0" || return 1
        fi

        echo "dockerman 更新完成"
    fi
}

add_quickfile() {
    if is_build_device "gehua_ghl-r-001_immwrt"; then
        log_info "Gehua 32M profile: skip luci-app-quickfile update."
        return 0
    fi

    local repo_url="https://github.com/sbwml/luci-app-quickfile.git"
    local target_dir="$BUILD_DIR/package/emortal/quickfile"
    if [ -d "$target_dir" ]; then
        rm -rf "$target_dir"
    fi
    echo "正在添加 luci-app-quickfile..."
    if ! git clone --depth 1 "$repo_url" "$target_dir"; then
        echo "错误：从 $repo_url 克隆 luci-app-quickfile 仓库失败" >&2
        exit 1
    fi

    local makefile_path="$target_dir/quickfile/Makefile"
    if [ -f "$makefile_path" ]; then
        sed -i '/\t\$(INSTALL_BIN) \$(PKG_BUILD_DIR)\/quickfile-\$(ARCH_PACKAGES)/c\
\tif [ "\$(ARCH_PACKAGES)" = "x86_64" ]; then \\\
\t\t\$(INSTALL_BIN) \$(PKG_BUILD_DIR)\/quickfile-x86_64 \$(1)\/usr\/bin\/quickfile; \\\
\telse \\\
\t\t\$(INSTALL_BIN) \$(PKG_BUILD_DIR)\/quickfile-aarch64_generic \$(1)\/usr\/bin\/quickfile; \\\
\tfi' "$makefile_path"
    fi
}

update_argon() {
    if is_build_device "gehua_ghl-r-001_immwrt"; then
        log_info "Gehua 32M profile: skip luci-theme-argon update."
        return 0
    fi

    local repo_url="https://github.com/ZqinKing/luci-theme-argon.git"
    local dst_theme_path="$BUILD_DIR/feeds/luci/themes/luci-theme-argon"
    local tmp_dir
    tmp_dir=$(mktemp -d)

    echo "正在更新 argon 主题..."

    if ! git clone --depth 1 "$repo_url" "$tmp_dir"; then
        echo "错误：从 $repo_url 克隆 argon 主题仓库失败" >&2
        rm -rf "$tmp_dir"
        exit 1
    fi

    rm -rf "$dst_theme_path"
    rm -rf "$tmp_dir/.git"
    mv "$tmp_dir" "$dst_theme_path"

    echo "luci-theme-argon 更新完成"
}

update_design() {
    if is_build_device "gehua_ghl-r-001_immwrt"; then
        log_info "Gehua 32M profile: skip luci-theme-design update."
        return 0
    fi

    local repo_url="https://github.com/0x676e67/luci-theme-design.git"
    local dst_theme_path="$BUILD_DIR/feeds/luci/themes/luci-theme-design"
    local tmp_dir
    tmp_dir=$(mktemp -d)

    echo "正在更新 design 主题 (JS 版本)..."

    if ! git clone --depth 1 -b js "$repo_url" "$tmp_dir"; then
        echo "错误：从 $repo_url 克隆 design 主题仓库失败" >&2
        rm -rf "$tmp_dir"
        exit 1
    fi

    rm -rf "$dst_theme_path"
    rm -rf "$tmp_dir/.git"
    mv "$tmp_dir" "$dst_theme_path"

    echo "luci-theme-design 更新完成"
}

remove_attendedsysupgrade() {
    find "$BUILD_DIR/feeds/luci/collections" -name "Makefile" | while read -r makefile; do
        if grep -q "luci-app-attendedsysupgrade" "$makefile"; then
            sed -i "/luci-app-attendedsysupgrade/d" "$makefile"
            echo "Removed luci-app-attendedsysupgrade from $makefile"
        fi
    done
}

update_package() {
    local dir=$(find "$BUILD_DIR/package" \( -type d -o -type l \) -name "$1")
    if [ -z "$dir" ]; then
        return 0
    fi
    local branch="$2"
    if [ -z "$branch" ]; then
        branch="releases"
    fi
    local mk_path="$dir/Makefile"
    if [ -f "$mk_path" ]; then
        local PKG_REPO=$(grep -oE "^PKG_GIT_URL.*github.com(/[-_a-zA-Z0-9]{1,}){2}" "$mk_path" | awk -F"/" '{print $(NF - 1) "/" $NF}')
        if [ -z "$PKG_REPO" ]; then
            PKG_REPO=$(grep -oE "^PKG_SOURCE_URL.*github.com(/[-_a-zA-Z0-9]{1,}){2}" "$mk_path" | awk -F"/" '{print $(NF - 1) "/" $NF}')
            if [ -z "$PKG_REPO" ]; then
                echo "错误：无法从 $mk_path 提取 PKG_REPO" >&2
                return 1
            fi
        fi
        local PKG_VER
        if ! PKG_VER=$(curl -fsSL "https://api.github.com/repos/$PKG_REPO/$branch" | jq -r '.[0] | .tag_name // .name'); then
            echo "错误：从 https://api.github.com/repos/$PKG_REPO/$branch 获取版本信息失败" >&2
            return 1
        fi
        if [ -n "$3" ]; then
            PKG_VER="$3"
        fi
        local PKG_VER_CLEAN
        PKG_VER_CLEAN=$(echo "$PKG_VER" | sed 's/^v//')
        if grep -q "^PKG_GIT_SHORT_COMMIT:=" "$mk_path"; then
            local PKG_GIT_URL_RAW
            PKG_GIT_URL_RAW=$(awk -F"=" '/^PKG_GIT_URL:=/ {print $NF}' "$mk_path")
            local PKG_GIT_REF_RAW
            PKG_GIT_REF_RAW=$(awk -F"=" '/^PKG_GIT_REF:=/ {print $NF}' "$mk_path")

            if [ -z "$PKG_GIT_URL_RAW" ] || [ -z "$PKG_GIT_REF_RAW" ]; then
                echo "错误：$mk_path 缺少 PKG_GIT_URL 或 PKG_GIT_REF，无法更新 PKG_GIT_SHORT_COMMIT" >&2
                return 1
            fi

            local PKG_GIT_REF_RESOLVED
            PKG_GIT_REF_RESOLVED=$(echo "$PKG_GIT_REF_RAW" | sed "s/\$(PKG_VERSION)/$PKG_VER_CLEAN/g; s/\${PKG_VERSION}/$PKG_VER_CLEAN/g")

            local PKG_GIT_REF_TAG="${PKG_GIT_REF_RESOLVED#refs/tags/}"

            local COMMIT_SHA
            local LS_REMOTE_OUTPUT
            LS_REMOTE_OUTPUT=$(git ls-remote "https://$PKG_GIT_URL_RAW" "refs/tags/${PKG_GIT_REF_TAG}" "refs/tags/${PKG_GIT_REF_TAG}^{}" 2>/dev/null)
            COMMIT_SHA=$(echo "$LS_REMOTE_OUTPUT" | awk '/\^\{\}$/ {print $1; exit}')
            if [ -z "$COMMIT_SHA" ]; then
                COMMIT_SHA=$(echo "$LS_REMOTE_OUTPUT" | awk 'NR==1{print $1}')
            fi
            if [ -z "$COMMIT_SHA" ]; then
                COMMIT_SHA=$(git ls-remote "https://$PKG_GIT_URL_RAW" "${PKG_GIT_REF_RESOLVED}^{}" 2>/dev/null | awk 'NR==1{print $1}')
            fi
            if [ -z "$COMMIT_SHA" ]; then
                COMMIT_SHA=$(git ls-remote "https://$PKG_GIT_URL_RAW" "$PKG_GIT_REF_RESOLVED" 2>/dev/null | awk 'NR==1{print $1}')
            fi
            if [ -z "$COMMIT_SHA" ]; then
                echo "错误：无法从 https://$PKG_GIT_URL_RAW 获取 $PKG_GIT_REF_RESOLVED 的提交哈希" >&2
                return 1
            fi

            local SHORT_COMMIT
            SHORT_COMMIT=$(echo "$COMMIT_SHA" | cut -c1-7)
            sed -i "s/^PKG_GIT_SHORT_COMMIT:=.*/PKG_GIT_SHORT_COMMIT:=$SHORT_COMMIT/g" "$mk_path"
        fi
        PKG_VER=$(echo "$PKG_VER" | grep -oE "[\.0-9]{1,}")

        local PKG_NAME=$(awk -F"=" '/PKG_NAME:=/ {print $NF}' "$mk_path" | grep -oE "[-_:/\$\(\)\?\.a-zA-Z0-9]{1,}")
        local PKG_SOURCE=$(awk -F"=" '/PKG_SOURCE:=/ {print $NF}' "$mk_path" | grep -oE "[-_:/\$\(\)\?\.a-zA-Z0-9]{1,}")
        local PKG_SOURCE_URL=$(awk -F"=" '/PKG_SOURCE_URL:=/ {print $NF}' "$mk_path" | grep -oE "[-_:/\$\(\)\{\}\?\.a-zA-Z0-9]{1,}")
        local PKG_GIT_URL=$(awk -F"=" '/PKG_GIT_URL:=/ {print $NF}' "$mk_path")
        local PKG_GIT_REF=$(awk -F"=" '/PKG_GIT_REF:=/ {print $NF}' "$mk_path")

        PKG_SOURCE_URL=${PKG_SOURCE_URL//\$\(PKG_GIT_URL\)/$PKG_GIT_URL}
        PKG_SOURCE_URL=${PKG_SOURCE_URL//\$\(PKG_GIT_REF\)/$PKG_GIT_REF}
        PKG_SOURCE_URL=${PKG_SOURCE_URL//\$\(PKG_NAME\)/$PKG_NAME}
        PKG_SOURCE_URL=$(echo "$PKG_SOURCE_URL" | sed "s/\${PKG_VERSION}/$PKG_VER/g; s/\$(PKG_VERSION)/$PKG_VER/g")
        PKG_SOURCE=${PKG_SOURCE//\$\(PKG_NAME\)/$PKG_NAME}
        PKG_SOURCE=${PKG_SOURCE//\$\(PKG_VERSION\)/$PKG_VER}

        local PKG_HASH
        if ! PKG_HASH=$(curl -fsSL "$PKG_SOURCE_URL""$PKG_SOURCE" | sha256sum | cut -b -64); then
            echo "错误：从 $PKG_SOURCE_URL$PKG_SOURCE 获取软件包哈希失败" >&2
            return 1
        fi

        sed -i 's/^PKG_VERSION:=.*/PKG_VERSION:='$PKG_VER'/g' "$mk_path"
        sed -i 's/^PKG_HASH:=.*/PKG_HASH:='$PKG_HASH'/g' "$mk_path"

        echo "更新软件包 $1 到 $PKG_VER $PKG_HASH"
    fi
}

fix_trojan_plus() {
    local trojan_dir="$BUILD_DIR/feeds/small8/trojan-plus"
    local makefile_path="$trojan_dir/Makefile"
    if [ -f "$makefile_path" ]; then
        echo "正在修复 trojan-plus Boost 1.86+ 适配问题 (完整补丁 & 依赖移除)..."
        
        # 1. 移除 Makefile 中的 boost-system 依赖 (Boost 1.86+ 已移除相关二进制包)
        sed -i 's/+boost-system//g' "$makefile_path"

        mkdir -p "$trojan_dir/patches"

        # 2. 修复 Boost system 链接补丁
        cat > "$trojan_dir/patches/010-fix-boost-system.patch" << 'EOF'
--- a/CMakeLists.txt
+++ b/CMakeLists.txt
@@ -174,7 +174,7 @@
     target_link_libraries(trojan ${ANDROID_MY_LIBS_LIBRARIES})
 else()
-    find_package(Boost 1.66.0 REQUIRED COMPONENTS system program_options)
+    find_package(Boost 1.66.0 REQUIRED COMPONENTS program_options)
     include_directories(${Boost_INCLUDE_DIR})
     target_link_libraries(trojan ${Boost_LIBRARIES})
     if(MSVC)
EOF

        # 3. 完整 ASIO/Timer 兼容性补丁 (用户提供)
        cat > "$trojan_dir/patches/020-fix-boost-1.86-compatibility.patch" << 'EOF'
--- a/src/core/service.cpp
+++ b/src/core/service.cpp
@@ -547,7 +547,7 @@
             int ttl         = -1;
 
             targetdst = recv_tproxy_udp_msg((int)udp_socket.native_handle(), udp_recv_endpoint,
-              boost::asio::buffer_cast<char*>(udp_read_buf.prepare(config.get_udp_recv_buf())), read_length, ttl);
+              const_cast<char*>(static_cast<const char*>(udp_read_buf.prepare(config.get_udp_recv_buf()).data())), read_length, ttl);
 
             length = read_length < 0 ? 0 : read_length;
             udp_read_buf.commit(length);
--- a/src/core/utils.cpp
+++ b/src/core/utils.cpp
@@ -59,8 +59,8 @@
         return 0;
     }
 
-    auto* dest      = boost::asio::buffer_cast<uint8_t*>(target.prepare(n));
-    const auto* src = boost::asio::buffer_cast<const uint8_t*>(append_buf.data()) + start;
+    auto* dest      = static_cast<uint8_t*>(target.prepare(n).data());
+    const auto* src = static_cast<const uint8_t*>(append_buf.data().data()) + start;
     memcpy(dest, src, n);
     target.commit(n);
     return n;
@@ -102,7 +102,7 @@
 size_t streambuf_append(boost::asio::streambuf& target, char append_char) {
     _guard;
     const size_t char_length = sizeof(char);
-    auto cp = gsl::span<char>(boost::asio::buffer_cast<char*>(target.prepare(char_length)), char_length);
+    auto cp = gsl::span<char>(static_cast<char*>(target.prepare(char_length).data()), char_length);
     cp[0]   = append_char;
     target.commit(char_length);
     return char_length;
@@ -137,7 +137,7 @@
 
 std::string_view streambuf_to_string_view(const boost::asio::streambuf& target) {
     _guard;
-    return std::string_view(boost::asio::buffer_cast<const char*>(target.data()), target.size());
+    return std::string_view(static_cast<const char*>(target.data().data()), target.size());
     _unguard;
 }
 
--- a/src/session/session.cpp
+++ b/src/session/session.cpp
@@ -40,7 +40,7 @@
     s_total_session_count--;
     _log_with_date_time_ALL((is_udp_forward_session() ? "[udp] ~" : "[tcp] ~") + string(session_name) +
                             " called, current all sessions:  " + to_string(s_total_session_count));
-};
+}
 
 int Session::get_udp_timer_timeout_val() const { return get_config().get_udp_timeout(); }
 
@@ -67,22 +69,16 @@
         udp_gc_timer_checker = time(nullptr);
     }
 
-    boost::system::error_code ec;
-    udp_gc_timer.cancel(ec);
-    if (ec) {
-        output_debug_info_ec(ec);
-        destroy();
-        return;
-    }
+    udp_gc_timer.cancel();
 
     udp_gc_timer.expires_after(chrono::seconds(timeout));
     auto self = shared_from_this();
-    udp_gc_timer.async_wait([this, self, timeout](const boost::system::error_code error) {
+    udp_gc_timer.async_wait([this, self, timeout](const boost::system::error_code& error) {
         _guard;
         if (!error) {
             auto curr = time(nullptr);
             if (curr - udp_gc_timer_checker < timeout) {
-                auto diff            = int(timeout - (curr - udp_gc_timer_checker));
+                auto diff = timeout - (curr - udp_gc_timer_checker);
                 udp_gc_timer_checker = 0;
                 udp_timer_async_wait(diff);
                 return;
@@ -90,6 +86,8 @@
 
             _log_with_date_time("session_id: " + to_string(get_session_id()) + " UDP session timeout");
             destroy();
+        } else if (error != boost::asio::error::operation_aborted) {
+            output_debug_info_ec(error);
         }
         _unguard;
     });
@@ -99,14 +97,13 @@
 
 void Session::udp_timer_cancel() {
     _guard;
+
     if (udp_gc_timer_checker == 0) {
         return;
     }
 
-    boost::system::error_code ec;
-    udp_gc_timer.cancel(ec);
-    if (ec) {
-        output_debug_info_ec(ec);
-    }
+    udp_gc_timer.cancel();
+
+    udp_gc_timer_checker = 0;
     _unguard;
 }
EOF
    fi
}
