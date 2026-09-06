#
# OpenWrt package Makefile for v2rayn-wrt
#

include $(TOPDIR)/rules.mk

PKG_NAME:=v2rayn-wrt
PKG_VERSION:=1.0.0
PKG_RELEASE:=1

PKG_LICENSE:=MIT
PKG_MAINTAINER:=v2rayN-Wrt Developers

PKG_BUILD_DEPENDS:=golang/host
PKG_BUILD_PARALLEL:=1

GO_PKG:=v2rayn-wrt

include $(INCLUDE_DIR)/package.mk
include $(TOPDIR)/feeds/packages/lang/golang/golang-package.mk

define Package/v2rayn-wrt
  SECTION:=net
  CATEGORY:=Network
  SUBMENU:=Web Servers/Proxies
  TITLE:=v2rayN-Wrt Modern Transparent Proxy Suite for OpenWrt
  URL:=https://github.com/gitshang5018/Build_ImmortalWrt
  DEPENDS:=$(GO_ARCH_DEPENDS) +sing-box +kmod-tun +ca-bundle +mosdns
endef

define Package/v2rayn-wrt/description
  A modern, lightweight, standalone WebUI transparent proxy suite for OpenWrt,
  inspired by v2rayN, with Sing-box core, strategy groups, chain proxying,
  and seamless MosDNS integration.
endef

define Package/v2rayn-wrt/install
	$(call GoPackage/Package/Install/Bin,$(PKG_INSTALL_DIR))

	$(INSTALL_DIR) $(1)/usr/bin
	$(INSTALL_BIN) $(PKG_INSTALL_DIR)/usr/bin/v2rayn-wrt $(1)/usr/bin/v2rayn-wrt

	$(INSTALL_DIR) $(1)/etc/init.d
	$(INSTALL_BIN) ./files/v2rayn-wrt.init $(1)/etc/init.d/v2rayn-wrt

	$(INSTALL_DIR) $(1)/etc/config
	$(INSTALL_CONF) ./files/v2rayn-wrt.config $(1)/etc/config/v2rayn-wrt
endef

$(eval $(call GoPackage/GoSubMenuAnnotation,v2rayn-wrt))
$(eval $(call BuildPackage,v2rayn-wrt))
