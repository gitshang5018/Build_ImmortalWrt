# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Logging functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

is_build_device() {
    [[ "${BUILD_DEVICE:-}" == "$1" ]]
}

group_start() {
    if [[ -n "$GITHUB_ACTIONS" ]]; then
        echo "::group::$1"
    else
        echo -e "${CYAN}==> $1${NC}"
    fi
}

group_end() {
    if [[ -n "$GITHUB_ACTIONS" ]]; then
        echo "::endgroup::"
    fi
}

clone_repo() {
    group_start "正在克隆仓库"
    if [[ ! -d "$BUILD_DIR" ]]; then
        log_info "克隆仓库: $REPO_URL 分支: $REPO_BRANCH"
        if ! git clone --depth 1 -b "$REPO_BRANCH" "$REPO_URL" "$BUILD_DIR"; then
            log_error "错误：克隆仓库 $REPO_URL 失败"
            exit 1
        fi
    fi
    group_end
}

clean_up() {
    group_start "正在清理环境"
    if [[ ! -d "$BUILD_DIR" ]]; then
        log_warn "编译目录 $BUILD_DIR 不存在"
        group_end
        return
    fi
    cd "$BUILD_DIR"
    if [[ -f ".config" ]]; then
        \rm -f ".config"
    fi
    if [[ -d "tmp" ]]; then
        \rm -rf "tmp"
    fi
    if [[ -d "logs" ]]; then
        \rm -rf "logs/*"
    fi
    if [[ -d "feeds" ]]; then
        ./scripts/feeds clean > /dev/null 2>&1
    fi
    mkdir -p "tmp"
    echo "1" >"tmp/.build"
    log_success "环境清理完成"
    group_end
}

reset_feeds_conf() {
    group_start "正在重置仓库状态"
    git reset --hard "origin/$REPO_BRANCH"
    git clean -f -d
    git pull
    if [[ $COMMIT_HASH != "none" ]]; then
        log_info "检出指定提交: $COMMIT_HASH"
        git checkout "$COMMIT_HASH"
    fi
    group_end
}
