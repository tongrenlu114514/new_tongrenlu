// 音乐库相关功能

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

function openAlbumModal() {
    $('#albumModal').css('display', 'flex');
}

function closeAlbumModal() {
    $('#albumModal').css('display', 'none');
}

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
        $('.meta-item').eq(2).html(`<i class="fas fa-headphones"></i> <span class="access-count">${albumDetail.accessCount || 0}</span> 次播放`);

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
                    const duration = track.duration || '0:00';
                    const trackTitle = track.name || `曲目 ${index + 1}`;
                    const hasMusicUrl = track.cloudMusicId;

                    if (hasMusicUrl) {
                        tracksHtml += `
                            <li class="track">
                                <button class="track-play-btn"><i class="fas fa-play"></i></button>
                                <span class="track-number">${trackNumber}</span>
                                <span class="track-title">${trackTitle}</span>
                                <span class="track-duration">${duration}</span>
                            </li>
                        `;
                    } else {
                        tracksHtml += `
                            <li class="track">
                                <button class="track-play-btn" disabled style="opacity: 0.5; cursor: not-allowed;"><i class="fas fa-play"></i></button>
                                <span class="track-number">${trackNumber}</span>
                                <span class="track-title">${trackTitle} <span style="color: #999; font-size: 0.8em;">(无法播放)</span></span>
                                <span class="track-duration">${duration}</span>
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
                            <span class="track-duration">${Math.floor(Math.random() * 4 + 2)}:${Math.floor(Math.random() * 60).toString().padStart(2, '0')}</span>
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
                    const playerUrl = `player.html?album=${albumId}`;
                    window.open(playerUrl, '_blank');
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
                        const playerUrl = `player.html?album=${albumId}`;
                        window.open(playerUrl, '_blank');
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
        html += `
            <div class="suggestion-item" data-index="${index}" data-title="${item.title}">
                <div class="suggestion-icon"><i class="fas fa-music"></i></div>
                <div class="suggestion-content">
                    <div class="suggestion-title">${item.title}</div>
                    <div class="suggestion-artist">${item.artist || '未知艺术家'}</div>
                </div>
            </div>
        `;
    });
    
    $list.html(html);
    $('#searchSuggestions').show();
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
        html += `
            <div class="suggestion-item history-item" data-index="${index}" data-keyword="${keyword}">
                <div class="suggestion-icon"><i class="fas fa-history"></i></div>
                <div class="suggestion-content">
                    <div class="suggestion-title">${keyword}</div>
                </div>
                <button class="history-delete" data-keyword="${keyword}">
                    <i class="fas fa-times"></i>
                </button>
            </div>
        `;
    });
    
    $list.html(html);
    $('#searchSuggestions').show();
    highlightedIndex = -1;
    suggestionsData = [];
}

// 隐藏搜索建议
function hideSuggestions() {
    $('#searchSuggestions').hide();
    highlightedIndex = -1;
}

// 高亮建议项
function highlightSuggestion(index) {
    const $items = $('.suggestion-item');
    $items.removeClass('highlighted');
    
    if (index >= 0 && index < $items.length) {
        $items.eq(index).addClass('highlighted');
        highlightedIndex = index;
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
    
    // 保存搜索历史
    if (keyword && keyword.trim()) {
        searchHistory.save(keyword.trim());
    }
    
    // 显示骨架屏
    renderSkeletonScreen();
    
    const params = new URLSearchParams();
    params.append('keyword', keyword);
    params.append('pageNumber', page.toString());
    params.append('pageSize', '15');
    params.append('orderBy', orderBy);
    if (tag) {
        params.append('tag', tag);
    }
    
    $.ajax({
        url: `api/music/search?${params.toString()}`,
        method: 'GET',
        dataType: 'json',
        success: function(data) {
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
    
    for (let i = 0; i < 16; i++) {
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
        const description = music.description || '暂无描述';
        html += `
        <div class="music-card" data-index="${index}" data-title="${music.title || '未知专辑'}" data-description="${description}" data-access-count="${music.accessCount || 0}" data-cover-url="${music.cloudMusicPicUrl || ''}" data-album-id="${music.id || ''}">
            <div class="album-cover" data-original-url="${music.cloudMusicPicUrl || ''}">
                ${music.cloudMusicPicUrl ? '' : '<div class="fallback-content">🎵</div>'}
                <div class="album-description-overlay">
                    <div class="album-description-text">${description}</div>
                </div>
            </div>
            <div class="card-content">
                <h3 class="card-title">${music.title || '未知专辑'}</h3>
                <div class="card-stats">
                    <span><i class="far fa-heart"></i> ${music.accessCount || 0}</span>
                </div>
            </div>
            <button class="play-button"><i class="fas fa-play"></i></button>
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
                const playerUrl = `player.html?album=${albumId}`;
                window.open(playerUrl, '_blank');
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

    triggerLazyLoadAfterSearch();
}

// ==================== 分页功能 ====================

function renderPagination(data, keyword) {
    totalPages = data.pages || 1;
    totalRecords = data.total || 0;
    currentPage = data.current || 1;
    
    // 更新分页信息
    $('#paginationInfo').text(`共 ${totalRecords} 条，第 ${currentPage} 页 / 共 ${totalPages} 页`);
    
    // 更新跳转输入框
    $('#jumpInput').attr('max', totalPages).val(currentPage);
    
    // 渲染分页按钮
    renderPaginationButtons(currentPage, totalPages, keyword);
}

function renderPaginationButtons(current, pages, keyword) {
    const $controls = $('#paginationControls');
    let html = '';
    
    // 上一页按钮
    if (current > 1) {
        html += `<button class="page-btn" data-page="${current - 1}"><i class="fas fa-chevron-left"></i></button>`;
    } else {
        html += `<button class="page-btn" disabled><i class="fas fa-chevron-left"></i></button>`;
    }
    
    // 页码按钮（智能省略）
    const maxVisible = 5;
    let startPage = Math.max(1, current - Math.floor(maxVisible / 2));
    let endPage = Math.min(pages, startPage + maxVisible - 1);
    
    if (endPage - startPage < maxVisible - 1) {
        startPage = Math.max(1, endPage - maxVisible + 1);
    }
    
    // 第一页
    if (startPage > 1) {
        html += `<button class="page-btn" data-page="1">1</button>`;
        if (startPage > 2) {
            html += `<span class="page-ellipsis">...</span>`;
        }
    }
    
    // 中间页码
    for (let i = startPage; i <= endPage; i++) {
        if (i === current) {
            html += `<button class="page-btn active" data-page="${i}">${i}</button>`;
        } else {
            html += `<button class="page-btn" data-page="${i}">${i}</button>`;
        }
    }
    
    // 最后一页
    if (endPage < pages) {
        if (endPage < pages - 1) {
            html += `<span class="page-ellipsis">...</span>`;
        }
        html += `<button class="page-btn" data-page="${pages}">${pages}</button>`;
    }
    
    // 下一页按钮
    if (current < pages) {
        html += `<button class="page-btn" data-page="${current + 1}"><i class="fas fa-chevron-right"></i></button>`;
    } else {
        html += `<button class="page-btn" disabled><i class="fas fa-chevron-right"></i></button>`;
    }
    
    $controls.html(html);
    
    // 绑定分页按钮事件 - 使用事件委托确保事件能正确触发
    $controls.off('click', '.page-btn').on('click', '.page-btn', function(e) {
        e.preventDefault();
        e.stopPropagation();
        
        const $btn = $(this);
        if ($btn.prop('disabled')) return;
        
        const page = $btn.data('page');
        console.log('分页点击:', page);
        searchMusic(currentKeyword, page, currentOrderBy, currentTag);
    });
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

function renderTags(tags) {
    const $filterTags = $('#filterTags');
    
    // 始终保留"全部"选项
    let html = '<div class="tag active" data-tag="">全部</div>';
    
    // 添加后端返回的标签
    tags.forEach(tag => {
        html += `<div class="tag" data-tag="${tag.tag}">${tag.tag}</div>`;
    });
    
    $filterTags.html(html);
    
    // 绑定标签点击事件
    $('.tag').off('click').on('click', function() {
        $('.tag').removeClass('active');
        $(this).addClass('active');
        const tag = $(this).data('tag') || '';
        searchMusic(currentKeyword, 1, currentOrderBy, tag);
    });
}

// ==================== 初始化和事件绑定 ====================

$(window).on('click', function (event) {
    const modal = $('#albumModal');
    if (event.target === modal[0]) {
        modal.css('display', 'none');
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
    
    // 设置排序默认值
    $('#sortSelect').val('publishDate');
    
    // 从 URL 读取状态
    const urlParams = new URLSearchParams(window.location.search);
    if (urlParams.has('keyword')) {
        currentKeyword = urlParams.get('keyword');
        $('.geo-search__input').val(currentKeyword);
    }
    if (urlParams.has('orderBy')) {
        currentOrderBy = urlParams.get('orderBy');
        $('#sortSelect').val(currentOrderBy);
    }
    if (urlParams.has('page')) {
        currentPage = parseInt(urlParams.get('page')) || 1;
    }
    
    // 如果有筛选标签，使用它进行搜索
    if (filterTag) {
        currentTag = filterTag;
        // 高亮选中的标签
        setTimeout(function() {
            $('.tag').each(function() {
                if ($(this).text() === filterTag) {
                    $(this).addClass('active');
                }
            });
        }, 500);
        searchMusic('', 1, 'publishDate', filterTag);
    } else {
        searchMusic('', 1, 'publishDate', '');
    }
    
    // 如果有选中的专辑ID，打开详情弹窗
    if (selectedAlbumId) {
        setTimeout(function() {
            updateAlbumModal(selectedAlbumId);
            openAlbumModal();
        }, 800);
    }
});

// 搜索按钮点击
$('.geo-search__btn').on('click', function () {
    const searchTerm = $('.geo-search__input').val();
    hideSuggestions();
    searchMusic(searchTerm, 1, currentOrderBy, currentTag);
});

// 搜索输入框事件
$('.geo-search__input').on('input', function() {
    const keyword = $(this).val().trim();
    
    if (keyword.length > 0) {
        fetchSuggestions(keyword);
    } else {
        renderSearchHistory();
    }
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

// 排序选择
$('#sortSelect').on('change', function() {
    currentOrderBy = $(this).val();
    searchMusic(currentKeyword, 1, currentOrderBy, currentTag);
});

// 分页跳转
$('#jumpBtn').on('click', function() {
    const page = parseInt($('#jumpInput').val()) || 1;
    const validPage = Math.max(1, Math.min(page, totalPages));
    searchMusic(currentKeyword, validPage, currentOrderBy, currentTag);
});

$('#jumpInput').on('keypress', function(e) {
    if (e.key === 'Enter') {
        $('#jumpBtn').click();
    }
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
