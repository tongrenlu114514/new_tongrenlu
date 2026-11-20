/**
 * 展会列表组件
 * 基于tag表中type='event'的数据
 */

(function() {
    'use strict';

    // ==================== 配置 ====================
    const CONFIG = {
        API_BASE: 'api/events',
        PAGE_SIZE: 20,
        DEBOUNCE_DELAY: 300
    };

    // ==================== 工具：HTML 转义 ====================
    function escapeHtml(value) {
        return String(value == null ? '' : value)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    // ==================== 状态管理 ====================
    const state = {
        events: [],
        filteredEvents: [],
        currentPage: 1,
        totalPages: 1,
        total: 0,
        loading: false,
        searchKeyword: '',
        currentFilter: 'all',
        currentSort: 'album_count',
        currentEventId: null,
        modalPage: 1
    };

    // ==================== DOM 元素 ====================
    const elements = {
        eventGrid: null,
        searchInput: null,
        searchClear: null,
        filterTabs: null,
        sortBtns: null,
        loadingEl: null,
        emptyEl: null,
        modal: null,
        modalTitle: null,
        modalBody: null,
        modalClose: null,
        modalPagination: null,
        paginationContainer: null
    };

    // ==================== 初始化 ====================
    function init() {
        cacheElements();
        bindEvents();
        loadEvents();
    }

    function cacheElements() {
        elements.eventGrid = document.getElementById('eventGrid');
        elements.searchInput = document.getElementById('eventSearchInput');
        elements.searchClear = document.getElementById('eventSearchClear');
        elements.filterTabs = document.querySelectorAll('.event-filter-tab');
        elements.sortBtns = document.querySelectorAll('.event-sort-btn');
        elements.loadingEl = document.getElementById('eventLoading');
        elements.emptyEl = document.getElementById('eventEmpty');
        elements.modal = document.getElementById('eventModal');
        elements.modalTitle = document.getElementById('eventModalTitle');
        elements.modalBody = document.getElementById('eventModalBody');
        elements.modalClose = document.getElementById('eventModalClose');
        elements.modalPagination = document.getElementById('eventModalPagination');
        elements.paginationContainer = document.getElementById('eventPaginationContainer');
        
    }

    function bindEvents() {
        // 搜索输入
        if (elements.searchInput) {
            let debounceTimer;
            elements.searchInput.addEventListener('input', (e) => {
                clearTimeout(debounceTimer);
                debounceTimer = setTimeout(() => {
                    state.searchKeyword = e.target.value.trim();
                    state.currentPage = 1;
                    loadEvents();
                    updateClearButton();
                }, CONFIG.DEBOUNCE_DELAY);
            });
        }

        // 清除搜索
        if (elements.searchClear) {
            elements.searchClear.addEventListener('click', () => {
                elements.searchInput.value = '';
                state.searchKeyword = '';
                state.currentPage = 1;
                loadEvents();
                updateClearButton();
            });
        }

        // 筛选标签
        elements.filterTabs.forEach(tab => {
            tab.addEventListener('click', () => {
                elements.filterTabs.forEach(t => t.classList.remove('active'));
                tab.classList.add('active');
                state.currentFilter = tab.dataset.filter;
                state.currentPage = 1;
                loadEvents();
            });
        });

        // 排序按钮
        elements.sortBtns.forEach(btn => {
            btn.addEventListener('click', () => {
                elements.sortBtns.forEach(b => b.classList.remove('active'));
                btn.classList.add('active');
                state.currentSort = btn.dataset.sort;
                state.currentPage = 1;
                loadEvents();
            });
        });

        // 弹窗关闭
        if (elements.modalClose) {
            elements.modalClose.addEventListener('click', closeModal);
        }
        if (elements.modal) {
            elements.modal.addEventListener('click', (e) => {
                if (e.target === elements.modal) {
                    closeModal();
                }
            });
        }

        // ESC 关闭弹窗；Tab 在弹层内循环
        document.addEventListener('keydown', (e) => {
            if (!elements.modal.classList.contains('active')) return;
            if (e.key === 'Escape') {
                closeModal();
                return;
            }
            if (e.key !== 'Tab') return;
            const focusables = modalFocusables();
            if (!focusables.length) return;
            const first = focusables[0];
            const last = focusables[focusables.length - 1];
            if (e.shiftKey && document.activeElement === first) {
                e.preventDefault();
                last.focus();
            } else if (!e.shiftKey && document.activeElement === last) {
                e.preventDefault();
                first.focus();
            }
        });
    }

    // ==================== API 调用 ====================
    async function loadEvents() {
        if (state.loading) return;
        state.loading = true;
        showLoading();

        try {
            // 始终获取足够多的数据用于前端筛选和分页
            const limit = 1000;
            
            const params = new URLSearchParams({
                page: 1,
                limit: limit,
                orderBy: 'album_count' // 始终按专辑数量获取，前端再排序
            });

            if (state.searchKeyword) {
                params.append('keyword', state.searchKeyword);
            }

            const response = await fetch(`${CONFIG.API_BASE}?${params}`);
            
            if (!response.ok) {
                const errorText = await response.text();
                console.error('API响应错误:', response.status, errorText);
                throw new Error(`API请求失败: ${response.status}`);
            }

            const data = await response.json();
            
            // 检查是否有错误响应
            if (data.error) {
                throw new Error(data.error);
            }
            
            state.events = data.records || [];
            state.total = data.total || 0;
            state.totalPages = data.totalPages || 1;
            

            // 应用前端筛选和排序
            applyFilter();
            
            // 应用分页到过滤后的数据
            const startIndex = (state.currentPage - 1) * CONFIG.PAGE_SIZE;
            const endIndex = startIndex + CONFIG.PAGE_SIZE;
            const paginatedEvents = state.filteredEvents.slice(startIndex, endIndex);
            
            renderEvents(paginatedEvents);
            updateStats();
            renderMainPagination();

        } catch (error) {
            console.error('加载展会失败:', error);
            // 如果API失败，使用模拟数据
            useMockData();
        } finally {
            state.loading = false;
            hideLoading();
        }
    }

    function useMockData() {
        // 模拟数据 - 基于tag表结构
        state.events = [
            { id: 1006, tag: 'C91', type: 'event', album_count: 156 },
            { id: 1007, tag: 'C89', type: 'event', album_count: 132 },
            { id: 1022, tag: '例大祭14', type: 'event', album_count: 128 },
            { id: 1011, tag: '例大祭9', type: 'event', album_count: 98 },
            { id: 1035, tag: 'C90', type: 'event', album_count: 89 },
            { id: 1012, tag: 'C83', type: 'event', album_count: 76 },
            { id: 1044, tag: '例大祭13', type: 'event', album_count: 72 },
            { id: 1049, tag: '東方紅楼夢11', type: 'event', album_count: 65 },
            { id: 1040, tag: 'C88', type: 'event', album_count: 58 },
            { id: 1030, tag: '東方紅楼夢4', type: 'event', album_count: 52 },
            { id: 1027, tag: 'C80', type: 'event', album_count: 45 },
            { id: 1042, tag: 'C85', type: 'event', album_count: 38 }
        ];
        
        // 应用前端筛选和排序
        applyFilter();
        
        // 应用分页
        const startIndex = (state.currentPage - 1) * CONFIG.PAGE_SIZE;
        const endIndex = startIndex + CONFIG.PAGE_SIZE;
        const paginatedEvents = state.filteredEvents.slice(startIndex, endIndex);
        
        renderEvents(paginatedEvents);
        updateStats();
        renderMainPagination();
    }

    function applyFilter() {
        let filtered = [...state.events];
        
        // 根据筛选条件过滤
        if (state.currentFilter !== 'all') {
            filtered = filtered.filter(event => {
                const tagName = (event.tag || '').toLowerCase();
                const name = event.tag || '';
                
                switch (state.currentFilter) {
                    case 'comiket':
                        // Comic Market: C开头 + 数字
                        return tagName.match(/^c\d+/i) || name.includes('Comic Market') || name.includes('コミケ');
                    case 'reitaisai':
                        // 例大祭
                        return name.includes('例大祭') || name.includes('例大祭');
                    case 'kouroumu':
                        // 东方红楼梦
                        return name.includes('紅楼夢') || name.includes('红楼梦');
                    case 'm3':
                        // M3
                        return tagName.match(/^m3/i) || name.includes('M3');
                    case 'other':
                        // 其他：不是Comic Market、例大祭、红楼梦、M3
                        const isComiket = tagName.match(/^c\d+/i) || name.includes('Comic Market') || name.includes('コミケ');
                        const isReitaisai = name.includes('例大祭');
                        const isKouroumu = name.includes('紅楼夢') || name.includes('红楼梦');
                        const isM3 = tagName.match(/^m3/i) || name.includes('M3');
                        return !isComiket && !isReitaisai && !isKouroumu && !isM3;
                    default:
                        return true;
                }
            });
        }
        
        // 根据搜索关键词过滤
        if (state.searchKeyword) {
            const keyword = state.searchKeyword.toLowerCase();
            filtered = filtered.filter(event => {
                return (event.tag || '').toLowerCase().includes(keyword);
            });
        }
        
        // 排序
        filtered.sort((a, b) => {
            const aCount = a.album_count || a.albumCount || 0;
            const bCount = b.album_count || b.albumCount || 0;
            const aName = a.tag || '';
            const bName = b.tag || '';
            
            if (state.currentSort === 'album_count') {
                return bCount - aCount; // 专辑数量降序
            } else {
                return aName.localeCompare(bName, 'zh-CN'); // 名称升序
            }
        });
        
        state.filteredEvents = filtered;
        state.total = filtered.length;
        state.totalPages = Math.ceil(filtered.length / CONFIG.PAGE_SIZE) || 1;
    }

    // ==================== 渲染函数 ====================
    function renderEvents(eventsToRender) {
        // 如果没有传入参数，使用过滤后的数据
        const events = eventsToRender || state.filteredEvents;
        
        
        if (!elements.eventGrid) {
            console.error('eventGrid元素不存在！');
            return;
        }

        if (events.length === 0) {
            showEmpty();
            elements.eventGrid.innerHTML = '';
            return;
        }

        hideEmpty();
        const html = events.map(event => createEventCard(event)).join('');
        elements.eventGrid.innerHTML = html;
        
        // 绑定卡片点击事件
        elements.eventGrid.querySelectorAll('.event-card').forEach(card => {
            card.addEventListener('click', () => {
                const eventId = parseInt(card.dataset.id);
                const eventName = card.dataset.name;
                openModal(eventId, eventName);
            });
        });
    }

    function createEventCard(event) {
        // 兼容驼峰和下划线两种命名格式
        const albumCount = event.album_count || event.albumCount || 0;
        const displayName = event.tag || '未知展会';
        
        // 解析展会名称获取届数信息
        const editionMatch = displayName.match(/(\d+)/);
        const edition = editionMatch ? `第${editionMatch[1]}回` : '';
        
        // 判断展会系列
        let series = '';
        if (displayName.startsWith('C') && !displayName.includes('例大祭')) {
            series = 'COMIC MARKET';
        } else if (displayName.includes('例大祭')) {
            series = '博丽神社例大祭';
        } else if (displayName.includes('紅楼夢')) {
            series = '东方红楼梦';
        } else if (displayName.includes('M3')) {
            series = 'M3';
        }

        const safeName = escapeHtml(displayName);
        const safeSeries = escapeHtml(series);
        const safeEdition = escapeHtml(edition);

        return `
            <div class="event-card" role="button" data-id="${escapeHtml(event.id)}" data-name="${safeName}">
                <div class="event-card__header">
                    <div class="event-card__icon" aria-hidden="true">
                        <i class="fas fa-calendar-alt"></i>
                    </div>
                    <div class="event-card__series">${safeSeries}</div>
                </div>
                <div class="event-card__body">
                    <h3 class="event-card__name">${safeName}</h3>
                    ${edition ? `<p class="event-card__edition">${safeEdition}</p>` : ''}
                    <div class="event-card__stats">
                        <span class="event-card__stat">
                            <i class="fas fa-compact-disc" aria-hidden="true"></i>
                            ${Number(albumCount) || 0} 张专辑
                        </span>
                    </div>
                </div>
                <div class="event-card__footer">
                    <span class="event-card__view">查看专辑 <i class="fas fa-arrow-right" aria-hidden="true"></i></span>
                </div>
            </div>
        `;
    }

    function updateStats() {
        const countEl = document.getElementById('eventCount');
        if (countEl) {
            countEl.textContent = state.total;
        }
    }

    function renderMainPagination(shouldScroll) {
        if (!elements.paginationContainer) return;

        TongrenluPagination.render(elements.paginationContainer, {
            page: state.currentPage,
            totalPages: state.totalPages,
            total: state.total,
            unit: '个展会',
            scroll: shouldScroll === true,
            scrollTarget: '#eventGrid',
            onPage: function(page) {
                state.currentPage = page;

                const startIndex = (state.currentPage - 1) * CONFIG.PAGE_SIZE;
                const endIndex = startIndex + CONFIG.PAGE_SIZE;
                renderEvents(state.filteredEvents.slice(startIndex, endIndex));
                renderMainPagination(true);
            }
        });
    }

    // ==================== 弹窗 ====================
    let modalReturnFocus = null;

    function modalFocusables() {
        return Array.prototype.filter.call(
            elements.modal.querySelectorAll('a[href], button:not([disabled]), [tabindex]:not([tabindex="-1"])'),
            function (el) { return el.offsetParent !== null; }
        );
    }

    async function openModal(eventId, eventName) {
        state.currentEventId = eventId;
        state.modalPage = 1;
        modalReturnFocus = document.activeElement;

        elements.modalTitle.textContent = `${eventName} - 专辑列表`;
        elements.modal.classList.add('active');
        elements.modal.setAttribute('aria-hidden', 'false');
        document.body.style.overflow = 'hidden';
        if (elements.modalClose) elements.modalClose.focus();

        await loadEventAlbums();
    }

    function closeModal() {
        elements.modal.classList.remove('active');
        elements.modal.setAttribute('aria-hidden', 'true');
        document.body.style.overflow = '';
        state.currentEventId = null;
        if (modalReturnFocus && typeof modalReturnFocus.focus === 'function') {
            modalReturnFocus.focus();
        }
        modalReturnFocus = null;
    }

    async function loadEventAlbums() {
        elements.modalBody.innerHTML = '<div class="event-modal__loading"><i class="fas fa-spinner fa-spin"></i> 加载中...</div>';

        try {
            const params = new URLSearchParams({
                page: state.modalPage,
                limit: 15
            });

            const response = await fetch(`${CONFIG.API_BASE}/${state.currentEventId}/albums?${params}`);
            
            if (!response.ok) {
                throw new Error('API请求失败');
            }

            const data = await response.json();
            renderModalAlbums(data);
            renderModalPagination(data);

        } catch (error) {
            console.error('加载展会专辑失败:', error);
            elements.modalBody.innerHTML = `
                <div class="event-modal__empty">
                    <i class="fas fa-exclamation-circle"></i>
                    <p>加载失败，请稍后重试</p>
                </div>
            `;
        }
    }

    function renderModalAlbums(data) {
        const albums = data.records || [];
        
        if (albums.length === 0) {
            elements.modalBody.innerHTML = `
                <div class="event-modal__empty">
                    <i class="fas fa-compact-disc"></i>
                    <p>暂无专辑数据</p>
                </div>
            `;
            return;
        }

        elements.modalBody.innerHTML = `
            <div class="event-album-list">
                ${albums.map(album => {
                    let coverUrl = album.cloud_music_pic_url || album.cloudMusicPicUrl;
                    // 使用优化后的图片URL（150x150）
                    if (coverUrl && typeof getOptimizedImageUrl === 'function') {
                        coverUrl = getOptimizedImageUrl(coverUrl, 150, 150);
                    }
                    return `
                    <div class="event-album-item" data-id="${album.id}">
                        <div class="event-album-item__cover">
                            ${coverUrl 
                                ? `<img src="${coverUrl}" alt="${album.title || '专辑封面'}" loading="lazy" onerror="this.parentElement.innerHTML='<i class=\\'fas fa-music\\'></i>'">` 
                                : '<i class="fas fa-music"></i>'}
                        </div>
                        <p class="event-album-item__title">${album.title || '未知专辑'}</p>
                    </div>
                `}).join('')}
            </div>
        `;

        // 绑定专辑点击事件
        elements.modalBody.querySelectorAll('.event-album-item').forEach(item => {
            item.addEventListener('click', () => {
                const albumId = item.dataset.id;
                // 跳转到 Folia 播放器
                openFoliaPlayer(albumId, { newTab: false });
            });
        });
    }

    function renderModalPagination(data) {
        TongrenluPagination.render(elements.modalPagination, {
            page: data.page || 1,
            totalPages: data.totalPages || 1,
            total: data.total || 0,
            unit: '张专辑',
            onPage: function(page) {
                state.modalPage = page;
                loadEventAlbums();
            }
        });
    }

    // ==================== 辅助函数 ====================
    function showLoading() {
        if (elements.loadingEl) elements.loadingEl.style.display = 'flex';
    }

    function hideLoading() {
        if (elements.loadingEl) elements.loadingEl.style.display = 'none';
    }

    function showEmpty() {
        if (elements.emptyEl) elements.emptyEl.style.display = 'flex';
    }

    function hideEmpty() {
        if (elements.emptyEl) elements.emptyEl.style.display = 'none';
    }

    function updateClearButton() {
        if (elements.searchClear) {
            elements.searchClear.style.display = state.searchKeyword ? 'flex' : 'none';
        }
    }

    // ==================== 启动 ====================
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }

})();