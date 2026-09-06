'use strict';
'require form';
'require view';
'require rpc';
'require uci';

var callServiceList = rpc.declare({
	object: 'service',
	method: 'list',
	params: ['name'],
	expect: { '': {} }
});

return view.extend({
	load: function() {
		return Promise.all([
			callServiceList('aerowrt'),
			uci.load('aerowrt')
		]);
	},

	render: function(data) {
		var serviceData = data[0] || {};
		var isRunning = false;
		if (serviceData.aerowrt && serviceData.aerowrt.instances) {
			for (var k in serviceData.aerowrt.instances) {
				if (serviceData.aerowrt.instances[k].running) {
					isRunning = true;
					break;
				}
			}
		}

		var m, s, o;

		m = new form.Map('aerowrt', _('AeroWrt 透明代理套件'),
			_('AeroWrt 是一款现代化、超低内存常驻的独立 WebUI 透明网关管理套件，支持出站策略组、链式代理与本地 MosDNS 联动。'));

		s = m.section(form.NamedSection, 'main', 'aerowrt', _('服务状态与快捷入口'));

		o = s.option(form.DummyValue, '_status', _('运行状态'));
		o.rawhtml = true;
		o.cfgvalue = function() {
			var port = uci.get('aerowrt', 'main', 'port') || '9099';
			var host = window.location.hostname;
			var url = 'http://' + host + ':' + port;

			if (isRunning) {
				return '<span style="color: #2e7d32; font-weight: bold; font-size: 14px;">🟢 ' + _('正在运行中') + '</span>' +
					'<div style="margin-top: 12px;">' +
					'<a href="' + url + '" target="_blank" class="cbi-button cbi-button-apply" style="display:inline-block; text-decoration:none; padding: 8px 18px; font-weight: bold; font-size: 14px; border-radius: 6px;">🚀 ' + _('打开 AeroWrt 仪表盘') + ' (' + url + ')</a>' +
					'</div>';
			} else {
				return '<span style="color: #c62828; font-weight: bold; font-size: 14px;">🔴 ' + _('已停止') + '</span>' +
					'<div style="margin-top: 8px; color: #666; font-size: 12px;">' + _('请确保下方“启用服务”已勾选并点击“保存并应用”，或在终端执行 /etc/init.d/aerowrt start') + '</div>';
			}
		};

		s = m.section(form.NamedSection, 'main', 'aerowrt', _('基础设置'));

		o = s.option(form.Flag, 'enabled', _('启用服务'));
		o.rmempty = false;
		o.default = '1';

		o = s.option(form.Value, 'port', _('WebUI 监听端口'));
		o.datatype = 'port';
		o.default = '9099';
		o.rmempty = false;
		o.description = _('AeroWrt 独立前端与 REST API 服务的监听端口（默认 9099）');

		o = s.option(form.Value, 'mosdns_port', _('本地 MosDNS 联动端口'));
		o.datatype = 'port';
		o.default = '5335';
		o.rmempty = false;
		o.description = _('TUN 网卡截获的 DNS 查询将无缝转发至此端口（默认 5335）');

		return m.render();
	}
});
