/*!
 * 东方同人录 — 统一分页组件
 * 音乐库 / 艺术家详情 / 展会列表与弹窗共用同一套结构与交互。
 *
 * 用法：
 *   TongrenluPagination.render('#mount', {
 *     page: 1, totalPages: 10, total: 187, unit: '个展会',
 *     jump: true,                                  // 是否显示跳页框
 *     scroll: true, scrollTarget: '#eventGrid',    // 翻页后把结果带回视口
 *     onPage: function (page) { ... }
 *   });
 *
 * 约定：
 *   - 容器需带 class="pgn-mount"，组件通过 hidden 控制显隐。
 *   - 无障碍行为内建：nav[aria-label]、aria-current、图标 aria-hidden、
 *     页码播报（sr-only live region）、跳页输入框 label、翻页后焦点归位。
 */
(function (global) {
    'use strict';

    var seq = 0;

    function toEl(target) {
        return typeof target === 'string' ? document.querySelector(target) : target;
    }

    function clamp(value, min, max) {
        return Math.min(max, Math.max(min, value));
    }

    function formatNumber(n) {
        try {
            return new Intl.NumberFormat('zh-CN').format(n);
        } catch (e) {
            return String(n);
        }
    }

    function prefersReducedMotion() {
        return !!(global.matchMedia && global.matchMedia('(prefers-reduced-motion: reduce)').matches);
    }

    function visiblePageSize() {
        return (global.matchMedia && global.matchMedia('(max-width: 480px)').matches) ? 3 : 5;
    }

    /** 页码窗口：首页、末页固定，中间最多 maxVisible 页，间隔用省略号。 */
    function pageSequence(page, totalPages, maxVisible) {
        var out = [];
        var start = Math.max(1, page - Math.floor(maxVisible / 2));
        var end = Math.min(totalPages, start + maxVisible - 1);
        if (end - start < maxVisible - 1) {
            start = Math.max(1, end - maxVisible + 1);
        }
        if (start > 1) {
            out.push(1);
            if (start > 2) out.push('gap');
        }
        for (var i = start; i <= end; i++) out.push(i);
        if (end < totalPages) {
            if (end < totalPages - 1) out.push('gap');
            out.push(totalPages);
        }
        return out;
    }

    function iconButton(className, label, icon, disabled) {
        return '<button type="button" class="' + className + '"' +
            ' aria-label="' + label + '"' +
            ' data-action="' + (icon === 'left' ? 'prev' : 'next') + '"' +
            (disabled ? ' disabled aria-disabled="true"' : '') + '>' +
            '<i class="fas fa-chevron-' + icon + '" aria-hidden="true"></i>' +
            '</button>';
    }

    function buildNav(opts, mountId) {
        var page = opts.page;
        var totalPages = opts.totalPages;
        var unit = opts.unit || '条';

        var infoText = '';
        if (opts.total >= 0) {
            infoText = '共 ' + formatNumber(opts.total) + ' ' + unit;
            if (totalPages > 1) infoText += ' · 第 ' + page + ' / ' + totalPages + ' 页';
        } else if (totalPages > 1) {
            infoText = '第 ' + page + ' / ' + totalPages + ' 页';
        }

        var html = '<nav class="pgn" aria-label="分页">';
        html += '<p class="pgn__info">' + infoText + '</p>';

        if (totalPages > 1) {
            html += '<div class="pgn__controls">';
            html += iconButton('pgn__btn pgn__btn--step', '上一页', 'left', page <= 1);

            pageSequence(page, totalPages, visiblePageSize()).forEach(function (item) {
                if (item === 'gap') {
                    html += '<span class="pgn__ellipsis" aria-hidden="true">…</span>';
                } else if (item === page) {
                    html += '<button type="button" class="pgn__btn" data-page="' + item + '"' +
                        ' aria-label="第 ' + item + ' 页" aria-current="page">' + item + '</button>';
                } else {
                    html += '<button type="button" class="pgn__btn" data-page="' + item + '"' +
                        ' aria-label="第 ' + item + ' 页">' + item + '</button>';
                }
            });

            html += iconButton('pgn__btn pgn__btn--step', '下一页', 'right', page >= totalPages);
            html += '</div>';

            if (opts.jump) {
                var inputId = 'pgn-jump-' + mountId;
                html += '<div class="pgn__jump">' +
                    '<label class="pgn__jump-label" for="' + inputId + '">跳转到</label>' +
                    '<input class="pgn__jump-input" id="' + inputId + '" type="number"' +
                    ' min="1" max="' + totalPages + '" step="1" inputmode="numeric"' +
                    ' value="' + page + '" aria-describedby="' + inputId + '-hint">' +
                    '<span class="pgn__jump-hint" id="' + inputId + '-hint">共 ' + totalPages + ' 页</span>' +
                    '<button type="button" class="pgn__jump-btn" aria-label="跳转到指定页">跳转</button>' +
                    '</div>';
            }
        }

        html += '</nav>';
        return html;
    }

    function render(container, options) {
        var el = toEl(container);
        if (!el) return;

        var opts = options || {};
        var totalPages = Math.max(1, parseInt(opts.totalPages, 10) || 1);
        var page = clamp(parseInt(opts.page, 10) || 1, 1, totalPages);
        var rawTotal = parseInt(opts.total, 10);
        var total = isNaN(rawTotal) ? -1 : Math.max(0, rawTotal);
        var mountId = el.getAttribute('data-pgn-id') || String(++seq);
        el.setAttribute('data-pgn-id', mountId);

        // 无数据时整块隐藏，避免空壳翻页控件（跳页框不再孤零零地可用）
        if (total === 0) {
            el.innerHTML = '';
            el.hidden = true;
            delete el.__pgnPage;
            return;
        }

        var previousPage = el.__pgnPage;
        var hadFocus = el.contains(document.activeElement);
        var fromKeyboard = el.__pgnKeyboard === true;
        el.__pgnKeyboard = false;

        el.innerHTML = buildNav({
            page: page,
            totalPages: totalPages,
            total: total,
            unit: opts.unit,
            jump: !!opts.jump
        }, mountId);
        el.hidden = false;

        var nav = el.querySelector('.pgn');
        var controls = el.querySelector('.pgn__controls');
        if (!nav) return;

        // 页码播报：live region 反复重建会漏报，因此常驻在容器里、只更新文本
        var live = el.querySelector(':scope > .pgn__live');
        if (!live) {
            live = document.createElement('span');
            live.className = 'pgn__live';
            live.setAttribute('role', 'status');
            live.setAttribute('aria-live', 'polite');
            el.insertBefore(live, el.firstChild);
        }
        if (previousPage !== undefined && previousPage !== page) {
            live.textContent = '第 ' + page + ' 页，共 ' + totalPages + ' 页' +
                (total >= 0 ? '，共 ' + formatNumber(total) + ' ' + (opts.unit || '条') : '');
        }

        function goTo(next) {
            if (!next || next === page) return;
            next = clamp(next, 1, totalPages);
            if (next === page) return;
            if (typeof opts.onPage === 'function') opts.onPage(next);
        }

        if (controls) {
            controls.addEventListener('click', function (event) {
                var btn = event.target.closest('button');
                if (!btn || btn.disabled) return;
                var target = btn.getAttribute('data-page');
                if (target) {
                    el.__pgnKeyboard = btn.matches(':focus-visible');
                    goTo(parseInt(target, 10));
                    return;
                }
                var action = btn.getAttribute('data-action');
                if (action === 'prev') {
                    el.__pgnKeyboard = btn.matches(':focus-visible');
                    goTo(page - 1);
                } else if (action === 'next') {
                    el.__pgnKeyboard = btn.matches(':focus-visible');
                    goTo(page + 1);
                }
            });
        }

        var jumpInput = el.querySelector('.pgn__jump-input');
        var jumpBtn = el.querySelector('.pgn__jump-btn');
        if (jumpInput && jumpBtn) {
            var submitJump = function () {
                var value = clamp(parseInt(jumpInput.value, 10) || page, 1, totalPages);
                if (value === page) {
                    jumpInput.value = page;
                    return;
                }
                el.__pgnKeyboard = true;
                goTo(value);
            };
            jumpBtn.addEventListener('click', submitJump);
            jumpInput.addEventListener('keydown', function (event) {
                if (event.key === 'Enter') {
                    event.preventDefault();
                    submitJump();
                }
            });
        }

        el.__pgnPage = page;

        // 焦点归位：翻页后不要把键盘用户丢回 body
        if (hadFocus || fromKeyboard) {
            var current = el.querySelector('[aria-current="page"]') || nav.querySelector('button');
            if (current) current.focus({ preventScroll: true });
        }

        // 指针翻页时把结果带回视口；键盘翻页保留焦点在分页区
        if (opts.scroll && opts.scrollTarget && !fromKeyboard) {
            scrollTo(opts.scrollTarget);
        }
    }

    function scrollTo(target) {
        var el = toEl(target);
        if (!el) return;
        el.scrollIntoView({
            behavior: prefersReducedMotion() ? 'auto' : 'smooth',
            block: 'start'
        });
    }

    global.TongrenluPagination = {
        render: render,
        scrollTo: scrollTo
    };
})(window);
