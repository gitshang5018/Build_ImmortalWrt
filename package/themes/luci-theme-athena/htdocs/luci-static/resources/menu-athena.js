/**
 * menu-athena.js - LuCI JS Dynamic Sidebar Menu Engine for Athena Theme
 */

'use strict';
'require baseclass';
'require ui';

return baseclass.extend({
	__init__: function () {
		ui.menu.load().then((tree) => this.render(tree));
	},

	render: function (tree) {
		var node = tree;
		var url = '';

		this.renderModeMenu(node);

		if (L.env.dispatchpath && L.env.dispatchpath.length >= 3) {
			for (var i = 0; i < 3 && node; i++) {
				node = node.children[L.env.dispatchpath[i]];
				url = url + (url ? '/' : '') + L.env.dispatchpath[i];
			}
			if (node) {
				this.renderTabMenu(node, url);
			}
		}

		var sidebarToggle = document.querySelector('a.showSide');
		var darkMask = document.querySelector('.darkMask');
		if (sidebarToggle) {
			sidebarToggle.addEventListener('click', ui.createHandlerFn(this, 'handleSidebarToggle'));
		}
		if (darkMask) {
			darkMask.addEventListener('click', ui.createHandlerFn(this, 'handleSidebarToggle'));
		}
	},

	handleMenuExpand: function (ev) {
		var target = ev.target.closest('a');
		if (!target) return;
		var slide = target.parentNode;
		var slideMenu = slide.querySelector('.slide-menu');
		var shouldCollapse = false;

		var activeMenus = document.querySelectorAll('.main .main-left .nav > li > ul.active');
		activeMenus.forEach(function (ul) {
			ul.classList.remove('active');
			if (ul.previousElementSibling) ul.previousElementSibling.classList.remove('active');
			if (!shouldCollapse && ul === slideMenu) {
				shouldCollapse = true;
			}
		});

		if (!slideMenu) return;

		if (!shouldCollapse) {
			slideMenu.classList.add('active');
			target.classList.add('active');
			target.blur();
		}

		ev.preventDefault();
		ev.stopPropagation();
	},

	getIcon: function (name) {
		var icons = {
			status: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 3v18h18"/><path d="M18 17V9"/><path d="M13 17V5"/><path d="M8 17v-3"/></svg>',
			system: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"/></svg>',
			services: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="2" y="3" width="20" height="14" rx="2" ry="2"/><line x1="8" y1="21" x2="16" y2="21"/><line x1="12" y1="17" x2="12" y2="21"/></svg>',
			network: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M5 12.55a11 11 0 0 1 14.08 0"/><path d="M1.42 9a16 16 0 0 1 21.16 0"/><path d="M8.53 16.11a6 6 0 0 1 6.95 0"/><line x1="12" y1="20" x2="12.01" y2="20"/></svg>',
			logout: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/></svg>',
			vpn: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>',
		};
		var iconStr = icons[name] || '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polygon points="12 2 2 7 12 12 22 7 12 2"/><polyline points="2 17 12 22 22 17"/><polyline points="2 12 12 17 22 12"/></svg>';
		var span = document.createElement('span');
		span.className = 'menu-icon';
		span.innerHTML = iconStr;
		return span;
	},

	renderMainMenu: function (tree, url, level) {
		var currentLevel = (level || 0) + 1;
		var menuContainer = E('ul', { 'class': level ? 'slide-menu' : 'nav' });
		var children = ui.menu.getChildren(tree);

		if (children.length === 0 || currentLevel > 2) {
			return E([]);
		}

		for (var i = 0; i < children.length; i++) {
			var child = children[i];
			var isActive = (
				L.env.dispatchpath &&
				(L.env.dispatchpath[currentLevel] === child.name) &&
				(L.env.dispatchpath[currentLevel - 1] === tree.name)
			);

			var submenu = this.renderMainMenu(child, url + '/' + child.name, currentLevel);
			var hasChildren = submenu.children && submenu.children.length > 0;

			var slideClass = hasChildren ? 'slide' : '';
			var menuClass = hasChildren ? 'menu' : 'leaf';

			if (isActive) {
				menuContainer.classList.add('active');
				slideClass += ' active';
				menuClass += ' active';
			}

			var aElem = E('a', {
				'href': L.url(url, child.name),
				'click': (currentLevel === 1 && hasChildren) ? ui.createHandlerFn(this, 'handleMenuExpand') : null,
				'class': menuClass,
				'data-name': child.name,
				'data-title': child.title.replace(/\s+/g, '_')
			});

			if (currentLevel === 1) {
				aElem.appendChild(this.getIcon(child.name));
			}
			aElem.appendChild(document.createTextNode(_(child.title)));

			var menuItem = E('li', { 'class': slideClass }, [
				aElem,
				submenu
			]);

			menuContainer.appendChild(menuItem);
		}

		if (currentLevel === 1) {
			var mainMenuElement = document.querySelector('#mainmenu');
			if (mainMenuElement) {
				mainMenuElement.appendChild(menuContainer);
				mainMenuElement.style.display = '';
			}
		}

		return menuContainer;
	},

	renderModeMenu: function (tree) {
		var menu = document.querySelector('#modemenu');
		var children = ui.menu.getChildren(tree);

		for (var i = 0; i < children.length; i++) {
			var isActive = (L.env.requestpath && L.env.requestpath.length ? children[i].name == L.env.requestpath[0] : i == 0);
			if (i > 0 && menu) menu.appendChild(E([], ['\u00a0|\u00a0']));
			if (menu) {
				menu.appendChild(E('li', {}, [
					E('a', {
						'href': L.url(children[i].name),
						'class': isActive ? 'active' : null
					}, [_(children[i].title)])
				]));
			}
			if (isActive) {
				this.renderMainMenu(children[i], children[i].name);
			}
		}
		if (menu && menu.children.length > 1) {
			menu.style.display = '';
		}
	},

	renderTabMenu: function (tree, url, level) {
		var container = document.querySelector('#tabmenu');
		var currentLevel = (level || 0) + 1;
		var tabContainer = E('ul', { 'class': 'tabs' });
		var children = ui.menu.getChildren(tree);
		var activeNode = null;

		if (children.length === 0) return E([]);

		for (var i = 0; i < children.length; i++) {
			var child = children[i];
			var isActive = (L.env.dispatchpath && L.env.dispatchpath[currentLevel + 2] === child.name);
			var activeClass = isActive ? ' active' : '';
			var className = 'tabmenu-item-' + child.name + activeClass;

			var tabItem = E('li', { 'class': className }, [
				E('a', { 'href': L.url(url, child.name) }, [_(child.title)])
			]);

			tabContainer.appendChild(tabItem);

			if (isActive) {
				activeNode = child;
			}
		}

		if (container) {
			container.appendChild(tabContainer);
			container.style.display = '';

			if (activeNode) {
				var nestedTabs = this.renderTabMenu(activeNode, url + '/' + activeNode.name, currentLevel);
				if (nestedTabs && nestedTabs.children && nestedTabs.children.length > 0) {
					container.appendChild(nestedTabs);
				}
			}
		}

		return tabContainer;
	},

	handleSidebarToggle: function () {
		var showSideButton = document.querySelector('a.showSide');
		var sidebar = document.querySelector('.main-left');
		var darkMask = document.querySelector('.darkMask');
		var scrollbarArea = document.querySelector('.main-right');

		if (!sidebar) return;

		if (sidebar.classList.contains('active')) {
			if (showSideButton) showSideButton.classList.remove('active');
			sidebar.classList.remove('active');
			if (scrollbarArea) scrollbarArea.classList.remove('active');
			if (darkMask) darkMask.classList.remove('active');
		} else {
			if (showSideButton) showSideButton.classList.add('active');
			sidebar.classList.add('active');
			if (scrollbarArea) scrollbarArea.classList.add('active');
			if (darkMask) darkMask.classList.add('active');
		}
	}
});
