/**
 * 搜索历史下拉：给没有联想结果的页面（艺术家 / 展会）复用音乐库那套外观。
 * 结构沿用 .geo-suggestions / .suggestion-item / .history-item，样式在 list-page.css。
 *
 * 用法：
 *   const history = TongrenluSearchHistory.mount({
 *       input: '#artistSearchInput',
 *       panel: '#artistSearchHistory',
 *       storageKey: 'tongrenlu_search_history_artist'
 *   });
 *   history.save(keyword);   // 用户真正提交/停顿时调用
 */
(function () {
    'use strict';

    var MAX_ITEMS = 10;

    function escapeHtml(value) {
        return String(value == null ? '' : value)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    function createStore(storageKey) {
        return {
            key: storageKey,
            get: function () {
                try {
                    var raw = localStorage.getItem(storageKey);
                    var list = raw ? JSON.parse(raw) : [];
                    return Array.isArray(list) ? list : [];
                } catch (e) {
                    return [];
                }
            },
            save: function (keyword) {
                var kw = String(keyword || '').trim();
                if (!kw) return;
                try {
                    var list = this.get().filter(function (item) { return item !== kw; });
                    list.unshift(kw);
                    localStorage.setItem(storageKey, JSON.stringify(list.slice(0, MAX_ITEMS)));
                } catch (e) { /* 隐私模式下写不了就算了 */ }
            },
            remove: function (keyword) {
                try {
                    var list = this.get().filter(function (item) { return item !== keyword; });
                    localStorage.setItem(storageKey, JSON.stringify(list));
                } catch (e) { /* 同上 */ }
            }
        };
    }

    function resolve(target) {
        return typeof target === 'string' ? document.querySelector(target) : target;
    }

    function mount(options) {
        var input = resolve(options.input);
        var panel = resolve(options.panel);
        if (!input || !panel) return null;

        var store = createStore(options.storageKey);
        var list = document.createElement('div');
        list.setAttribute('role', 'listbox');
        list.setAttribute('aria-label', '搜索历史');
        panel.appendChild(list);

        function hide() {
            panel.classList.remove('active');
            input.setAttribute('aria-expanded', 'false');
        }

        function render() {
            var history = store.get();
            if (!history.length) {
                list.innerHTML = '';
                hide();
                return;
            }
            var html = '<div class="suggestions-header"><span>搜索历史</span></div>';
            history.forEach(function (keyword) {
                var kw = escapeHtml(keyword);
                html += '<div class="suggestion-item history-item" role="option" tabindex="0" data-keyword="' + kw + '">'
                    + '<div class="suggestion-icon" aria-hidden="true"><i class="fas fa-history"></i></div>'
                    + '<div class="suggestion-content"><div class="suggestion-title">' + kw + '</div></div>'
                    + '<button type="button" class="history-delete" data-keyword="' + kw + '" aria-label="删除搜索历史「' + kw + '」">'
                    + '<i class="fas fa-times" aria-hidden="true"></i></button>'
                    + '</div>';
            });
            list.innerHTML = html;
            panel.classList.add('active');
            input.setAttribute('aria-expanded', 'true');
        }

        function pick(keyword) {
            input.value = keyword;
            hide();
            input.dispatchEvent(new Event('input', { bubbles: true }));
            input.focus();
        }

        input.addEventListener('focus', function () {
            if (!input.value.trim()) render();
        });

        input.addEventListener('input', function () {
            if (!input.value.trim()) render(); else hide();
        });

        input.addEventListener('keydown', function (e) {
            if (e.key === 'Escape') hide();
        });

        document.addEventListener('click', function (e) {
            if (e.target !== input && !panel.contains(e.target)) hide();
        });

        list.addEventListener('click', function (e) {
            var del = e.target.closest('.history-delete');
            if (del) {
                e.stopPropagation();
                store.remove(del.dataset.keyword);
                render();
                return;
            }
            var item = e.target.closest('.history-item');
            if (item) pick(item.dataset.keyword);
        });

        list.addEventListener('keydown', function (e) {
            if (e.key !== 'Enter' && e.key !== ' ') return;
            var item = e.target.closest('.history-item');
            if (!item) return;
            e.preventDefault();
            pick(item.dataset.keyword);
        });

        return {
            render: render,
            hide: hide,
            save: function (keyword) { store.save(keyword); }
        };
    }

    window.TongrenluSearchHistory = { mount: mount, createStore: createStore };
})();
