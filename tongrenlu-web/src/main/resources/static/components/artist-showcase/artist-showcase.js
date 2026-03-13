// 艺术家展示页面逻辑 - 卡片网格布局（分页版本）

let currentPage = 1;
let currentKeyword = '';
let isLoading = false;
let hasMore = true;
const PAGE_SIZE = 30;

// 分页状态（弹窗专辑）
let allAlbums = [];
let currentArtistName = '';
let albumCurrentPage = 1;
const ALBUM_PAGE_SIZE = 20;

// 页面加载完成后初始化
$(function() {
    loadArtists();
    initSearch();
    initModal();
    initScrollLoad();
});

// 加载艺术家数据（分页）
function loadArtists(page = 1, append = false) {
    if (isLoading) return;
    isLoading = true;
    
    if (!append) {
        $('#artistLoading').show();
        $('#artistGrid').hide();
        $('#artistEmpty').hide();
    }
    
    $.ajax({
        url: 'api/music/artists',
        method: 'GET',
        data: {
            keyword: currentKeyword || null,
            page: page,
            limit: PAGE_SIZE
        },
        dataType: 'json',
        success: function(data) {
            const artists = data.records || [];
            const total = data.total || 0;
            const totalPages = data.totalPages || 1;
            
            $('#navArtistCount').text(total);
            
            hasMore = page < totalPages;
            currentPage = page;
            
            if (append) {
                appendCards(artists);
            } else {
                renderCards(artists);
            }
            
            $('#artistLoading').hide();
            $('#artistGrid').show();
            
            if (artists.length === 0 && page === 1) {
                $('#artistEmpty').show();
            }
            
            isLoading = false;
        },
        error: function(xhr, status, error) {
            console.error('加载艺术家失败:', error);
            $('#artistLoading').hide();
            $('#artistEmpty').show();
            isLoading = false;
        }
    });
}

// 渲染卡片
function renderCards(artists) {
    const grid = $('#artistGrid');
    grid.empty();
    
    if (!artists || artists.length === 0) {
        $('#artistEmpty').show();
        return;
    }
    
    $('#artistEmpty').hide();
    appendCards(artists);
}

// 追加卡片
function appendCards(artists) {
    const grid = $('#artistGrid');
    
    artists.forEach((artist) => {
        const card = createCard(artist);
        grid.append(card);
    });
}

// 创建单个卡片
function createCard(artist) {
    const card = $('<div>', {
        class: 'artist-card',
        'data-name': artist.name.toLowerCase(),
        'data-artist-name': artist.name
    });
    
    // 封面区域
    const cover = $('<div>', {
        class: 'artist-card__cover'
    });
    
    if (artist.cloud_music_pic_url) {
        // 使用优化后的图片URL（200x200）
        const optimizedUrl = typeof getOptimizedImageUrl === 'function' 
            ? getOptimizedImageUrl(artist.cloud_music_pic_url, 200, 200) 
            : artist.cloud_music_pic_url;
        const img = $('<img>', {
            class: 'artist-card__image',
            src: optimizedUrl,
            alt: artist.name,
            loading: 'lazy',
            onerror: 'this.parentElement.innerHTML = \'<div class="artist-card__placeholder"><i class="fas fa-user"></i></div>\''
        });
        cover.append(img);
    } else {
        const placeholder = $('<div>', {
            class: 'artist-card__placeholder',
            html: '<i class="fas fa-user"></i>'
        });
        cover.append(placeholder);
    }
    
    // 专辑数量角标
    if (artist.album_count) {
        const badge = $('<span>', {
            class: 'artist-card__badge',
            text: artist.album_count + '张'
        });
        cover.append(badge);
    }
    
    // 信息区域
    const info = $('<div>', {
        class: 'artist-card__info'
    });
    
    const name = $('<h3>', {
        class: 'artist-card__name',
        text: artist.name,
        title: artist.name
    });
    
    info.append(name);
    card.append(cover, info);
    
    // 点击打开弹窗
    card.on('click', function() {
        openArtistModal(artist.name);
    });
    
    return card;
}

// 初始化搜索功能（后端搜索）
function initSearch() {
    const searchInput = $('#artistSearchInput');
    const searchClear = $('#artistSearchClear');
    let searchTimer = null;
    
    // 防抖搜索
    searchInput.on('input', function() {
        const keyword = $(this).val().trim();
        
        // 显示/隐藏清除按钮
        if (keyword.length > 0) {
            searchClear.show();
        } else {
            searchClear.hide();
        }
        
        // 清除之前的定时器
        if (searchTimer) {
            clearTimeout(searchTimer);
        }
        
        // 防抖 300ms
        searchTimer = setTimeout(function() {
            currentKeyword = keyword;
            currentPage = 1;
            hasMore = true;
            loadArtists(1, false);
        }, 300);
    });
    
    // 清除搜索
    searchClear.on('click', function() {
        searchInput.val('');
        currentKeyword = '';
        $(this).hide();
        currentPage = 1;
        hasMore = true;
        loadArtists(1, false);
    });
}

// 初始化滚动加载
function initScrollLoad() {
    $(window).on('scroll', function() {
        if (isLoading || !hasMore) return;
        
        const scrollTop = $(window).scrollTop();
        const windowHeight = $(window).height();
        const docHeight = $(document).height();
        
        // 距离底部 200px 时加载更多
        if (scrollTop + windowHeight >= docHeight - 200) {
            loadArtists(currentPage + 1, true);
        }
    });
}

// 初始化弹窗
function initModal() {
    // 关闭按钮
    $('#artistModalClose').on('click', closeArtistModal);
    
    // 点击背景关闭
    $('#artistModal').on('click', function(e) {
        if (e.target === this) {
            closeArtistModal();
        }
    });
    
    // ESC 关闭
    $(document).on('keydown', function(e) {
        if (e.key === 'Escape') {
            closeArtistModal();
        }
    });
}

// 打开艺术家弹窗
function openArtistModal(artistName) {
    currentArtistName = artistName;
    albumCurrentPage = 1;
    allAlbums = [];
    
    $('#artistModalTitle').text(artistName + ' 的专辑');
    $('#artistModalBody').html('<div class="artist-modal__loading"><i class="fas fa-spinner fa-spin"></i> 加载中...</div>');
    $('#artistModalPagination').hide();
    $('#artistModal').addClass('active');
    
    // 加载专辑列表
    loadArtistAlbums(artistName);
}

// 关闭艺术家弹窗
function closeArtistModal() {
    $('#artistModal').removeClass('active');
}

// 加载艺术家专辑
function loadArtistAlbums(artistName) {
    $.ajax({
        url: 'api/music/search',
        method: 'GET',
        data: {
            tag: artistName,
            pageSize: 1000
        },
        dataType: 'json',
        success: function(data) {
            allAlbums = data.records || data || [];
            albumCurrentPage = 1;
            renderAlbums();
            renderAlbumPagination();
        },
        error: function(xhr, status, error) {
            console.error('加载专辑失败:', error);
            $('#artistModalBody').html('<div class="artist-modal__empty">加载失败</div>');
            $('#artistModalPagination').hide();
        }
    });
}

// 渲染专辑列表（当前页）
function renderAlbums() {
    const body = $('#artistModalBody');
    
    if (!allAlbums || allAlbums.length === 0) {
        body.html('<div class="artist-modal__empty">暂无专辑</div>');
        $('#artistModalPagination').hide();
        return;
    }
    
    // 计算当前页的数据
    const startIndex = (albumCurrentPage - 1) * ALBUM_PAGE_SIZE;
    const endIndex = Math.min(startIndex + ALBUM_PAGE_SIZE, allAlbums.length);
    const pageAlbums = allAlbums.slice(startIndex, endIndex);
    
    const list = $('<div>', {
        class: 'artist-album-list'
    });
    
    pageAlbums.forEach(function(album) {
        const item = $('<div>', {
            class: 'artist-album-item',
            'data-album-id': album.id
        });
        
        // 封面
        const cover = $('<div>', {
            class: 'artist-album-item__cover'
        });
        
        if (album.cloudMusicPicUrl) {
            // 使用优化后的图片URL（150x150）
            const optimizedUrl = typeof getOptimizedImageUrl === 'function' 
                ? getOptimizedImageUrl(album.cloudMusicPicUrl, 150, 150) 
                : album.cloudMusicPicUrl;
            const img = $('<img>', {
                src: optimizedUrl,
                alt: album.title,
                loading: 'lazy'
            });
            cover.append(img);
        }
        
        // 标题
        const title = $('<div>', {
            class: 'artist-album-item__title',
            text: album.title,
            title: album.title
        });
        
        item.append(cover, title);
        
        // 点击跳转到播放器页面
        item.on('click', function() {
            closeArtistModal();
            window.location.href = 'player.html?album=' + album.id;
        });
        
        list.append(item);
    });
    
    body.empty().append(list);
}

// 渲染分页按钮
function renderAlbumPagination() {
    const totalPages = Math.ceil(allAlbums.length / ALBUM_PAGE_SIZE);
    const $pagination = $('#artistModalPagination');
    
    if (totalPages <= 1) {
        $pagination.hide();
        return;
    }
    
    $pagination.show();
    
    let html = '<div class="artist-pagination">';
    
    // 总数信息
    html += '<span class="artist-pagination__info">共 ' + allAlbums.length + ' 张</span>';
    
    // 上一页
    if (albumCurrentPage > 1) {
        html += '<button class="artist-pagination__btn" data-page="' + (albumCurrentPage - 1) + '"><i class="fas fa-chevron-left"></i></button>';
    } else {
        html += '<button class="artist-pagination__btn" disabled><i class="fas fa-chevron-left"></i></button>';
    }
    
    // 页码
    const maxVisible = 5;
    let startPage = Math.max(1, albumCurrentPage - Math.floor(maxVisible / 2));
    let endPage = Math.min(totalPages, startPage + maxVisible - 1);
    
    if (endPage - startPage < maxVisible - 1) {
        startPage = Math.max(1, endPage - maxVisible + 1);
    }
    
    if (startPage > 1) {
        html += '<button class="artist-pagination__btn" data-page="1">1</button>';
        if (startPage > 2) {
            html += '<span class="artist-pagination__ellipsis">...</span>';
        }
    }
    
    for (let i = startPage; i <= endPage; i++) {
        if (i === albumCurrentPage) {
            html += '<button class="artist-pagination__btn active">' + i + '</button>';
        } else {
            html += '<button class="artist-pagination__btn" data-page="' + i + '">' + i + '</button>';
        }
    }
    
    if (endPage < totalPages) {
        if (endPage < totalPages - 1) {
            html += '<span class="artist-pagination__ellipsis">...</span>';
        }
        html += '<button class="artist-pagination__btn" data-page="' + totalPages + '">' + totalPages + '</button>';
    }
    
    // 下一页
    if (albumCurrentPage < totalPages) {
        html += '<button class="artist-pagination__btn" data-page="' + (albumCurrentPage + 1) + '"><i class="fas fa-chevron-right"></i></button>';
    } else {
        html += '<button class="artist-pagination__btn" disabled><i class="fas fa-chevron-right"></i></button>';
    }
    
    html += '</div>';
    
    $pagination.html(html);
    
    // 绑定分页按钮事件
    $pagination.find('.artist-pagination__btn[data-page]').on('click', function() {
        const page = parseInt($(this).data('page'));
        if (page && page !== albumCurrentPage) {
            albumCurrentPage = page;
            renderAlbums();
            renderAlbumPagination();
            // 滚动到顶部
            $('#artistModalBody').scrollTop(0);
        }
    });
}
