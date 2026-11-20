/* ================================================================
   列表状态持久化 — 音乐库 / 艺术家 / 展会共用
   把搜索词、筛选、排序、页码写进地址栏（replaceState，不刷历史记录），
   刷新、后退、收藏、把链接发给别人，都能落在同一屏。
   与默认值相同的项不写进地址栏，URL 保持干净。
   ================================================================ */
(function (global) {
    'use strict';

    // 旧参数名 -> 新参数名（音乐库早期用过 orderBy / tag，读到也认）
    var LEGACY = { orderBy: 'sort', tag: 'filter' };

    function defaultsOf(defaults) {
        defaults = defaults || {};
        return {
            keyword: defaults.keyword || '',
            sort: defaults.sort || '',
            filter: defaults.filter || '',
            page: defaults.page || 1
        };
    }

    function read(defaults) {
        var fallback = defaultsOf(defaults);
        var params = new global.URLSearchParams(global.location.search);

        Object.keys(LEGACY).forEach(function (oldKey) {
            if (params.has(oldKey) && !params.has(LEGACY[oldKey])) {
                params.set(LEGACY[oldKey], params.get(oldKey));
            }
        });

        var page = parseInt(params.get('page'), 10);

        return {
            keyword: params.has('keyword') ? (params.get('keyword') || '') : fallback.keyword,
            sort: params.has('sort') && params.get('sort') ? params.get('sort') : fallback.sort,
            filter: params.has('filter') ? (params.get('filter') || '') : fallback.filter,
            page: page > 0 ? page : fallback.page
        };
    }

    function write(state, defaults) {
        var fallback = defaultsOf(defaults);
        state = state || {};

        var url = new global.URL(global.location.href);
        var params = url.searchParams;

        put(params, 'keyword', state.keyword, fallback.keyword);
        put(params, 'sort', state.sort, fallback.sort);
        put(params, 'filter', state.filter, fallback.filter);

        var page = parseInt(state.page, 10);
        if (page > 1) {
            params.set('page', String(page));
        } else {
            params.delete('page');
        }

        // 同一状态只留一份参数，别让旧名字残留在地址里
        Object.keys(LEGACY).forEach(function (oldKey) { params.delete(oldKey); });

        var search = params.toString();
        global.history.replaceState(
            global.history.state,
            '',
            url.pathname + (search ? '?' + search : '') + url.hash
        );
    }

    function put(params, key, value, fallback) {
        var text = value == null ? '' : String(value);
        if (text && text !== String(fallback)) {
            params.set(key, text);
        } else {
            params.delete(key);
        }
    }

    /* 只拼查询串，不改地址栏：详情深链、返回列表用同一份规则 */
    function toQuery(state, defaults) {
        var fallback = defaultsOf(defaults);
        state = state || {};
        var params = new global.URLSearchParams();

        put(params, 'keyword', state.keyword, fallback.keyword);
        put(params, 'sort', state.sort, fallback.sort);
        put(params, 'filter', state.filter, fallback.filter);

        var page = parseInt(state.page, 10);
        if (page > 1) params.set('page', String(page));

        return params.toString();
    }

    global.TongrenluListState = { read: read, write: write, toQuery: toQuery };
})(window);
