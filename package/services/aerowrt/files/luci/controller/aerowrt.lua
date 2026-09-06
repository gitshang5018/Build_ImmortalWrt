module("luci.controller.aerowrt", package.seeall)

function index()
	if not nixio.fs.access("/etc/config/aerowrt") then
		return
	end

	entry({"admin", "services", "aerowrt"}, cbi("aerowrt"), _("AeroWrt"), 50).dependent = true
end
