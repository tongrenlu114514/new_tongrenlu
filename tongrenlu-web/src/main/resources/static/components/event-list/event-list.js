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
        console.log('event-list.js 初始化开始');
        cacheElements();
        bindEvents();
        console.log('开始加载展会数据');
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
        
        console.log('DOM元素缓存完成:');
        console.log('  eventGrid:', elements.eventGrid);
        console.log('  eventLoading:', elements.loadingEl);
        console.log('  eventEmpty:', elements.emptyEl);
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

        // ESC 关闭弹窗
        document.addEventListener('keydown', (e) => {
            if (e.key === 'Escape' && elements.modal.classList.contains('active')) {
                closeModal();
            }
        });
    }

    // ==================== API 调用 ====================
    async function loadEvents() {
        if (state.loading) return;
        state.loading = true;
        showLoading();

        try {
            const params = new URLSearchParams({
                page: state.currentPage,
                limit: CONFIG.PAGE_SIZE,
                orderBy: state.currentSort
            });

            if (state.searchKeyword) {
                params.append('keyword', state.searchKeyword);
            }

            console.log('正在请求API:', `${CONFIG.API_BASE}?${params}`);
            const response = await fetch(`${CONFIG.API_BASE}?${params}`);
            
            if (!response.ok) {
                const errorText = await response.text();
                console.error('API响应错误:', response.status, errorText);
                throw new Error(`API请求失败: ${response.status}`);
            }

            const data = await response.json();
            console.log('API返回数据:', data);
            console.log('records类型:', typeof data.records, Array.isArray(data.records));
            console.log('records内容:', JSON.stringify(data.records, null, 2));
            
            // 检查是否有错误响应
            if (data.error) {
                throw new Error(data.error);
            }
            
            state.events = data.records || [];
            state.total = data.total || 0;
            state.totalPages = data.totalPages || 1;
            
            console.log('处理后的events:', state.events);
            console.log('total:', state.total);

            // 应用前端筛选（因为API不直接支持状态筛选）
            applyFilter();
            renderEvents();
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
        
        state.total = state.events.length;
        state.totalPages = 1;
        
        applyFilter();
        renderEvents();
        updateStats();
        renderMainPagination();
    }

    function applyFilter() {
        // 由于tag表没有状态字段，这里只是简单展示
        // 如果需要状态筛选，需要在tag表添加额外字段
        state.filteredEvents = state.events;
    }

    // ==================== 渲染函数 ====================
    function renderEvents() {
        console.log('renderEvents被调用');
        console.log('eventGrid元素:', elements.eventGrid);
        console.log('filteredEvents数量:', state.filteredEvents.length);
        
        if (!elements.eventGrid) {
            console.error('eventGrid元素不存在！');
            return;
        }

        if (state.filteredEvents.length === 0) {
            showEmpty();
            elements.eventGrid.innerHTML = '';
            return;
        }

        hideEmpty();
        const html = state.filteredEvents.map(event => createEventCard(event)).join('');
        console.log('生成的HTML长度:', html.length);
        elements.eventGrid.innerHTML = html;
        console.log('HTML已注入到eventGrid');
        
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

        return `
            <div class="event-card" data-id="${event.id}" data-name="${displayName}">
                <div class="event-card__header">
                    <div class="event-card__icon">
                        <i class="fas fa-calendar-alt"></i>
                    </div>
                    <div class="event-card__series">${series}</div>
                </div>
                <div class="event-card__body">
                    <h3 class="event-card__name">${displayName}</h3>
                    ${edition ? `<p class="event-card__edition">${edition}</p>` : ''}
                    <div class="event-card__stats">
                        <span class="event-card__stat">
                            <i class="fas fa-compact-disc"></i>
                            ${albumCount} 张专辑
                        </span>
                    </div>
                </div>
                <div class="event-card__footer">
                    <span class="event-card__view">查看专辑 <i class="fas fa-arrow-right"></i></span>
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

    function renderMainPagination() {
        if (!elements.paginationContainer) return;
        
        if (state.totalPages <= 1) {
            elements.paginationContainer.innerHTML = '';
            return;
        }

        let html = '<div class="event-main-pagination">';
        
        // 上一页
        html += `<button class="event-pagination__btn" data-page="${state.currentPage - 1}" ${state.currentPage <= 1 ? 'disabled' : ''}>
            <i class="fas fa-chevron-left"></i> 上一页
        </button>`;
        
        // 页码信息
        html += `<span class="event-pagination__info">第 ${state.currentPage} / ${state.totalPages} 页，共 ${state.total} 个展会</span>`;
        
        // 下一页
        html += `<button class="event-pagination__btn" data-page="${state.currentPage + 1}" ${state.currentPage >= state.totalPages ? 'disabled' : ''}>
            下一页 <i class="fas fa-chevron-right"></i>
        </button>`;
        
        html += '</div>';
        
        elements.paginationContainer.innerHTML = html;

        // 绑定分页事件
        elements.paginationContainer.querySelectorAll('.event-pagination__btn:not([disabled])').forEach(btn => {
            btn.addEventListener('click', () => {
                const page = parseInt(btn.dataset.page);
                if (page && page !== state.currentPage && page >= 1 && page <= state.totalPages) {
                    state.currentPage = page;
                    loadEvents();
                    // 滚动到页面顶部
                    window.scrollTo({ top: 0, behavior: 'smooth' });
                }
            });
        });
    }

    // ==================== 弹窗 ====================
    async function openModal(eventId, eventName) {
        state.currentEventId = eventId;
        state.modalPage = 1;
        
        elements.modalTitle.textContent = `${eventName} - 专辑列表`;
        elements.modal.classList.add('active');
        document.body.style.overflow = 'hidden';
        
        await loadEventAlbums();
    }

    function closeModal() {
        elements.modal.classList.remove('active');
        document.body.style.overflow = '';
        state.currentEventId = null;
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
                    const coverUrl = album.cloud_music_pic_url || album.cloudMusicPicUrl;
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
                // 跳转到专辑详情页
                window.location.href = `album.html?id=${albumId}`;
            });
        });
    }

    function renderModalPagination(data) {
        const totalPages = data.totalPages || 1;
        const currentPage = data.page || 1;
        
        if (totalPages <= 1) {
            elements.modalPagination.innerHTML = '';
            return;
        }

        let paginationHtml = '<div class="event-pagination">';
        
        // 上一页
        paginationHtml += `<button class="event-pagination__btn" data-page="${currentPage - 1}" ${currentPage <= 1 ? 'disabled' : ''}>
            <i class="fas fa-chevron-left"></i>
        </button>`;
        
        // 页码
        for (let i = 1; i <= totalPages; i++) {
            if (i === 1 || i === totalPages || (i >= currentPage - 1 && i <= currentPage + 1)) {
                paginationHtml += `<button class="event-pagination__btn ${i === currentPage ? 'active' : ''}" data-page="${i}">${i}</button>`;
            } else if (i === currentPage - 2 || i === currentPage + 2) {
                paginationHtml += '<span class="event-pagination__ellipsis">...</span>';
            }
        }
        
        // 下一页
        paginationHtml += `<button class="event-pagination__btn" data-page="${currentPage + 1}" ${currentPage >= totalPages ? 'disabled' : ''}>
            <i class="fas fa-chevron-right"></i>
        </button>`;
        
        paginationHtml += '</div>';
        paginationHtml += `<div class="event-pagination__info">共 ${data.total} 张专辑</div>`;
        
        elements.modalPagination.innerHTML = paginationHtml;

        // 绑定分页事件
        elements.modalPagination.querySelectorAll('.event-pagination__btn:not([disabled])').forEach(btn => {
            btn.addEventListener('click', () => {
                const page = parseInt(btn.dataset.page);
                if (page && page !== state.modalPage) {
                    state.modalPage = page;
                    loadEventAlbums();
                }
            });
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