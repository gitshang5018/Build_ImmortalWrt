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

var callInitAction = rpc.declare({
	object: 'luci',
	method: 'setInitAction',
	params: ['name', 'action'],
	expect: { result: false }
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

		// 拦截 Save & Apply，在保存 UCI 之后自动触发服务重启
		m.handleSaveApply = function(ev, mode) {
			return this.handleSave(ev).then(function() {
				return callInitAction('aerowrt', 'restart');
			}).then(function() {
				return new Promise(function(resolve) { setTimeout(resolve, 1500); });
			}).then(function() {
				window.location.reload();
			});
		};

		s = m.section(form.NamedSection, 'main', 'aerowrt', _('服务控制与设置'));

		// 挂载全局操作函数
		window.aerowrtAction = function(act, btn) {
			if (btn) {
				btn.disabled = true;
				btn.innerText = _('正在执行中...');
			}
			callInitAction('aerowrt', act).then(function() {
				setTimeout(function() {
					window.location.reload();
				}, 1500);
			}).catch(function(err) {
				alert(_('操作失败: ') + (err.message || err));
				if (btn) btn.disabled = false;
			});
		};

		o = s.option(form.DummyValue, '_status', _('运行状态'));
		o.rawhtml = true;
		o.cfgvalue = function() {
			var port = uci.get('aerowrt', 'main', 'port') || '9099';
			var host = window.location.hostname;
			var url = 'http://' + host + ':' + port;

			if (isRunning) {
				return '<span style="color: #2e7d32; font-weight: bold; font-size: 14px;">🟢 ' + _('正在运行中') + '</span>' +
					'<div style="margin-top: 12px; display: flex; gap: 10px; align-items: center; flex-wrap: wrap;">' +
					'<a href="' + url + '" target="_blank" class="cbi-button cbi-button-apply" style="display:inline-block; text-decoration:none; padding: 7px 18px; font-weight: bold; font-size: 14px; border-radius: 6px;">🚀 ' + _('打开 AeroWrt 仪表盘') + ' (' + url + ')</a>' +
					'<button type="button" class="cbi-button cbi-button-reset" style="padding: 7px 14px; font-size: 13px;" onclick="window.aerowrtAction(\'restart\', this)">🔄 ' + _('重启服务') + '</button>' +
					'<button type="button" class="cbi-button cbi-button-remove" style="padding: 7px 14px; font-size: 13px;" onclick="window.aerowrtAction(\'stop\', this)">⏹️ ' + _('停止服务') + '</button>' +
					'</div>';
			} else {
				return '<span style="color: #c62828; font-weight: bold; font-size: 14px;">🔴 ' + _('已停止') + '</span>' +
					'<div style="margin-top: 12px; display: flex; gap: 10px; align-items: center; flex-wrap: wrap;">' +
					'<button type="button" class="cbi-button cbi-button-apply" style="padding: 7px 18px; font-weight: bold; font-size: 14px; border-radius: 6px;" onclick="window.aerowrtAction(\'start\', this)">▶️ ' + _('启动服务') + '</button>' +
					'<span style="color: #666; font-size: 12px;">' + _('（点击上方按钮直接启动，或勾选下方“启用服务”后点击页面底部的“保存并应用”）') + '</span>' +
					'</div>';
			}
		};

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
