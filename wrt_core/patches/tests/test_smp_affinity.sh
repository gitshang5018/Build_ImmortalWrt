#!/bin/bash
set -e

# 显式指定 /tmp: Git Bash 的默认临时目录是 Windows 风格路径
TMP_DIR=$(mktemp -d /tmp/wb-affinity-test.XXXXXX 2>/dev/null) || TMP_DIR=$(mktemp -d)
trap 'rm -rf "$TMP_DIR"' EXIT

mkdir -p "$TMP_DIR/irq"

# 构造假 /proc/interrupts: 4 个 QCN9074 MSI-X 向量 + 1 个有线 EDMA 中断 + 1 个无关中断
cat > "$TMP_DIR/interrupts" <<'EOF'
           CPU0       CPU1       CPU2       CPU3
 12:       1000          0          0          0     GICv3  39 Level     arch_timer
 45:          0        500          0          0     PCI-MSI 524288-edge      ath11k_pci
 46:          0          0        500          0     PCI-MSI 524289-edge      ath11k_pci
 47:          0          0          0        500     PCI-MSI 524290-edge      ath11k_pci
 48:          0          0          0          0     PCI-MSI 524291-edge      ath11k_pci
 60:          0          0          0          0     GICv3 100 Level     edma_rxdesc
EOF

for irq in 12 45 46 47 48 60; do
	mkdir -p "$TMP_DIR/irq/$irq"
	echo 1 > "$TMP_DIR/irq/$irq/smp_affinity"
done

echo "=== 测试 1: QCN9074 多个 MSI-X 向量在 CPU0/CPU1 间交替 ==="
INTERRUPTS_FILE="$TMP_DIR/interrupts" PROC_IRQ_DIR="$TMP_DIR/irq" bash -c '
	. wrt_core/patches/smp_affinity
	enable_log=false
	enable_affinity
'

read_mask() {
	tr -d ' \n' < "$TMP_DIR/irq/$1/smp_affinity"
}

[ "$(read_mask 45)" = "1" ] || { echo "FAIL: 第 1 个 ath11k_pci 向量应绑 CPU0 (掩码 1), 实际 $(read_mask 45)"; exit 1; }
[ "$(read_mask 46)" = "2" ] || { echo "FAIL: 第 2 个 ath11k_pci 向量应绑 CPU1 (掩码 2), 实际 $(read_mask 46)"; exit 1; }
[ "$(read_mask 47)" = "1" ] || { echo "FAIL: 第 3 个 ath11k_pci 向量应轮转回 CPU0, 实际 $(read_mask 47)"; exit 1; }
[ "$(read_mask 48)" = "2" ] || { echo "FAIL: 第 4 个 ath11k_pci 向量应轮转回 CPU1, 实际 $(read_mask 48)"; exit 1; }

echo "=== 测试 2: 有线 EDMA 绑核不受无线改动影响 ==="
[ "$(read_mask 60)" = "2" ] || { echo "FAIL: edma_rxdesc 应绑 CPU1 (掩码 2), 实际 $(read_mask 60)"; exit 1; }
[ "$(read_mask 12)" = "1" ] || { echo "FAIL: 无关中断不应被改写, 实际 $(read_mask 12)"; exit 1; }

echo "=== 测试 3: 无 PCIe 无线网卡时静默跳过 (如 AX1800 Pro 亚瑟) ==="
grep -v "ath11k_pci" "$TMP_DIR/interrupts" > "$TMP_DIR/interrupts_no_pci"
echo 1 > "$TMP_DIR/irq/45/smp_affinity"
INTERRUPTS_FILE="$TMP_DIR/interrupts_no_pci" PROC_IRQ_DIR="$TMP_DIR/irq" bash -c '
	. wrt_core/patches/smp_affinity
	enable_log=false
	enable_affinity
' || { echo "FAIL: 无 ath11k_pci 时脚本不应报错退出"; exit 1; }
[ "$(read_mask 45)" = "1" ] || { echo "FAIL: 无匹配时不应写入任何 smp_affinity"; exit 1; }

echo "PASS: test_smp_affinity (QCN9074 双核交替与有线绑核隔离验证通过)"
