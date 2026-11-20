// 音乐库相关功能

// ==================== 工具：HTML 转义 ====================
// 专辑标题/描述来自外部抓取与生成内容，进模板前一律转义
function escapeHtml(value) {
    return String(value == null ? '' : value)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

// ==================== 全局状态 ====================
let currentKeyword = '';
let currentPage = 1;
let currentOrderBy = 'publishDate';
let currentTag = '';
let totalPages = 1;
let totalRecords = 0;

// 搜索建议相关状态
let debounceTimer = null;
let suggestionsData = [];
let highlightedIndex = -1;

// ==================== 工具函数 ====================

// 加载专辑数量统计
function loadAlbumCount() {
    $.ajax({
        url: 'api/music/album-stats',
        method: 'GET',
        success: function(response) {
            if (response && response.success && response.data) {
                const count = response.data.published || response.data.total || 0;
                $('#navAlbumCount').text(count.toLocaleString());
            }
        },
        error: function() {
            $('#navAlbumCount').text('--');
        }
    });
}

// 防抖函数
function debounce(func, wait) {
    return function executedFunction(...args) {
        clearTimeout(debounceTimer);
        debounceTimer = setTimeout(() => func.apply(this, args), wait);
    };
}

// localStorage 安全操作
const searchHistory = {
    STORAGE_KEY: 'tongrenlu_search_history',
    MAX_ITEMS: 10,
    
    get() {
        try {
            const data = localStorage.getItem(this.STORAGE_KEY);
            return data ? JSON.parse(data) : [];
        } catch (e) {
            console.warn('读取搜索历史失败:', e);
            return [];
        }
    },
    
    save(keyword) {
        try {
            let history = this.get();
            // 移除重复项
            history = history.filter(item => item !== keyword);
            // 添加到开头
            history.unshift(keyword);
            // 限制数量
            history = history.slice(0, this.MAX_ITEMS);
            localStorage.setItem(this.STORAGE_KEY, JSON.stringify(history));
            return true;
        } catch (e) {
            console.warn('保存搜索历史失败:', e);
            return false;
        }
    },
    
    remove(keyword) {
        try {
            let history = this.get();
            history = history.filter(item => item !== keyword);
            localStorage.setItem(this.STORAGE_KEY, JSON.stringify(history));
            return true;
        } catch (e) {
            console.warn('删除搜索历史失败:', e);
            return false;
        }
    },
    
    clear() {
        try {
            localStorage.removeItem(this.STORAGE_KEY);
            return true;
        } catch (e) {
            console.warn('清除搜索历史失败:', e);
            return false;
        }
    }
};

// ==================== 模态框功能 ====================

// 弹层焦点管理：打开时把焦点送进去，关闭时还给触发它的卡片
var albumModalReturnFocus = null;

function albumModalFocusables(modal) {
    return Array.prototype.filter.call(
        modal.querySelectorAll('a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'),
        function (el) { return el.offsetParent !== null; }
    );
}

function openAlbumModal() {
    var modal = document.getElementById('albumModal');
    if (!modal) return;
    albumModalReturnFocus = document.activeElement;
    modal.style.display = 'flex';
    modal.setAttribute('aria-hidden', 'false');
    var content = modal.querySelector('.geo-modal__content');
    if (content) content.scrollTop = 0;
    var closeBtn = modal.querySelector('.geo-modal__close');
    if (closeBtn) closeBtn.focus();
}

function closeAlbumModal() {
    var modal = document.getElementById('albumModal');
    if (!modal || modal.style.display === 'none') return;
    modal.style.display = 'none';
    modal.setAttribute('aria-hidden', 'true');
    if (albumModalReturnFocus && typeof albumModalReturnFocus.focus === 'function') {
        albumModalReturnFocus.focus();
    }
    albumModalReturnFocus = null;
}

document.addEventListener('keydown', function (e) {
    var modal = document.getElementById('albumModal');
    if (!modal || modal.style.display !== 'flex') return;
    if (e.key === 'Escape') {
        e.preventDefault();
        closeAlbumModal();
        return;
    }
    if (e.key !== 'Tab') return;
    var focusables = albumModalFocusables(modal);
    if (!focusables.length) return;
    var first = focusables[0];
    var last = focusables[focusables.length - 1];
    if (e.shiftKey && document.activeElement === first) {
        e.preventDefault();
        last.focus();
    } else if (!e.shiftKey && document.activeElement === last) {
        e.preventDefault();
        first.focus();
    }
});

async function updateAlbumModal(albumId) {
    try {
        showLoadingState();

        const albumDetail = await new Promise((resolve, reject) => {
            $.ajax({
                url: `api/music/detail?albumId=${albumId}`,
                method: 'GET',
                dataType: 'json',
                success: resolve,
                error: (xhr, status, error) => {
                    reject(new Error(`HTTP error! status: ${xhr.status}`));
                }
            });
        });

        // 更新标题和艺术家
        $('.album-title').text(albumDetail.title || '未知专辑');
        $('.album-artist').text(albumDetail.artist || '未知艺术家');

        // 更新元信息 - 新结构
        const publishDate = albumDetail.publishDate ? new Date(albumDetail.publishDate) : null;
        $('.meta-item').eq(0).html(`<i class="far fa-calendar"></i> ${publishDate ? publishDate.getFullYear() + '年' : '未知'}`);
        $('.meta-item').eq(1).html(`<i class="fas fa-music"></i> ${albumDetail.tracks && albumDetail.tracks.length > 0 ? albumDetail.tracks.length + '首曲目' : '未知'}`);

        // 更新描述
        const descriptionElement = $('.album-description');
        if (descriptionElement.length > 0) {
            descriptionElement.text(albumDetail.description || '暂无专辑描述');
        }

        // 更新专辑封面
        const albumArtElement = $('.album-art');
        if (albumArtElement.length > 0) {
            albumArtElement.attr('data-album-id', albumId);

            if (albumDetail.cloudMusicPicUrl) {
                loadImageWithCache(albumArtElement[0], albumDetail.cloudMusicPicUrl, 300, 300);
            } else {
                if (albumArtElement.find('.fallback-content').length === 0) {
                    albumArtElement.html('<div class="fallback-content">🎵</div>');
                }
            }
        }

        // 更新曲目列表
        const tracksContainer = $('.tracks');
        if (tracksContainer.length > 0) {
            let tracksHtml = '';

            if (albumDetail.tracks && albumDetail.tracks.length > 0) {
                $.each(albumDetail.tracks, (index, track) => {
                    const trackNumber = (index + 1).toString().padStart(2, '0');
                    const trackTitle = track.name || `曲目 ${index + 1}`;
                    const hasMusicUrl = track.cloudMusicId;
                    const originalInfo = track.original || '';

                    // 原作信息只保留文字出处；外链与匹配标记、时长都不再展示
                    const originalSubtitle = originalInfo
                        ? `<span class="track-subtitle">${originalInfo}</span>`
                        : '';

                    if (hasMusicUrl) {
                        tracksHtml += `
                            <li class="track">
                                <button class="track-play-btn"><i class="fas fa-play"></i></button>
                                <span class="track-number">${trackNumber}</span>
                                <div class="track-info">
                                    <span class="track-title">${trackTitle}</span>
                                    ${originalSubtitle}
                                </div>
                            </li>
                        `;
                    } else {
                        tracksHtml += `
                            <li class="track">
                                <button class="track-play-btn" disabled style="opacity: 0.5; cursor: not-allowed;"><i class="fas fa-play"></i></button>
                                <span class="track-number">${trackNumber}</span>
                                <div class="track-info">
                                    <span class="track-title">${trackTitle} <span style="color: #999; font-size: 0.8em;">(无法播放)</span></span>
                                    ${originalSubtitle}
                                </div>
                            </li>
                        `;
                    }
                });
            } else {
                const trackCount = 10;
                const title = albumDetail.title || '未知专辑';
                for (let i = 1; i <= trackCount; i++) {
                    tracksHtml += `
                        <li class="track">
                            <button class="track-play-btn"><i class="fas fa-play"></i></button>
                            <span class="track-number">${i.toString().padStart(2, '0')}</span>
                            <span class="track-title">${title} - 曲目 ${i}</span>
                        </li>
                    `;
                }
            }

            tracksContainer.html(tracksHtml);
        }

        // 更新错误报告按钮
        const errorButton = $('.report-error-btn');
        if (errorButton.length > 0) {
            errorButton.data('album-id', albumId);
            errorButton.prop('disabled', false);
            errorButton.html('<i class="fas fa-flag"></i>');
            errorButton.removeClass('loading success error');
            // 绑定点击事件
            errorButton.off('click').on('click', function(e) {
                e.stopPropagation();
                reportAlbumError();
            });
        }

        // 更新播放全部按钮
        const playAllBtn = $('.play-all-btn');
        if (playAllBtn.length > 0) {
            playAllBtn.off('click').on('click', function() {
                const albumId = $('.album-art').attr('data-album-id');
                if (albumId) {
                    openFoliaPlayer(albumId);
                }
            });
        }

        hideLoadingState();

        $('.track-play-btn').off('click').on('click', function (e) {
            e.stopPropagation();

            if ($(this).prop('disabled')) {
                return;
            }

            const icon = $(this).find('i');
            if (icon.hasClass('fa-play')) {
                const track = $(this).closest('.track');
                if (track.length > 0) {
                    const albumArt = $('.album-art');
                    const albumId = albumArt.attr('data-album-id');

                    if (albumId) {
                        openFoliaPlayer(albumId);
                    }
                }
            } else {
                icon.removeClass('fa-pause').addClass('fa-play');
                pauseMusic();
            }
        });

    } catch (error) {
        console.error('获取专辑详情失败:', error);
        hideLoadingState();
        showErrorState('加载专辑详情失败，请稍后重试');
    }
}

function showLoadingState() {
    const albumTitle = $('.album-title');
    const albumArtElement = $('.album-art');
    const tracksContainer = $('.tracks');

    if (albumTitle.length > 0) {
        albumTitle.text('加载中...');
    }

    if (albumArtElement.length > 0) {
        const fallbackContent = albumArtElement.find('.fallback-content');
        if (fallbackContent.length > 0) {
            fallbackContent.text('加载中...');
        } else {
            albumArtElement.html('<div class="fallback-content">加载中...</div>');
        }
    }

    if (tracksContainer.length > 0) {
        tracksContainer.html('<li class="track"><span class="track-title">正在加载曲目列表...</span></li>');
    }
}

function hideLoadingState() {
    // 由数据更新时自动处理
}

function showErrorState(message) {
    const modal = $('#albumModal');
    if (modal.length > 0) {
        modal.css('display', 'none');
    }
    alert(message);
}

// ==================== 搜索建议功能 ====================

// 渲染搜索建议
function renderSuggestions(items) {
    const $list = $('#suggestionsList');
    
    if (items.length === 0) {
        $list.html('');
        return;
    }
    
    let html = '';
    items.forEach((item, index) => {
        const title = escapeHtml(item.title);
        const artist = escapeHtml(item.artist || '未知艺术家');
        html += `
            <div class="suggestion-item" role="option" id="suggestion-opt-${index}" aria-selected="false" data-index="${index}" data-title="${title}">
                <div class="suggestion-icon" aria-hidden="true"><i class="fas fa-music"></i></div>
                <div class="suggestion-content">
                    <div class="suggestion-title">${title}</div>
                    <div class="suggestion-artist">${artist}</div>
                </div>
            </div>
        `;
    });
    
    $list.html(html);
    $('#searchSuggestions').show();
    $('.geo-search__input').attr('aria-expanded', 'true').removeAttr('aria-activedescendant');
    highlightedIndex = -1;
    suggestionsData = items;
}

// 渲染搜索历史
function renderSearchHistory() {
    const history = searchHistory.get();
    const $list = $('#suggestionsList');
    
    if (history.length === 0) {
        $list.html('');
        return;
    }
    
    let html = '<div class="suggestions-header"><span>搜索历史</span></div>';
    history.forEach((keyword, index) => {
        const kw = escapeHtml(keyword);
        html += `
            <div class="suggestion-item history-item" role="option" id="history-opt-${index}" aria-selected="false" data-index="${index}" data-keyword="${kw}">
                <div class="suggestion-icon" aria-hidden="true"><i class="fas fa-history"></i></div>
                <div class="suggestion-content">
                    <div class="suggestion-title">${kw}</div>
                </div>
                <button class="history-delete" data-keyword="${kw}" aria-label="删除搜索历史「${kw}」">
                    <i class="fas fa-times" aria-hidden="true"></i>
                </button>
            </div>
        `;
    });
    
    $list.html(html);
    $('#searchSuggestions').show();
    $('.geo-search__input').attr('aria-expanded', 'true').removeAttr('aria-activedescendant');
    highlightedIndex = -1;
    suggestionsData = [];
}

// 隐藏搜索建议
function hideSuggestions() {
    $('#searchSuggestions').hide();
    $('.geo-search__input').attr('aria-expanded', 'false').removeAttr('aria-activedescendant');
    highlightedIndex = -1;
}

// 高亮建议项
function highlightSuggestion(index) {
    const $items = $('.suggestion-item');
    $items.removeClass('highlighted').attr('aria-selected', 'false');
    const $input = $('.geo-search__input');

    if (index >= 0 && index < $items.length) {
        const $active = $items.eq(index).addClass('highlighted').attr('aria-selected', 'true');
        highlightedIndex = index;
        $input.attr('aria-activedescendant', $active.attr('id') || '');
    } else {
        highlightedIndex = -1;
        $input.removeAttr('aria-activedescendant');
    }
}

// 获取搜索建议
const fetchSuggestions = debounce(function(keyword) {
    if (!keyword || keyword.trim().length < 1) {
        hideSuggestions();
        return;
    }
    
    $.ajax({
        url: `api/music/search?keyword=${encodeURIComponent(keyword)}&pageNumber=1&pageSize=5&orderBy=publishDate`,
        method: 'GET',
        dataType: 'json',
        success: function(data) {
            if (data.records && data.records.length > 0) {
                const suggestions = data.records.map(item => ({
                    title: item.title || '未知专辑',
                    artist: item.artist || '未知艺术家'
                }));
                renderSuggestions(suggestions);
            } else {
                hideSuggestions();
            }
        },
        error: function() {
            hideSuggestions();
        }
    });
}, 300);

// ==================== 搜索功能 ====================

function searchMusic(keyword, page = 1, orderBy = currentOrderBy, tag = currentTag) {
    currentKeyword = keyword;
    currentPage = page;
    currentOrderBy = orderBy;
    currentTag = tag;

    // 搜索词 / 标签 / 排序 / 页码写进地址栏：刷新、后退、分享都能回到这一屏
    if (window.TongrenluListState) {
        window.TongrenluListState.write(
            { keyword: keyword, sort: orderBy, filter: tag, page: page },
            { keyword: '', sort: 'publishDate', filter: '', page: 1 }
        );
    }

    // 保存搜索历史
    if (keyword && keyword.trim()) {
        searchHistory.save(keyword.trim());
    }
    
    // 显示骨架屏
    renderSkeletonScreen();
    
    const params = new URLSearchParams();
    params.append('keyword', keyword);
    params.append('pageNumber', page.toString());
    params.append('pageSize', '10');
    params.append('orderBy', orderBy);
    if (tag) {
        params.append('tag', tag);
    }
    
    $.ajax({
        url: `api/music/search?${params.toString()}`,
        method: 'GET',
        dataType: 'json',
        success: function(data) {
            // 地址栏里的页码可能已经超出范围（换了筛选条件后），退到最后一页
            const pages = data.pages || 1;
            if (page > pages) {
                searchMusic(keyword, pages, orderBy, tag);
                return;
            }
            renderSearchResults(data);
            renderPagination(data, keyword);
        },
        error: function(xhr, status, error) {
            console.error('搜索出错:', error);
            $('.music-grid').html('<div class="error">搜索出错，请稍后重试</div>');
        }
    });
}

// 渲染骨架屏
function renderSkeletonScreen() {
    const musicGrid = $('.music-grid');
    let html = '';
    
    for (let i = 0; i < 12; i++) {
        html += `
            <div class="music-card skeleton-card">
                <div class="skeleton-cover"></div>
                <div class="card-content">
                    <div class="skeleton-title"></div>
                    <div class="skeleton-stats"></div>
                </div>
            </div>
        `;
    }
    
    musicGrid.html(html);
}

// 渲染搜索结果
function renderSearchResults(data) {
    const musicGrid = $('.music-grid');

    if (!data.records || data.records.length === 0) {
        musicGrid.html('<div class="no-results">未找到相关音乐</div>');
        return;
    }

    let html = '';
    $.each(data.records, (index, music) => {
        const title = escapeHtml(music.title || '未知专辑');
        const coverUrl = escapeHtml(music.cloudMusicPicUrl || '');
        const albumId = escapeHtml(music.id || '');
        html += `
        <div class="music-card" tabindex="0" role="button" aria-label="查看专辑《${title}》" data-index="${index}" data-title="${title}" data-cover-url="${coverUrl}" data-album-id="${albumId}">
            <div class="album-cover" data-original-url="${coverUrl}">
                ${music.cloudMusicPicUrl ? '' : '<div class="fallback-content" aria-hidden="true">🎵</div>'}
            </div>
            <div class="card-content">
                <h3 class="card-title">${title}</h3>
            </div>
            <button class="play-button" aria-label="播放《${title}》"><i class="fas fa-play" aria-hidden="true"></i></button>
        </div>
        `;
    });

    musicGrid.html(html);

    // 绑定播放按钮事件
    $('.play-button').off('click').on('click', function (e) {
        e.stopPropagation();

        const card = $(this).closest('.music-card');
        if (card.length > 0) {
            const albumId = card.data('album-id');
            if (albumId) {
                openFoliaPlayer(albumId);
            }
        }
    });

    // 绑定卡片点击事件
    $('.music-card').off('click').on('click', function (e) {
        if ($(e.target).closest('.play-button').length > 0) {
            return;
        }

        const albumId = $(this).data('album-id');

        if (albumId) {
            updateAlbumModal(albumId);
            openAlbumModal();
        }
    });

    // 键盘等价：卡片是 role=link，Enter/Space 打开详情（事件源为播放按钮时交给按钮自己处理）
    $('.music-card').off('keydown').on('keydown', function (e) {
        if (e.target !== this) return;
        if (e.key !== 'Enter' && e.key !== ' ') return;
        e.preventDefault();
        const albumId = $(this).data('album-id');
        if (albumId) {
            updateAlbumModal(albumId);
            openAlbumModal();
        }
    });

    triggerLazyLoadAfterSearch();
}

// ==================== 分页功能 ====================

let pendingPaginationScroll = false;

function renderPagination(data) {
    totalPages = data.pages || 1;
    totalRecords = data.total || 0;
    currentPage = data.current || 1;

    TongrenluPagination.render('#paginationContainer', {
        page: currentPage,
        totalPages: totalPages,
        total: totalRecords,
        unit: '条',
        jump: true,
        scroll: pendingPaginationScroll,
        scrollTarget: '.music-grid',
        onPage: function(page) {
            pendingPaginationScroll = true;
            searchMusic(currentKeyword, page, currentOrderBy, currentTag);
        }
    });

    pendingPaginationScroll = false;
}

// ==================== 动态标签功能 ====================

function loadPopularTags() {
    $.ajax({
        url: 'api/music/tags?limit=10',
        method: 'GET',
        dataType: 'json',
        success: function(tags) {
            renderTags(tags);
        },
        error: function() {
            // 降级：只显示"全部"
            renderTags([]);
        }
    });
}

// 一行最多摆 8 个分类标签（加「全部」共 9 枚）；再多就把排序挤下去
const TAG_CHIP_LIMIT = 8;

function renderTags(allTags) {
    const $filterTags = $('#filterTags');
    const tags = (allTags || []).slice(0, TAG_CHIP_LIMIT);

    // 当前筛选可能来自艺术家页、又不在截断后的 8 个里：补进来并让最后一枚退位，
    // 否则会出现「列表已筛选、筛选条上却看不到」的状态
    if (currentTag && !tags.some(t => String(t.tag) === String(currentTag))) {
        tags.unshift({ tag: currentTag });
        if (tags.length > TAG_CHIP_LIMIT) tags.pop();
    }

    // 始终保留"全部"选项；选中态直接按 currentTag 渲染，避免异步时序错位
    const allActive = !currentTag;
    let html = `<button type="button" class="tag${allActive ? ' active' : ''}" data-tag="" aria-pressed="${allActive}">全部</button>`;

    // 添加后端返回的标签
    tags.forEach(tag => {
        const label = escapeHtml(tag.tag);
        const isActive = !!currentTag && String(tag.tag) === String(currentTag);
        html += `<button type="button" class="tag${isActive ? ' active' : ''}" data-tag="${label}" aria-pressed="${isActive}">${label}</button>`;
    });

    $filterTags.html(html);

    // 手机端下拉与 Chip 共用同一份标签，状态互相同步
    const $tagSelect = $('#tagSelect');
    if ($tagSelect.length) {
        let options = '<option value="">全部标签</option>';
        tags.forEach(tag => {
            const label = escapeHtml(tag.tag);
            options += `<option value="${label}">${label}</option>`;
        });
        $tagSelect.html(options).val(currentTag || '');
    }

    // 绑定标签点击事件
    $('.tag').off('click').on('click', function() {
        $('.tag').removeClass('active').attr('aria-pressed', 'false');
        $(this).addClass('active').attr('aria-pressed', 'true');
        const tag = $(this).data('tag') || '';
        $tagSelect.val(tag);
        searchMusic(currentKeyword, 1, currentOrderBy, tag);
    });

    // 下拉切换：反向同步 Chip 的高亮与 aria-pressed
    $tagSelect.off('change').on('change', function() {
        const tag = $(this).val() || '';
        $('.tag').removeClass('active').attr('aria-pressed', 'false');
        $('.tag').filter(function() { return ($(this).data('tag') || '') === tag; })
            .addClass('active').attr('aria-pressed', 'true');
        searchMusic(currentKeyword, 1, currentOrderBy, tag);
    });
}

// ==================== 初始化和事件绑定 ====================

$(window).on('click', function (event) {
    const modal = $('#albumModal');
    if (event.target === modal[0]) {
        closeAlbumModal();
    }
    
    // 点击外部关闭搜索建议
    if (!$(event.target).closest('.geo-search').length) {
        hideSuggestions();
    }
});

$(function () {
    // 初始化：加载标签和音乐数据
    loadPopularTags();
    
    // 加载专辑数量统计
    loadAlbumCount();
    
    // 检查 localStorage 中是否有来自艺术家页面的筛选标签
    let filterTag = localStorage.getItem('filterTag');
    let selectedAlbumId = localStorage.getItem('selectedAlbumId');
    
    // 清除 localStorage 中的临时数据
    if (filterTag) {
        localStorage.removeItem('filterTag');
    }
    if (selectedAlbumId) {
        localStorage.removeItem('selectedAlbumId');
    }
    
    // 从地址栏恢复上一次的搜索词 / 标签 / 排序 / 页码
    const saved = window.TongrenluListState
        ? window.TongrenluListState.read({ keyword: '', sort: 'publishDate', filter: '', page: 1 })
        : { keyword: '', sort: 'publishDate', filter: '', page: 1 };

    // 选项已下线（如 accessCount）时不沿用，回退默认排序，避免地址栏与界面不一致
    const allowed = $('#sortSelect option').map(function() { return this.value; }).get();
    const sort = allowed.indexOf(saved.sort) >= 0 ? saved.sort : 'publishDate';

    // 从艺术家页点「专辑」过来时按标签筛选，优先级高于地址栏
    const keyword = filterTag ? '' : saved.keyword;
    const tag = filterTag || saved.filter || '';
    const page = filterTag ? 1 : saved.page;

    currentKeyword = keyword;
    currentTag = tag;
    applySortSelection(sort);

    if (keyword) {
        $('.geo-search__input').val(keyword);
        $('#searchClear').addClass('visible');
    }

    searchMusic(keyword, page, sort, tag);
    
    // 如果有选中的专辑ID，打开详情弹窗
    if (selectedAlbumId) {
        setTimeout(function() {
            updateAlbumModal(selectedAlbumId);
            openAlbumModal();
        }, 800);
    }
});

// 搜索按钮点击
// 搜索输入框事件：与艺术家/展会页一致，边打边搜（300ms），右侧清除钮可见
let searchInputTimer = null;

$('.geo-search__input').on('input', function() {
    const keyword = $(this).val().trim();
    $('#searchClear').toggleClass('visible', keyword.length > 0);

    if (keyword.length > 0) {
        fetchSuggestions(keyword);
    } else {
        hideSuggestions();
        renderSearchHistory();
    }

    if (searchInputTimer) clearTimeout(searchInputTimer);
    searchInputTimer = setTimeout(function() {
        searchMusic(keyword, 1, currentOrderBy, currentTag);
    }, 300);
});

$('#searchClear').on('click', function() {
    $('.geo-search__input').val('').focus();
    $(this).removeClass('visible');
    hideSuggestions();
    if (searchInputTimer) clearTimeout(searchInputTimer);
    currentKeyword = '';
    searchMusic('', 1, currentOrderBy, currentTag);
});

// 搜索输入框聚焦
$('.geo-search__input').on('focus', function() {
    const keyword = $(this).val().trim();
    
    if (keyword.length === 0) {
        renderSearchHistory();
    }
});

// 键盘导航
$('.geo-search__input').on('keydown', function(e) {
    const $items = $('.suggestion-item');
    const itemCount = $items.length;
    
    if (e.key === 'ArrowDown') {
        e.preventDefault();
        highlightSuggestion((highlightedIndex + 1) % itemCount);
    } else if (e.key === 'ArrowUp') {
        e.preventDefault();
        highlightSuggestion(highlightedIndex <= 0 ? itemCount - 1 : highlightedIndex - 1);
    } else if (e.key === 'Enter') {
        e.preventDefault();
        
        if (highlightedIndex >= 0 && highlightedIndex < itemCount) {
            const $selected = $items.eq(highlightedIndex);
            
            if ($selected.hasClass('history-item')) {
                const keyword = $selected.data('keyword');
                $('.geo-search__input').val(keyword);
                hideSuggestions();
                searchMusic(keyword, 1, currentOrderBy, currentTag);
            } else {
                const title = $selected.data('title');
                $('.geo-search__input').val(title);
                hideSuggestions();
                searchMusic(title, 1, currentOrderBy, currentTag);
            }
        } else {
            hideSuggestions();
            searchMusic($(this).val(), 1, currentOrderBy, currentTag);
        }
    } else if (e.key === 'Escape') {
        hideSuggestions();
    }
});

// 搜索建议点击
$(document).on('click', '.suggestion-item:not(.history-item)', function() {
    const title = $(this).data('title');
    $('.geo-search__input').val(title);
    hideSuggestions();
    searchMusic(title, 1, currentOrderBy, currentTag);
});

// 搜索历史点击
$(document).on('click', '.history-item', function(e) {
    if ($(e.target).closest('.history-delete').length) return;
    
    const keyword = $(this).data('keyword');
    $('.geo-search__input').val(keyword);
    hideSuggestions();
    searchMusic(keyword, 1, currentOrderBy, currentTag);
});

// 删除历史项
$(document).on('click', '.history-delete', function(e) {
    e.stopPropagation();
    const keyword = $(this).data('keyword');
    searchHistory.remove(keyword);
    renderSearchHistory();
});

// 排序：宽屏按钮组、窄屏下拉，共用一个状态
function applySortSelection(orderBy) {
    currentOrderBy = orderBy;
    $('.sort-btn').removeClass('active').attr('aria-pressed', 'false')
        .filter('[data-sort="' + orderBy + '"]').addClass('active').attr('aria-pressed', 'true');
    $('#sortSelect').val(orderBy);
}

$('.geo-sort').on('click', '.sort-btn', function() {
    const value = $(this).data('sort');
    if (!value || value === currentOrderBy) return;
    applySortSelection(value);
    searchMusic(currentKeyword, 1, currentOrderBy, currentTag);
});

$('#sortSelect').on('change', function() {
    const value = $(this).val();
    applySortSelection(value);
    searchMusic(currentKeyword, 1, currentOrderBy, currentTag);
});

// 触发懒加载
function triggerLazyLoadAfterSearch() {
    setTimeout(() => {
        if (typeof lazyLoadAlbumCovers === 'function') {
            lazyLoadAlbumCovers();
        }
    }, 100);
}

// 报告专辑错误功能
async function reportAlbumError() {
    const errorButton = $('.report-error-btn');

    if (errorButton.length === 0) {
        console.error('未找到错误报告按钮');
        return;
    }

    const albumId = errorButton.data('album-id');

    if (!albumId) {
        alert('无法获取专辑ID，请刷新页面后重试');
        return;
    }

    const isConfirmed = confirm('确定要报告这个专辑的错误吗？我们会尽快处理。');

    if (!isConfirmed) {
        return;
    }

    try {
        errorButton.prop('disabled', true);
        errorButton.html('<i class="fas fa-spinner fa-spin"></i> 提交中...');
        errorButton.addClass('loading');

        await $.ajax({
            url: `api/music/report-error?albumId=${albumId}`,
            method: 'POST',
            dataType: 'json',
            headers: {
                'Content-Type': 'application/json',
            }
        });

        errorButton.html('<i class="fas fa-check"></i> 已报告');
        errorButton.removeClass('loading');
        errorButton.addClass('success');

        alert('错误报告已提交，感谢您的反馈！我们会尽快处理。');

    } catch (error) {
        console.error('报告错误失败:', error);

        errorButton.html('<i class="fas fa-exclamation-triangle"></i> 报告失败');
        errorButton.removeClass('loading');
        errorButton.addClass('error');

        alert('提交失败，请稍后重试。如果问题持续存在，请联系管理员。');

    } finally {
        setTimeout(() => {
            if (errorButton.length > 0) {
                errorButton.prop('disabled', false);
                errorButton.html('<i class="fas fa-flag"></i>');
                errorButton.removeClass('loading success error');
            }
        }, 3000);
    }
}
