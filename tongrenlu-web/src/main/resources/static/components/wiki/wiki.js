// 统一 Wiki 百科页逻辑：词条列表（类型筛选 + 搜索 + 无限滚动） + 详情（URL 参数路由）

// ===== 列表状态 =====
let listType = 'all';
let listKeyword = '';
let listPage = 1;
let listLoading = false;
let listHasMore = true;
const LIST_PAGE_SIZE = 30;

// ===== 专辑分页状态（服务端分页，不在前端缓存全量专辑）=====
let currentTagId = null;
let albumPage = 1;
let albumTotal = 0;
let albumTotalPages = 1;
const ALBUM_PAGE_SIZE = 20;

$(function() {
    init();
    initMotion();
});

function init() {
    const params = new URLSearchParams(window.location.search);
    const id = params.get('id');
    const tag = params.get('tag');
    listType = normalizeType(params.get('type'));
    syncTypeTabs(listType);

    if (id || tag) {
        showDetail(id ? { id: id } : { tag: tag }, false);
    } else {
        showList(false);
    }

    initSearch();
    initTypeTabs();
    initScrollLoad();
    initDetailEvents();
    initHistory();
}

function normalizeType(value) {
    return value === 'artist' || value === 'event' ? value : 'all';
}

function syncTypeTabs(type) {
    $('.wiki-type-tab').removeClass('active')
        .filter('[data-type="' + type + '"]').addClass('active');
    // 导航高亮跟随类型：全部→百科，艺术家/展会→对应入口
    $('#navWikiAll').toggleClass('active', type === 'all');
    $('#navWikiArtist').toggleClass('active', type === 'artist');
    $('#navWikiEvent').toggleClass('active', type === 'event');
}

// ===== 动效（渐进增强）=====
function initMotion() {
    const progressBar = document.getElementById('wikiProgressBar');
    if (progressBar) {
        const updateProgress = function() {
            const doc = document.documentElement;
            const scrollable = doc.scrollHeight - window.innerHeight;
            progressBar.style.width = scrollable > 0
                ? (window.scrollY / scrollable * 100) + '%'
                : '0%';
        };
        window.addEventListener('scroll', updateProgress, { passive: true });
        window.addEventListener('resize', updateProgress);
        updateProgress();
    }

    armReveal(document.querySelectorAll('[data-rv]'));
}

function armReveal(revealEls) {
    if ('IntersectionObserver' in window && revealEls.length > 0) {
        const observer = new IntersectionObserver(function(entries) {
            entries.forEach(function(entry) {
                if (entry.isIntersecting) {
                    entry.target.classList.add('is-visible');
                    observer.unobserve(entry.target);
                }
            });
        }, { rootMargin: '0px 0px -8% 0px', threshold: 0.05 });
        revealEls.forEach(function(el) {
            el.classList.add('rv-armed');
            observer.observe(el);
        });
    }
}

function playDetailEnter() {
    const detail = document.getElementById('wikiDetailView');
    if (!detail) return;
    detail.classList.remove('is-entering');
    void detail.offsetWidth;
    detail.classList.add('is-entering');
}

// ===== 路由 =====
function showList(pushState = true) {
    $('#wikiDetailView').hide();
    $('#wikiListView').show();
    window.scrollTo(0, 0);

    if (pushState) {
        history.pushState({ view: 'list', type: listType }, '', listUrl(listType));
    }

    if ($('#wikiGrid').children().length === 0 && !listLoading) {
        listPage = 1;
        listHasMore = true;
        loadEntries(1, false);
    }
}

function listUrl(type) {
    return type === 'all' ? 'wiki.html' : 'wiki.html?type=' + encodeURIComponent(type);
}

function showDetail(param, pushState = true) {
    $('#wikiListView').hide();
    $('#wikiDetailView').show();
    $('#wikiDetailLoading').show();
    $('#wikiDetailError').hide();
    $('#wikiDetailContent').hide();
    window.scrollTo(0, 0);
    playDetailEnter();

    const detailUrl = param.id
        ? 'wiki.html?id=' + encodeURIComponent(param.id)
        : 'wiki.html?tag=' + encodeURIComponent(param.tag);
    if (pushState) {
        history.pushState({ view: 'detail', param: param }, '', detailUrl);
    }

    loadDetail(param);
}

function initHistory() {
    window.addEventListener('popstate', function(e) {
        const state = e.state;
        if (state && state.view === 'detail' && state.param) {
            showDetail(state.param, false);
        } else {
            if (state && state.type) {
                listType = normalizeType(state.type);
                syncTypeTabs(listType);
            }
            showList(false);
        }
    });
}

// ===== 列表视图 =====
function loadEntries(page = 1, append = false) {
    if (listLoading) return;
    listLoading = true;

    if (!append) {
        $('#wikiLoading').show();
        $('#wikiGrid').hide();
        $('#wikiEmpty').hide();
    }

    $.ajax({
        url: 'api/wiki/entries',
        method: 'GET',
        data: {
            type: listType,
            keyword: listKeyword || null,
            page: page,
            limit: LIST_PAGE_SIZE
        },
        dataType: 'json',
        success: function(data) {
            const entries = data.records || [];
            const total = data.total || 0;
            const totalPages = data.totalPages || 1;

            $('#navWikiCount').text(total);
            $('#wikiIndexCount').text(total);

            listHasMore = page < totalPages;
            listPage = page;

            if (append) {
                appendCards(entries);
            } else {
                renderCards(entries);
            }

            $('#wikiLoading').hide();
            $('#wikiGrid').show();

            if (entries.length === 0 && page === 1) {
                $('#wikiEmpty').show();
            }
            listLoading = false;
        },
        error: function(xhr, status, error) {
            console.error('加载词条失败:', error);
            $('#wikiLoading').hide();
            $('#wikiGrid').show();
            if (!append) $('#wikiEmpty').show();
            listLoading = false;
        }
    });
}

function renderCards(entries) {
    const grid = $('#wikiGrid');
    grid.empty();
    $('#wikiEmpty').hide();
    appendCards(entries);
}

function appendCards(entries) {
    const grid = $('#wikiGrid');
    const startDelayIndex = grid.children().length % 12;
    (entries || []).forEach(function(entry, i) {
        const card = createCard(entry);
        card[0].style.setProperty('--i', (startDelayIndex + i) % 12);
        grid.append(card);
    });
}

function createCard(entry) {
    const type = entry.type === 'event' ? 'event' : 'artist';
    const isEvent = type === 'event';
    const name = entry.name || '未命名词条';
    const tagId = pickField(entry, 'tagId', 'id');
    const albumCount = pickField(entry, 'albumCount', 'album_count') || 0;
    const coverUrl = pickField(entry, 'coverUrl', 'cloud_music_pic_url');

    const card = $('<div>', {
        class: 'wiki-card' + (isEvent ? ' wiki-card--event' : ''),
        'data-tag-id': tagId,
        'data-entry-type': type,
        tabindex: 0,
        role: 'link',
        'aria-label': '查看 ' + name + ' 的百科词条'
    });

    const cover = $('<div>', { class: 'wiki-card__cover' });
    if (!isEvent && coverUrl) {
        const optimizedUrl = typeof getOptimizedImageUrl === 'function'
            ? getOptimizedImageUrl(coverUrl, 200, 200)
            : coverUrl;
        cover.append($('<img>', {
            class: 'wiki-card__image',
            src: optimizedUrl,
            alt: name,
            loading: 'lazy',
            onerror: "this.parentElement.innerHTML = '<div class=\"wiki-card__placeholder\"><i class=\"fas fa-user\"></i></div>'"
        }));
    } else {
        cover.append($('<div>', {
            class: 'wiki-card__placeholder',
            html: isEvent ? '<i class="fas fa-calendar-alt"></i>' : '<i class="fas fa-user"></i>'
        }));
    }
    cover.append($('<span>', {
        class: 'wiki-card__badge',
        text: isEvent ? '展会' : '艺术家'
    }));

    const info = $('<div>', { class: 'wiki-card__info' });
    info.append($('<h3>', { class: 'wiki-card__name', text: name, title: name }));
    info.append($('<div>', { class: 'wiki-card__count', text: albumCount + ' 张专辑' }));

    card.append(cover, info);

    card.on('click', function() { showDetail({ id: tagId }); });
    card.on('keydown', function(e) {
        if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault();
            showDetail({ id: tagId });
        }
    });
    return card;
}

function initTypeTabs() {
    $('.wiki-type-tab').on('click', function() {
        const type = normalizeType($(this).data('type'));
        if (type === listType && $('#wikiGrid').children().length > 0) return;
        listType = type;
        syncTypeTabs(type);
        listKeyword = '';
        $('#wikiSearchInput').val('');
        $('#wikiSearchClear').removeClass('visible');
        listPage = 1;
        listHasMore = true;
        $('#wikiGrid').empty();
        history.replaceState({ view: 'list', type: type }, '', listUrl(type));
        loadEntries(1, false);
    });
}

// 搜索（防抖）
function initSearch() {
    const searchInput = $('#wikiSearchInput');
    const searchClear = $('#wikiSearchClear');
    let searchTimer = null;

    searchInput.on('input', function() {
        const keyword = $(this).val().trim();
        searchClear.toggleClass('visible', keyword.length > 0);

        if (searchTimer) clearTimeout(searchTimer);
        searchTimer = setTimeout(function() {
            listKeyword = keyword;
            listPage = 1;
            listHasMore = true;
            loadEntries(1, false);
        }, 300);
    });

    searchClear.on('click', function() {
        searchInput.val('');
        listKeyword = '';
        $(this).removeClass('visible');
        listPage = 1;
        listHasMore = true;
        loadEntries(1, false);
    });
}

// 无限滚动
function initScrollLoad() {
    $(window).on('scroll', function() {
        if (listLoading || !listHasMore) return;
        if ($('#wikiListView').is(':hidden')) return;
        const scrollTop = $(window).scrollTop();
        const windowHeight = $(window).height();
        const docHeight = $(document).height();
        if (scrollTop + windowHeight >= docHeight - 200) {
            loadEntries(listPage + 1, true);
        }
    });
}

// ===== 详情视图 =====
function loadDetail(param) {
    $.ajax({
        url: 'api/wiki/detail',
        method: 'GET',
        data: param.id ? { id: param.id } : { tag: param.tag },
        dataType: 'json',
        success: function(entry) { renderDetail(entry); },
        error: function(xhr, status, error) {
            console.error('加载词条详情失败:', error);
            $('#wikiDetailLoading').hide();
            $('#wikiDetailError').show();
        }
    });
}

/**
 * 展会词条的 THBWiki 外链：m_tag 里多为届次名（C91 / 例大祭9 / 東方紅楼夢11），
 * THBWiki 通常只有系列总览页，按系列映射过去；未识别的回落到「活动导航」。
 * 这些页面均已在 THBWiki 上验证有效（Special:Search / index.php?search 会被 WAF 拦成欢迎页，不能用）。
 */
function thbwikiEventUrl(name) {
    const n = String(name || '');
    if (/^C\d+/i.test(n) || /comic\s*market/i.test(n) || n.includes('コミケ')) {
        return 'https://thbwiki.cc/Comic_Market';
    }
    if (n.includes('例大祭')) return 'https://thbwiki.cc/博丽神社例大祭';
    if (n.includes('紅楼夢') || n.includes('红楼梦')) return 'https://thbwiki.cc/东方红楼梦';
    if (/^M3/i.test(n) || n.includes('M3')) return 'https://thbwiki.cc/M3';
    return 'https://thbwiki.cc/活动导航';
}

function renderDetail(entry) {
    $('#wikiDetailLoading').hide();
    $('#wikiDetailError').hide();
    $('#wikiDetailContent').show();

    const name = entry.name || '--';
    const type = entry.type === 'event' ? 'event' : 'artist';
    const isEvent = type === 'event';
    const coverUrl = pickField(entry, 'coverUrl', 'cloud_music_pic_url');
    const albumCount = pickField(entry, 'albumCount', 'album_count') || 0;
    const cloudMusicId = pickField(entry, 'cloudMusicId', 'cloud_music_id');
    const wikiContent = pickField(entry, 'wikiContent', 'wiki_content');
    const description = entry.description;
    const sourceUrl = safeHttpUrl(pickField(entry, 'wikiSourceUrl', 'wiki_source_url'));
    // 展会词条生成时不抓 THBWiki，用系列映射补一个外部资料链接
    const thbUrl = sourceUrl || (isEvent ? thbwikiEventUrl(name) : null);
    const wikiTags = entry.wikiTags || [];

    // 头像
    const avatar = $('#wikiHeroAvatar');
    avatar.empty();
    if (!isEvent && coverUrl) {
        const optimizedUrl = typeof getOptimizedImageUrl === 'function'
            ? getOptimizedImageUrl(coverUrl, 300, 300)
            : coverUrl;
        avatar.append($('<img>', {
            src: optimizedUrl,
            alt: name,
            onerror: "this.parentElement.innerHTML = '<div class=\"wiki-card__placeholder\"><i class=\"fas fa-user\"></i></div>'"
        }));
    } else {
        avatar.append($('<div>', {
            class: 'wiki-card__placeholder',
            html: isEvent ? '<i class="fas fa-calendar-alt"></i>' : '<i class="fas fa-user"></i>'
        }));
    }

    // 名称 + 统计
    $('#wikiHeroName').text(name);
    const stats = $('#wikiHeroStats');
    stats.empty();
    stats.append($('<span>', {
        class: 'geo-wiki-stat geo-wiki-stat--accent',
        html: '<i class="fas ' + (isEvent ? 'fa-calendar-alt' : 'fa-user') + '"></i> ' + (isEvent ? '展会词条' : '艺术家词条')
    }));
    stats.append($('<span>', {
        class: 'geo-wiki-stat',
        html: '<i class="fas fa-compact-disc"></i> ' + albumCount + ' 张专辑'
    }));

    // 外部 / 站内链接
    const links = $('#wikiHeroLinks');
    links.empty();
    if (thbUrl) {
        links.append($('<a>', {
            class: 'geo-wiki-link', href: thbUrl, target: '_blank', rel: 'noopener',
            html: '<i class="fas fa-book"></i> THBWiki'
        }));
    }
    if (!isEvent && cloudMusicId) {
        links.append($('<a>', {
            class: 'geo-wiki-link', href: 'https://music.163.com/#/artist?id=' + cloudMusicId,
            target: '_blank', rel: 'noopener',
            html: '<i class="fas fa-external-link-alt"></i> 网易云音乐'
        }));
    }

    // 正文：词条内容优先，回落简介
    const desc = $('#wikiDesc');
    desc.removeClass('geo-wiki-desc--empty');
    if (wikiContent && String(wikiContent).trim()) {
        desc.html(renderMarkdown(String(wikiContent)));
    } else if (description && String(description).trim()) {
        desc.html(renderMarkdown(String(description)));
    } else {
        desc.addClass('geo-wiki-desc--empty');
        desc.text('暂无词条内容');
    }

    renderWikiTags(wikiTags);

    currentTagId = entry.tagId || null;
    albumPage = 1;
    loadDetailAlbums(1);
}

// 加载词条关联专辑（服务端分页，按 tagId 直接查，避免 pageSize=1000 的全量拉取）
function loadDetailAlbums(page) {
    const container = $('#wikiAlbums');
    container.html('<div style="text-align:center; padding:2rem; color:var(--wiki-muted);"><i class="fas fa-spinner fa-spin"></i> 加载专辑...</div>');
    $('#wikiAlbumsPagination').hide();

    if (!currentTagId) {
        container.html('<div style="text-align:center; padding:2rem; color:var(--wiki-muted);">暂无专辑</div>');
        return;
    }

    $.ajax({
        url: 'api/wiki/' + encodeURIComponent(currentTagId) + '/albums',
        method: 'GET',
        data: { page: page, limit: ALBUM_PAGE_SIZE },
        dataType: 'json',
        success: function(data) {
            albumPage = data.page || page;
            albumTotal = data.total || 0;
            albumTotalPages = data.totalPages || 1;
            renderDetailAlbums(data.records || []);
            renderDetailAlbumPagination();
        },
        error: function(xhr, status, error) {
            console.error('加载专辑失败:', error);
            container.html('<div style="text-align:center; padding:2rem; color:var(--wiki-muted);">加载失败</div>');
        }
    });
}

function renderDetailAlbums(albums) {
    const container = $('#wikiAlbums');
    if (!albums || albums.length === 0) {
        container.html('<div style="text-align:center; padding:2rem; color:var(--wiki-muted);">暂无专辑</div>');
        $('#wikiAlbumsPagination').hide();
        return;
    }

    container.empty();
    albums.forEach(function(album, i) {
        const item = $('<div>', {
            class: 'wiki-album-item',
            'data-album-id': album.id,
            tabindex: 0,
            role: 'button',
            'aria-label': '播放专辑 ' + (album.title || '')
        });
        item[0].style.setProperty('--i', i % ALBUM_PAGE_SIZE);

        const cover = $('<div>', { class: 'wiki-album-item__cover' });
        const picUrl = pickField(album, 'cloudMusicPicUrl', 'cloud_music_pic_url');
        if (picUrl) {
            const optimizedUrl = typeof getOptimizedImageUrl === 'function'
                ? getOptimizedImageUrl(picUrl, 150, 150)
                : picUrl;
            cover.append($('<img>', { src: optimizedUrl, alt: album.title || '', loading: 'lazy' }));
        }
        item.append(cover, $('<div>', {
            class: 'wiki-album-item__title',
            text: album.title || '未知专辑',
            title: album.title || ''
        }));

        item.on('click', function() { openFoliaPlayer(album.id, { newTab: false }); });
        item.on('keydown', function(e) {
            if (e.key === 'Enter' || e.key === ' ') {
                e.preventDefault();
                openFoliaPlayer(album.id, { newTab: false });
            }
        });
        container.append(item);
    });
}

function renderDetailAlbumPagination() {
    const totalPages = albumTotalPages;
    const pagination = $('#wikiAlbumsPagination');
    if (totalPages <= 1) {
        pagination.hide();
        return;
    }
    pagination.show();

    let html = '<span class="wiki-albums-pagination__info">共 ' + albumTotal + ' 张</span>';
    html += albumPage > 1
        ? '<button class="wiki-albums-pagination__btn" data-page="' + (albumPage - 1) + '"><i class="fas fa-chevron-left"></i></button>'
        : '<button class="wiki-albums-pagination__btn" disabled><i class="fas fa-chevron-left"></i></button>';

    const maxVisible = 5;
    let startPage = Math.max(1, albumPage - Math.floor(maxVisible / 2));
    let endPage = Math.min(totalPages, startPage + maxVisible - 1);
    if (endPage - startPage < maxVisible - 1) startPage = Math.max(1, endPage - maxVisible + 1);

    if (startPage > 1) {
        html += '<button class="wiki-albums-pagination__btn" data-page="1">1</button>';
        if (startPage > 2) html += '<span class="wiki-albums-pagination__ellipsis">...</span>';
    }
    for (let i = startPage; i <= endPage; i++) {
        html += '<button class="wiki-albums-pagination__btn' + (i === albumPage ? ' active' : '') +
                '" data-page="' + i + '">' + i + '</button>';
    }
    if (endPage < totalPages) {
        if (endPage < totalPages - 1) html += '<span class="wiki-albums-pagination__ellipsis">...</span>';
        html += '<button class="wiki-albums-pagination__btn" data-page="' + totalPages + '">' + totalPages + '</button>';
    }
    html += albumPage < totalPages
        ? '<button class="wiki-albums-pagination__btn" data-page="' + (albumPage + 1) + '"><i class="fas fa-chevron-right"></i></button>'
        : '<button class="wiki-albums-pagination__btn" disabled><i class="fas fa-chevron-right"></i></button>';

    pagination.html(html);
    pagination.find('.wiki-albums-pagination__btn[data-page]').on('click', function() {
        const page = parseInt($(this).data('page'));
        if (page && page !== albumPage) {
            $('#wikiAlbums')[0].scrollIntoView({ behavior: 'smooth', block: 'start' });
            loadDetailAlbums(page);
        }
    });
}

function initDetailEvents() {
    $('#wikiBackBtn').on('click', function() {
        if (history.state && history.state.view === 'detail') {
            history.back();
        } else {
            showList();
        }
    });
}

// 互联标签：命中词条渲染为跳转链接，style 为纯 chip
function renderWikiTags(tags) {
    const section = $('#wikiTagsSection');
    const container = $('#wikiTagChips');
    container.empty();

    if (!Array.isArray(tags) || tags.length === 0) {
        section.hide();
        return;
    }

    tags.forEach(function(item) {
        const tag = typeof item === 'string' ? { tag: item, type: 'style' } : item;
        if (!tag || !tag.tag) return;
        const label = escapeHtml(tag.tag);

        if (tag.tagId) {
            container.append($('<a>', {
                class: 'geo-wiki-tag geo-wiki-tag--artist',
                href: 'wiki.html?id=' + encodeURIComponent(tag.tagId),
                title: '查看 ' + tag.tag + ' 的百科词条',
                html: '<i class="fas fa-link"></i> ' + label
            }));
        } else if (tag.artistId || tag.type === 'artist') {
            // 迁移前的旧标签（只有 artistId）按词条名查询，后端会回退按 m_artist.name 解析
            container.append($('<a>', {
                class: 'geo-wiki-tag geo-wiki-tag--artist',
                href: 'wiki.html?tag=' + encodeURIComponent(tag.tag),
                title: '查看 ' + tag.tag + ' 的百科词条',
                html: '<i class="fas fa-user-astronaut"></i> ' + label
            }));
        } else {
            container.append($('<span>', {
                class: 'geo-wiki-tag geo-wiki-tag--style',
                html: '<i class="fas fa-tag"></i> ' + label
            }));
        }
    });

    section.show();
}
