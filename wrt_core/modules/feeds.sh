#!/usr/bin/env bash

update_feeds() {
    log_info "更新 feeds 配置..."
    local FEEDS_PATH="$BUILD_DIR/$FEEDS_CONF"
    if [[ -f "$BUILD_DIR/feeds.conf" ]]; then
        FEEDS_PATH="$BUILD_DIR/feeds.conf"
    fi
    sed -i '/^#/d' "$FEEDS_PATH"
    sed -i '/packages_ext/d' "$FEEDS_PATH"

    if ! grep -q "small-package" "$FEEDS_PATH"; then
        [ -z "$(tail -c 1 "$FEEDS_PATH")" ] || echo "" >>"$FEEDS_PATH"
        echo "src-git small8 https://github.com/kenzok8/jell" >>"$FEEDS_PATH"
    fi

    if ! grep -q "openwrt-passwall" "$FEEDS_PATH"; then
        [ -z "$(tail -c 1 "$FEEDS_PATH")" ] || echo "" >>"$FEEDS_PATH"
        echo "src-git passwall https://github.com/Openwrt-Passwall/openwrt-passwall;main" >>"$FEEDS_PATH"
    fi
    
    if ! grep -q "openwrt_bandix" "$FEEDS_PATH"; then
        [ -z "$(tail -c 1 "$FEEDS_PATH")" ] || echo "" >>"$FEEDS_PATH"
        echo 'src-git openwrt_bandix https://github.com/timsaya/openwrt-bandix.git;main' >>"$FEEDS_PATH"
    fi

    if ! grep -q "luci_app_bandix" "$FEEDS_PATH"; then
        [ -z "$(tail -c 1 "$FEEDS_PATH")" ] || echo "" >>"$FEEDS_PATH"
        echo 'src-git luci_app_bandix https://github.com/timsaya/luci-app-bandix.git;main' >>"$FEEDS_PATH"
    fi

    if ! grep -q "nikki" "$FEEDS_PATH"; then
        [ -z "$(tail -c 1 "$FEEDS_PATH")" ] || echo "" >>"$FEEDS_PATH"
        echo 'src-git nikki https://github.com/nikkinikki-org/OpenWrt-nikki.git;main' >>"$FEEDS_PATH"
    fi

    if [ ! -f "$BUILD_DIR/include/bpf.mk" ]; then
        touch "$BUILD_DIR/include/bpf.mk"
    fi

    log_info "执行 feeds update..."
    ./scripts/feeds update -a
}

install_feeds() {
    log_info "执行 feeds install..."
    ./scripts/feeds update -i
    for dir in $BUILD_DIR/feeds/*; do
        if [ -d "$dir" ] && [[ ! "$dir" == *.tmp ]] && [[ ! "$dir" == *.index ]] && [[ ! "$dir" == *.targetindex ]]; then
            local feed_name=$(basename "$dir")
            log_info "正在安装 feed: $feed_name"
            if [[ "$feed_name" == "small8" ]]; then
                install_small8
                install_fullconenat
            elif [[ "$feed_name" == "passwall" ]]; then
                install_passwall
            elif [[ "$feed_name" == "nikki" ]]; then
                install_nikki
            else
                ./scripts/feeds install -f -ap "$feed_name" > /dev/null
            fi
        fi
    done
}
