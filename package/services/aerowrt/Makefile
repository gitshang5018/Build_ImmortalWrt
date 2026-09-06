#
# OpenWrt package Makefile for aerowrt
#

include $(TOPDIR)/rules.mk

PKG_NAME:=aerowrt
PKG_VERSION:=1.0.0
PKG_RELEASE:=1

PKG_LICENSE:=MIT
PKG_MAINTAINER:=AeroWrt Developers

PKG_BUILD_DEPENDS:=golang/host
PKG_BUILD_PARALLEL:=1

GO_PKG:=aerowrt

include $(INCLUDE_DIR)/package.mk
include $(TOPDIR)/feeds/packages/lang/golang/golang-package.mk

define Package/aerowrt
  SECTION:=net
  CATEGORY:=Network
  SUBMENU:=Web Servers/Proxies
  TITLE:=AeroWrt Modern Transparent Proxy Suite for OpenWrt
  URL:=https://github.com/gitshang5018/Build_ImmortalWrt
  DEPENDS:=$(GO_ARCH_DEPENDS) +sing-box +kmod-tun +ca-bundle +mosdns
endef

define Package/aerowrt/description
  A modern, lightweight, standalone WebUI transparent proxy suite for OpenWrt,
  with Sing-box core, strategy groups, chain proxying, and seamless MosDNS integration.
endef

define Package/aerowrt/install
	$(call GoPackage/Package/Install/Bin,$(PKG_INSTALL_DIR))

	$(INSTALL_DIR) $(1)/usr/bin
	$(INSTALL_BIN) $(PKG_INSTALL_DIR)/usr/bin/aerowrt $(1)/usr/bin/aerowrt

	$(INSTALL_DIR) $(1)/etc/init.d
	$(INSTALL_BIN) ./files/aerowrt.init $(1)/etc/init.d/aerowrt

	$(INSTALL_DIR) $(1)/etc/config
	$(INSTALL_CONF) ./files/aerowrt.config $(1)/etc/config/aerowrt
endef

$(eval $(call GoPackage/GoSubMenuAnnotation,aerowrt))
$(eval $(call BuildPackage,aerowrt))
