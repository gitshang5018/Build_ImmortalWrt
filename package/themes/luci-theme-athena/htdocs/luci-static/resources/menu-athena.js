/**
 * menu-athena.js - LuCI JS Dynamic Sidebar Menu Engine for Athena Theme
 */

'use strict';
'require baseclass';
'require ui';

// 平滑滑动动画工具
var SlideAnimations = {
	slideDown: function (element, speed) {
		element.style.display = 'block';
		element.style.overflow = 'hidden';
		var height = element.scrollHeight;
		element.style.height = '0px';
		element.style.transition = 'height ' + (speed === 'fast' ? '0.2s' : '0.35s') + ' ease';
		setTimeout(function () {
			element.style.height = height + 'px';
		}, 10);
		setTimeout(function () {
			element.style.height = '';
			element.style.overflow = '';
			element.style.transition = '';
		}, speed === 'fast' ? 200 : 350);
	},
	slideUp: function (element, speed) {
		element.style.overflow = 'hidden';
		element.style.height = element.scrollHeight + 'px';
		element.style.transition = 'height ' + (speed === 'fast' ? '0.2s' : '0.35s') + ' ease';
		setTimeout(function () {
			element.style.height = '0px';
		}, 10);
		setTimeout(function () {
			element.style.display = 'none';
			element.style.height = '';
			element.style.overflow = '';
			element.style.transition = '';
		}, speed === 'fast' ? 200 : 350);
	}
};

return baseclass.extend({
	__init__: function () {
		ui.menu.register('menu-athena', this);
	},

	render: function (tree) {
		var node = tree;
		var url = '';

		this.renderModeMenu(node);

		if (L.env.dispatchpath.length >= 3) {
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
			SlideAnimations.slideUp(ul, 'fast');
			if (!shouldCollapse && ul === slideMenu) {
				shouldCollapse = true;
			}
		});

		if (!slideMenu) return;

		if (!shouldCollapse) {
			slideMenu.classList.add('active');
			target.classList.add('active');
			SlideAnimations.slideDown(slideMenu, 'fast');
			target.blur();
		}

		ev.preventDefault();
		ev.stopPropagation();
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

			var menuItem = E('li', { 'class': slideClass }, [
				E('a', {
					'href': L.url(url, child.name),
					'click': (currentLevel === 1 && hasChildren) ? ui.createHandlerFn(this, 'handleMenuExpand') : null,
					'class': menuClass,
					'data-title': child.title.replace(/\s+/g, '_')
				}, [_(child.title)]),
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
			var isActive = (L.env.requestpath.length ? children[i].name == L.env.requestpath[0] : i == 0);
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
			var isActive = (L.env.dispatchpath[currentLevel + 2] === child.name);
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
