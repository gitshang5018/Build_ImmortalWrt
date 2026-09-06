local m, s, o
local sys = require "luci.sys"

m = Map("aerowrt", translate("AeroWrt 透明代理套件"),
	translate("AeroWrt 是一款现代化、超低内存常驻的独立 WebUI 透明网关管理套件，支持出站策略组、链式代理（Detour）与本地 MosDNS 联动。"))

s = m:section(NamedSection, "main", "aerowrt", translate("服务状态与快捷入口"))
s.anonymous = true

o = s:option(DummyValue, "_status", translate("运行状态"))
o.rawhtml = true
o.cfgvalue = function()
	local is_running = (sys.call("pidof aerowrt >/dev/null") == 0)
	local port = m.uci:get("aerowrt", "main", "port") or "9099"
	local host = luci.http.getenv("SERVER_NAME") or "192.168.1.1"
	local url = "http://" .. host .. ":" .. port

	if is_running then
		return string.format('<span style="color: #2e7d32; font-weight: bold; font-size: 14px;">🟢 %s</span><div style="margin-top: 12px;"><a href="%s" target="_blank" class="cbi-button cbi-button-apply" style="display:inline-block; text-decoration:none; padding: 8px 18px; font-weight: bold; font-size: 14px; border-radius: 6px;">🚀 %s (%s)</a></div>',
			translate("正在运行中"), url, translate("打开 AeroWrt 仪表盘"), url)
	else
		return string.format('<span style="color: #c62828; font-weight: bold; font-size: 14px;">🔴 %s</span><div style="margin-top: 8px; color: #666; font-size: 12px;">%s</div>',
			translate("已停止"), translate("请确保下方“启用服务”已勾选并点击“保存并应用”，或在终端执行 /etc/init.d/aerowrt start"))
	end
end

s = m:section(NamedSection, "main", "aerowrt", translate("基础设置"))
s.anonymous = true

o = s:option(Flag, "enabled", translate("启用服务"))
o.rmempty = false
o.default = "1"

o = s:option(Value, "port", translate("WebUI 监听端口"))
o.datatype = "port"
o.default = "9099"
o.rmempty = false
o.description = translate("AeroWrt 独立前端与 REST API 服务的监听端口（默认 9099）")

o = s:option(Value, "mosdns_port", translate("本地 MosDNS 联动端口"))
o.datatype = "port"
o.default = "5335"
o.rmempty = false
o.description = translate("TUN 网卡截获的 DNS 查询将无缝转发至此端口（默认 5335）")

return m
