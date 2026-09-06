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

	$(INSTALL_DIR) $(1)/usr/share/luci/menu.d
	$(INSTALL_DATA) ./files/luci/menu.d/* $(1)/usr/share/luci/menu.d/

	$(INSTALL_DIR) $(1)/usr/share/rpcd/acl.d
	$(INSTALL_DATA) ./files/luci/acl.d/* $(1)/usr/share/rpcd/acl.d/

	$(INSTALL_DIR) $(1)/www/luci-static/resources/view/aerowrt
	$(INSTALL_DATA) ./files/luci/view/overview.js $(1)/www/luci-static/resources/view/aerowrt/overview.js

	$(INSTALL_DIR) $(1)/usr/lib/lua/luci/controller
	$(INSTALL_DATA) ./files/luci/controller/aerowrt.lua $(1)/usr/lib/lua/luci/controller/aerowrt.lua

	$(INSTALL_DIR) $(1)/usr/lib/lua/luci/model/cbi
	$(INSTALL_DATA) ./files/luci/cbi/aerowrt.lua $(1)/usr/lib/lua/luci/model/cbi/aerowrt.lua
endef

$(eval $(call GoPackage/GoSubMenuAnnotation,aerowrt))
$(eval $(call BuildPackage,aerowrt))
