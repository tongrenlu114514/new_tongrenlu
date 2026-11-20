// 艺术家展示页：列表数据来自 api/artists（m_artist，按 tag_id 去重），
// 详情正文来自 api/artists/{id}（m_okf_entry 词条，缺失时回退 m_artist.description）。
// 列表 / 详情是同一页面的两个视图，用 ?id= 做 history 路由。

// ===== 列表状态 =====
let listKeyword = '';
let listPage = 1;
let listLoading = false;
let listHasMore = true;
const LIST_PAGE_SIZE = 30;

// ===== 详情状态（专辑为服务端分页）=====
let currentArtistId = null;
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
    const keyword = params.get('keyword');

    if (keyword) {
        listKeyword = keyword;
        $('#artistSearchInput').val(keyword);
        $('#artistSearchClear').addClass('visible');
    }

    if (id) {
        // 直接深链进详情时列表不会加载，单独取一次总数填导航角标
        loadArtistCount();
        showDetail({ id: id }, false);
    } else {
        showList(false);
    }

    initSearch();
    initScrollLoad();
    initDetailEvents();
    initHistory();
}

// ===== 动效（渐进增强）=====
function initMotion() {
    const revealEls = document.querySelectorAll('[data-rv]');
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
    const detail = document.getElementById('artistDetailView');
    if (!detail) return;
    detail.classList.remove('is-entering');
    void detail.offsetWidth;
    detail.classList.add('is-entering');
}

// ===== 路由 =====
function listUrl() {
    return 'artist-showcase.html';
}

function detailUrl(id) {
    return 'artist-showcase.html?id=' + encodeURIComponent(id);
}

function showList(pushState = true) {
    $('#artistDetailView').hide();
    $('#artistListView').show();
    window.scrollTo(0, 0);

    if (pushState) {
        history.pushState({ view: 'list' }, '', listUrl());
    }

    if ($('#artistGrid').children().length === 0 && !listLoading) {
        listPage = 1;
        listHasMore = true;
        loadArtists(1, false);
    }
}

function showDetail(param, pushState = true) {
    currentArtistId = param.id;
    $('#artistListView').hide();
    $('#artistDetailView').show();
    $('#artistDetailContent').hide();
    $('#artistDetailError').hide();
    $('#artistDetailLoading').show();
    playDetailEnter();
    window.scrollTo(0, 0);

    if (pushState) {
        history.pushState({ view: 'detail', id: param.id }, '', detailUrl(param.id));
    }

    loadDetail(param);
}

function initHistory() {
    window.addEventListener('popstate', function(e) {
        const state = e.state;
        if (state && state.view === 'detail' && state.id) {
            showDetail({ id: state.id }, false);
        } else {
            showList(false);
        }
    });
}

// ===== 列表 =====
function loadArtists(page, append) {
    if (listLoading) return;
    listLoading = true;

    if (!append) {
        $('#artistLoading').show();
        $('#artistGrid').hide();
        $('#artistEmpty').hide();
    }

    $.ajax({
        url: 'api/artists',
        method: 'GET',
        data: {
            keyword: listKeyword || null,
            page: page,
            limit: LIST_PAGE_SIZE
        },
        dataType: 'json',
        success: function(data) {
            const records = data.records || [];
            const total = data.total || 0;
            const totalPages = data.totalPages || 1;

            $('#navArtistCount').text(total);
            $('#artistListCount').text(total);

            listPage = page;
            listHasMore = page < totalPages;

            if (append) {
                appendCards(records);
            } else {
                renderCards(records);
            }

            $('#artistLoading').hide();
            $('#artistGrid').show();
            if (records.length === 0 && page === 1) {
                $('#artistEmpty').show();
            }
            listLoading = false;
        },
        error: function(xhr, status, error) {
            console.error('加载艺术家失败:', error);
            $('#artistLoading').hide();
            $('#artistGrid').show();
            $('#artistEmpty').show();
            listLoading = false;
        }
    });
}

// 只取总数（深链进详情时用，避免整页列表请求）
function loadArtistCount() {
    $.ajax({
        url: 'api/artists',
        method: 'GET',
        data: { page: 1, limit: 1 },
        dataType: 'json',
        success: function(data) {
            $('#navArtistCount').text(data.total || 0);
        }
    });
}

function renderCards(records) {
    const grid = $('#artistGrid');
    grid.empty();
    if (!records || records.length === 0) {
        $('#artistEmpty').show();
        return;
    }
    $('#artistEmpty').hide();
    appendCards(records);
}

function appendCards(records) {
    const grid = $('#artistGrid');
    const startDelayIndex = grid.children().length % 12;
    (records || []).forEach(function(artist, i) {
        const card = createCard(artist);
        card[0].style.setProperty('--i', (startDelayIndex + i) % 12);
        grid.append(card);
    });
}

function createCard(artist) {
    const name = artist.name || '未命名艺术家';
    const artistId = artist.id;
    const albumCount = artist.albumCount || 0;
    const coverUrl = artist.cloudMusicPicUrl;

    const card = $('<div>', {
        class: 'wiki-card',
        'data-artist-id': artistId,
        tabindex: 0,
        role: 'link',
        'aria-label': '查看艺术家 ' + name
    });

    const cover = $('<div>', { class: 'wiki-card__cover' });
    if (coverUrl) {
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
            html: '<i class="fas fa-user"></i>'
        }));
    }
    cover.append($('<span>', {
        class: 'wiki-card__badge',
        text: albumCount + ' 张'
    }));

    const info = $('<div>', { class: 'wiki-card__info' });
    info.append($('<h3>', { class: 'wiki-card__name', text: name, title: name }));
    info.append($('<div>', { class: 'wiki-card__count', text: '艺术家' }));

    card.append(cover, info);
    card.on('click', function() { showDetail({ id: artistId }); });
    card.on('keydown', function(e) {
        if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault();
            showDetail({ id: artistId });
        }
    });
    return card;
}

// 搜索（防抖 300ms）
function initSearch() {
    const searchInput = $('#artistSearchInput');
    const searchClear = $('#artistSearchClear');
    let searchTimer = null;

    searchInput.on('input', function() {
        const keyword = $(this).val().trim();
        searchClear.toggleClass('visible', keyword.length > 0);

        if (searchTimer) clearTimeout(searchTimer);
        searchTimer = setTimeout(function() {
            listKeyword = keyword;
            listPage = 1;
            listHasMore = true;
            loadArtists(1, false);
        }, 300);
    });

    searchClear.on('click', function() {
        searchInput.val('');
        listKeyword = '';
        $(this).removeClass('visible');
        listPage = 1;
        listHasMore = true;
        loadArtists(1, false);
    });
}

// 无限滚动（详情视图打开时不再翻列表）
function initScrollLoad() {
    // rAF 节流：与 wiki.js 同一写法，避免每个 scroll 事件都读一次布局
    let scrollQueued = false;
    $(window).on('scroll', function() {
        if (scrollQueued || listLoading || !listHasMore) return;
        scrollQueued = true;
        window.requestAnimationFrame(function() {
            scrollQueued = false;
            if (listLoading || !listHasMore) return;
            if ($('#artistListView').is(':hidden')) return;
            const scrollTop = window.scrollY;
            const windowHeight = window.innerHeight;
            const docHeight = document.documentElement.scrollHeight;
            if (scrollTop + windowHeight >= docHeight - 200) {
                loadArtists(listPage + 1, true);
            }
        });
    });
}

// ===== 详情 =====
function loadDetail(param) {
    $.ajax({
        url: 'api/artists/' + encodeURIComponent(param.id),
        method: 'GET',
        dataType: 'json',
        success: function(artist) {
            renderDetail(artist);
        },
        error: function(xhr, status, error) {
            console.error('加载艺术家详情失败:', error);
            $('#artistDetailLoading').hide();
            $('#artistDetailContent').hide();
            $('#artistDetailError').show();
        }
    });
}

function renderDetail(artist) {
    $('#artistDetailLoading').hide();
    $('#artistDetailError').hide();
    $('#artistDetailContent').show();

    const name = artist.name || '--';
    const albumCount = artist.albumCount || 0;
    const entry = artist.entry || null;

    // 头像
    const avatar = $('#artistHeroAvatar');
    avatar.empty();
    if (artist.cloudMusicPicUrl) {
        const optimizedUrl = typeof getOptimizedImageUrl === 'function'
            ? getOptimizedImageUrl(artist.cloudMusicPicUrl, 300, 300)
            : artist.cloudMusicPicUrl;
        avatar.append($('<img>', {
            src: optimizedUrl,
            alt: name,
            onerror: "this.parentElement.innerHTML = '<div class=\"wiki-card__placeholder\"><i class=\"fas fa-user\"></i></div>'"
        }));
    } else {
        avatar.append($('<div>', {
            class: 'wiki-card__placeholder',
            html: '<i class="fas fa-user"></i>'
        }));
    }

    // 名称 + 统计
    $('#artistHeroName').text(name);
    const stats = $('#artistHeroStats');
    stats.empty();
    stats.append($('<span>', {
        class: 'geo-wiki-stat geo-wiki-stat--accent',
        html: '<i class="fas fa-user"></i> 艺术家'
    }));
    stats.append($('<span>', {
        class: 'geo-wiki-stat',
        html: '<i class="fas fa-compact-disc"></i> ' + albumCount + ' 张专辑'
    }));

    // 外链：OKF sources 优先，其次 THBWiki，最后补网易云
    const links = $('#artistHeroLinks');
    links.empty();
    const sources = (entry && Array.isArray(entry.sources)) ? entry.sources : [];
    let hasThbwiki = false;
    let hasNetease = false;
    sources.forEach(function(source) {
        const href = safeHttpUrl(source.resource);
        if (!href) return;
        if (/thbwiki/i.test(source.id || '') || /thbwiki/i.test(href)) hasThbwiki = true;
        if (/music\.163\.com/i.test(href)) hasNetease = true;
        links.append($('<a>', {
            class: 'geo-wiki-link', href: href, target: '_blank', rel: 'noopener',
            html: '<i class="fas fa-book"></i> ' + escapeHtml(source.title || source.id || '资料来源')
        }));
    });
    const thbUrl = safeHttpUrl(artist.thbWikiUrl)
        || (entry ? safeHttpUrl(entry.sourceUrl) : null);
    if (thbUrl && !hasThbwiki) {
        links.append($('<a>', {
            class: 'geo-wiki-link', href: thbUrl, target: '_blank', rel: 'noopener',
            html: '<i class="fas fa-book"></i> THBWiki'
        }));
    }
    if (artist.cloudMusicId && !hasNetease) {
        links.append($('<a>', {
            class: 'geo-wiki-link',
            href: 'https://music.163.com/#/artist?id=' + artist.cloudMusicId,
            target: '_blank', rel: 'noopener',
            html: '<i class="fas fa-external-link-alt"></i> 网易云音乐'
        }));
    }

    // 正文：后端已决定 content 来源（okf / artist / none）
    const desc = $('#artistDesc');
    desc.removeClass('geo-wiki-desc--empty');
    const lead = (entry && entry.description && String(entry.description).trim())
        ? '<p class="geo-wiki-lead">' + escapeHtml(String(entry.description)) + '</p>'
        : '';
    const content = artist.content && String(artist.content).trim() ? String(artist.content) : '';

    if (content) {
        let note = '';
        if (artist.contentSource === 'artist') {
            note = entry
                ? '<p class="artist-note">词条正文生成中，以下为艺人简介。</p>'
                : '<p class="artist-note">该艺术家暂无 OKF 词条，以下为艺人简介。</p>';
        }
        desc.html(lead + note + renderMarkdown(content));
    } else {
        desc.addClass('geo-wiki-desc--empty');
        desc.text('暂无词条内容');
    }

    renderTags(entry ? entry.tags : []);

    currentTagId = artist.tagId || null;
    albumPage = 1;
    loadDetailAlbums(1);
    renumberSections();
}

// 互联标签：优先回链 OKF 词条，其次本站艺术家 / 旧标签词条
function renderTags(tags) {
    const section = $('#artistTagsSection');
    const container = $('#artistTagChips');
    container.empty();

    if (!Array.isArray(tags) || tags.length === 0) {
        section.hide();
        return;
    }

    let shown = 0;
    tags.forEach(function(item) {
        const tag = typeof item === 'string' ? { tag: item, type: 'style' } : item;
        if (!tag || !tag.tag) return;
        const label = escapeHtml(tag.tag);
        shown++;

        if (tag.artistId) {
            container.append($('<a>', {
                class: 'geo-wiki-tag geo-wiki-tag--artist',
                href: 'artist-showcase.html?id=' + encodeURIComponent(tag.artistId),
                title: '查看艺术家 ' + tag.tag,
                html: '<i class="fas fa-user-astronaut"></i> ' + label
            }));
        } else {
            container.append($('<span>', {
                class: 'geo-wiki-tag geo-wiki-tag--style',
                html: '<i class="fas fa-tag"></i> ' + label
            }));
        }
    });

    if (shown === 0) {
        section.hide();
    } else {
        section.show();
    }
}

// 相关专辑（服务端分页，按 m_artist.tag_id 查）
function loadDetailAlbums(page) {
    const container = $('#artistAlbums');
    container.html('<div class="artist-albums-loading"><i class="fas fa-spinner fa-spin"></i> 加载专辑...</div>');

    if (!currentTagId) {
        container.html('<div class="artist-albums-empty">暂无专辑</div>');
        return;
    }

    $.ajax({
        url: 'api/artists/' + encodeURIComponent(currentTagId) + '/albums',
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
            container.html('<div class="artist-albums-empty">加载失败</div>');
        }
    });
}

function renderDetailAlbums(albums) {
    const container = $('#artistAlbums');
    if (!albums || albums.length === 0) {
        container.html('<div class="artist-albums-empty">暂无专辑</div>');
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

let pendingAlbumScroll = false;

function renderDetailAlbumPagination() {
    const pagination = document.getElementById('artistAlbumsPagination');
    if (!pagination) return;

    TongrenluPagination.render(pagination, {
        page: albumPage,
        totalPages: albumTotalPages,
        total: albumTotal,
        unit: '张',
        scroll: pendingAlbumScroll,
        scrollTarget: '#artistAlbums',
        onPage: function(page) {
            pendingAlbumScroll = true;
            loadDetailAlbums(page);
        }
    });

    pendingAlbumScroll = false;
}

function initDetailEvents() {
    $('#artistBackBtn').on('click', function() {
        if (history.state && history.state.view === 'detail') {
            history.back();
        } else {
            showList();
        }
    });
}

// 标签节隐藏后重排 01/02/03，避免断号
function renumberSections() {
    let index = 1;
    $('#artistDetailContent .geo-wiki-section').each(function() {
        if ($(this).is(':hidden')) return;
        $(this).find('.geo-wiki-section__index').text(index < 10 ? '0' + index : String(index));
        index++;
    });
}
