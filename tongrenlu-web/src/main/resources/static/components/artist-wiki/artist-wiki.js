// 艺术家 Wiki 页面逻辑 - 列表 + 条目详情（URL 参数路由）

// ===== 列表状态 =====
let listPage = 1;
let listKeyword = '';
let listLoading = false;
let listHasMore = true;
const LIST_PAGE_SIZE = 30;

// ===== 专辑分页状态 =====
let allAlbums = [];
let albumPage = 1;
const ALBUM_PAGE_SIZE = 20;

$(function() {
    init();
    initMotion();
});

function init() {
    const params = new URLSearchParams(window.location.search);
    const name = params.get('name');
    const id = params.get('id');

    if (name || id) {
        showDetail(id ? { id: id } : { name: name }, false);
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
    // 阅读进度条
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

    // 滚动揭示：武装 [data-rv] 元素并观察
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

// 详情视图入场动画：每次显示时重触发
function playDetailEnter() {
    const detail = document.getElementById('wikiDetailView');
    if (!detail) return;
    detail.classList.remove('is-entering');
    // 强制重排以重启动画
    void detail.offsetWidth;
    detail.classList.add('is-entering');
}

// ===== 路由 =====

function showList(pushState = true) {
    $('#wikiDetailView').hide();
    $('#wikiListView').show();
    window.scrollTo(0, 0);

    if (pushState) {
        history.pushState({ view: 'list' }, '', 'artist-wiki.html');
    }

    // 首次进入列表时加载
    if ($('#wikiGrid').children().length === 0 && !listLoading) {
        listPage = 1;
        listHasMore = true;
        loadWikiArtists(1, false);
    }
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
        ? 'artist-wiki.html?id=' + encodeURIComponent(param.id)
        : 'artist-wiki.html?name=' + encodeURIComponent(param.name);
    if (pushState) {
        history.pushState({ view: 'detail', param: param }, '', detailUrl);
    }

    loadArtistDetail(param);
}

function initHistory() {
    window.addEventListener('popstate', function(e) {
        const state = e.state;
        if (state && state.view === 'detail' && state.param) {
            showDetail(state.param, false);
        } else {
            showList(false);
        }
    });
}

// ===== 列表视图 =====

function loadWikiArtists(page = 1, append = false) {
    if (listLoading) return;
    listLoading = true;

    if (!append) {
        $('#wikiLoading').show();
        $('#wikiGrid').hide();
        $('#wikiEmpty').hide();
    }

    $.ajax({
        url: 'api/music/artists',
        method: 'GET',
        data: {
            keyword: listKeyword || null,
            page: page,
            limit: LIST_PAGE_SIZE
        },
        dataType: 'json',
        success: function(data) {
            const artists = data.records || [];
            const total = data.total || 0;
            const totalPages = data.totalPages || 1;

            $('#navArtistCount').text(total);
            $('#wikiIndexCount').text(total);

            listHasMore = page < totalPages;
            listPage = page;

            if (append) {
                appendWikiCards(artists);
            } else {
                renderWikiCards(artists);
            }

            $('#wikiLoading').hide();
            $('#wikiGrid').show();

            if (artists.length === 0 && page === 1) {
                $('#wikiEmpty').show();
            }

            listLoading = false;
        },
        error: function(xhr, status, error) {
            console.error('加载艺术家失败:', error);
            $('#wikiLoading').hide();
            $('#wikiEmpty').show();
            listLoading = false;
        }
    });
}

function renderWikiCards(artists) {
    const grid = $('#wikiGrid');
    grid.empty();

    if (!artists || artists.length === 0) {
        $('#wikiEmpty').show();
        return;
    }

    $('#wikiEmpty').hide();
    appendWikiCards(artists);
}

function appendWikiCards(artists) {
    const grid = $('#wikiGrid');
    const startDelayIndex = grid.children().length % 12;
    artists.forEach(function(artist, i) {
        const card = createWikiCard(artist);
        // 批内 stagger（mod 12 防止无限滚动时延迟过长）
        card[0].style.setProperty('--i', (startDelayIndex + i) % 12);
        grid.append(card);
    });
}

function createWikiCard(artist) {
    const card = $('<div>', {
        class: 'wiki-card',
        'data-artist-name': artist.name,
        'data-artist-id': artist.id,
        tabindex: 0,
        role: 'link',
        'aria-label': '查看 ' + artist.name + ' 的 Wiki 条目'
    });

    // 封面区域
    const cover = $('<div>', { class: 'wiki-card__cover' });

    const picUrl = pickField(artist, 'cloud_music_pic_url', 'cloudMusicPicUrl');
    if (picUrl) {
        const optimizedUrl = typeof getOptimizedImageUrl === 'function'
            ? getOptimizedImageUrl(picUrl, 200, 200)
            : picUrl;
        const img = $('<img>', {
            class: 'wiki-card__image',
            src: optimizedUrl,
            alt: artist.name,
            loading: 'lazy',
            onerror: 'this.parentElement.innerHTML = \'<div class="wiki-card__placeholder"><i class="fas fa-user"></i></div>\''
        });
        cover.append(img);
    } else {
        cover.append($('<div>', {
            class: 'wiki-card__placeholder',
            html: '<i class="fas fa-user"></i>'
        }));
    }

    // 专辑数量角标
    const albumCount = pickField(artist, 'album_count', 'albumCount');
    if (albumCount) {
        cover.append($('<span>', {
            class: 'wiki-card__badge',
            text: albumCount + '张'
        }));
    }

    // 信息区域
    const info = $('<div>', { class: 'wiki-card__info' });
    info.append($('<h3>', {
        class: 'wiki-card__name',
        text: artist.name,
        title: artist.name
    }));
    if (albumCount) {
        info.append($('<div>', {
            class: 'wiki-card__count',
            text: albumCount + ' 张专辑'
        }));
    }

    card.append(cover, info);

    // 点击进入条目详情
    card.on('click', function() {
        showDetail({ name: artist.name, id: artist.id });
    });

    // 键盘可达：Enter / Space 触发
    card.on('keydown', function(e) {
        if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault();
            showDetail({ name: artist.name, id: artist.id });
        }
    });

    return card;
}

// 搜索（防抖）
function initSearch() {
    const searchInput = $('#wikiSearchInput');
    const searchClear = $('#wikiSearchClear');
    let searchTimer = null;

    searchInput.on('input', function() {
        const keyword = $(this).val().trim();

        if (keyword.length > 0) {
            searchClear.addClass('visible');
        } else {
            searchClear.removeClass('visible');
        }

        if (searchTimer) {
            clearTimeout(searchTimer);
        }

        searchTimer = setTimeout(function() {
            listKeyword = keyword;
            listPage = 1;
            listHasMore = true;
            loadWikiArtists(1, false);
        }, 300);
    });

    searchClear.on('click', function() {
        searchInput.val('');
        listKeyword = '';
        $(this).removeClass('visible');
        listPage = 1;
        listHasMore = true;
        loadWikiArtists(1, false);
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
            loadWikiArtists(listPage + 1, true);
        }
    });
}

// ===== 详情视图 =====

function loadArtistDetail(param) {
    const requestData = param.id ? { id: param.id } : { name: param.name };

    $.ajax({
        url: 'api/music/artist-detail',
        method: 'GET',
        data: requestData,
        dataType: 'json',
        success: function(artist) {
            renderArtistDetail(artist);
        },
        error: function(xhr, status, error) {
            console.warn('artist-detail 接口不可用，降级为列表数据:', error);
            loadArtistDetailFallback(param);
        }
    });
}

// 降级：用列表接口的关键词搜索补全基本信息（无简介）
function loadArtistDetailFallback(param) {
    const keyword = param.name || '';
    $.ajax({
        url: 'api/music/artists',
        method: 'GET',
        data: { keyword: keyword || null, page: 1, limit: 1 },
        dataType: 'json',
        success: function(data) {
            const records = data.records || [];
            if (records.length > 0) {
                renderArtistDetail(records[0]);
            } else {
                $('#wikiDetailLoading').hide();
                $('#wikiDetailError').show();
            }
        },
        error: function(xhr, status, error) {
            console.error('降级加载艺术家失败:', error);
            $('#wikiDetailLoading').hide();
            $('#wikiDetailError').show();
        }
    });
}

function renderArtistDetail(artist) {
    $('#wikiDetailLoading').hide();
    $('#wikiDetailError').hide();
    $('#wikiDetailContent').show();

    const name = artist.name || '--';
    const picUrl = pickField(artist, 'cloudMusicPicUrl', 'cloud_music_pic_url');
    const albumCount = pickField(artist, 'albumCount', 'album_count') || 0;
    const cloudMusicId = pickField(artist, 'cloudMusicId', 'cloud_music_id');
    const wikiContent = pickField(artist, 'wikiContent', 'wiki_content');
    const wikiTags = artist.wikiTags || [];
    const thbWikiUrl = pickField(artist, 'thbWikiUrl', 'thb_wiki_url');

    // 头部：头像
    const avatar = $('#wikiHeroAvatar');
    avatar.empty();
    if (picUrl) {
        const optimizedUrl = typeof getOptimizedImageUrl === 'function'
            ? getOptimizedImageUrl(picUrl, 300, 300)
            : picUrl;
        avatar.append($('<img>', {
            src: optimizedUrl,
            alt: name,
            onerror: 'this.parentElement.innerHTML = \'<div class="wiki-card__placeholder"><i class="fas fa-user"></i></div>\''
        }));
    } else {
        avatar.append($('<div>', {
            class: 'wiki-card__placeholder',
            html: '<i class="fas fa-user"></i>'
        }));
    }

    // 头部：名称 + 统计
    $('#wikiHeroName').text(name);
    const stats = $('#wikiHeroStats');
    stats.empty();
    stats.append($('<span>', {
        class: 'geo-wiki-stat geo-wiki-stat--accent',
        html: '<i class="fas fa-compact-disc"></i> ' + albumCount + ' 张专辑'
    }));

    // 外部链接
    const links = $('#wikiHeroLinks');
    links.empty();
    if (cloudMusicId) {
        links.append($('<a>', {
            class: 'geo-wiki-link',
            href: 'https://music.163.com/#/artist?id=' + cloudMusicId,
            target: '_blank',
            rel: 'noopener',
            html: '<i class="fas fa-external-link-alt"></i> 网易云音乐'
        }));
    }
    if (thbWikiUrl) {
        links.append($('<a>', {
            class: 'geo-wiki-link',
            href: thbWikiUrl,
            target: '_blank',
            rel: 'noopener',
            html: '<i class="fas fa-book"></i> THBWiki'
        }));
    }

    // 简介：生成内容优先（接口已回落到网易云简介）
    const desc = $('#wikiDesc');
    desc.removeClass('geo-wiki-desc--empty');
    if (wikiContent && wikiContent.trim()) {
        desc.html(renderMarkdown(wikiContent));
    } else {
        desc.addClass('geo-wiki-desc--empty');
        desc.text('暂无简介');
    }

    // 互联标签
    renderWikiTags(wikiTags);

    // 专辑列表
    albumPage = 1;
    allAlbums = [];
    loadDetailAlbums(name);
}

// 加载条目的专辑列表
function loadDetailAlbums(artistName) {
    const container = $('#wikiAlbums');
    container.html('<div style="text-align:center; padding:2rem; color:var(--geo-text-muted);"><i class="fas fa-spinner fa-spin"></i> 加载专辑...</div>');
    $('#wikiAlbumsPagination').hide();

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
            renderDetailAlbums();
            renderDetailAlbumPagination();
        },
        error: function(xhr, status, error) {
            console.error('加载专辑失败:', error);
            container.html('<div style="text-align:center; padding:2rem; color:var(--geo-text-muted);">加载失败</div>');
        }
    });
}

function renderDetailAlbums() {
    const container = $('#wikiAlbums');

    if (!allAlbums || allAlbums.length === 0) {
        container.html('<div style="text-align:center; padding:2rem; color:var(--geo-text-muted);">暂无专辑</div>');
        $('#wikiAlbumsPagination').hide();
        return;
    }

    const startIndex = (albumPage - 1) * ALBUM_PAGE_SIZE;
    const endIndex = Math.min(startIndex + ALBUM_PAGE_SIZE, allAlbums.length);
    const pageAlbums = allAlbums.slice(startIndex, endIndex);

    container.empty();
    pageAlbums.forEach(function(album, i) {
        const item = $('<div>', {
            class: 'wiki-album-item',
            'data-album-id': album.id,
            tabindex: 0,
            role: 'button',
            'aria-label': '播放专辑 ' + album.title
        });
        // 入场 stagger
        item[0].style.setProperty('--i', i % ALBUM_PAGE_SIZE);

        const cover = $('<div>', { class: 'wiki-album-item__cover' });
        if (album.cloudMusicPicUrl) {
            const optimizedUrl = typeof getOptimizedImageUrl === 'function'
                ? getOptimizedImageUrl(album.cloudMusicPicUrl, 150, 150)
                : album.cloudMusicPicUrl;
            cover.append($('<img>', {
                src: optimizedUrl,
                alt: album.title,
                loading: 'lazy'
            }));
        }

        const title = $('<div>', {
            class: 'wiki-album-item__title',
            text: album.title,
            title: album.title
        });

        item.append(cover, title);

        // 点击跳转到 Folia 播放器
        item.on('click', function() {
            openFoliaPlayer(album.id, { newTab: false });
        });

        // 键盘可达
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
    const totalPages = Math.ceil(allAlbums.length / ALBUM_PAGE_SIZE);
    const pagination = $('#wikiAlbumsPagination');

    if (totalPages <= 1) {
        pagination.hide();
        return;
    }

    pagination.show();

    let html = '<span class="wiki-albums-pagination__info">共 ' + allAlbums.length + ' 张</span>';

    // 上一页
    if (albumPage > 1) {
        html += '<button class="wiki-albums-pagination__btn" data-page="' + (albumPage - 1) + '"><i class="fas fa-chevron-left"></i></button>';
    } else {
        html += '<button class="wiki-albums-pagination__btn" disabled><i class="fas fa-chevron-left"></i></button>';
    }

    // 页码
    const maxVisible = 5;
    let startPage = Math.max(1, albumPage - Math.floor(maxVisible / 2));
    let endPage = Math.min(totalPages, startPage + maxVisible - 1);

    if (endPage - startPage < maxVisible - 1) {
        startPage = Math.max(1, endPage - maxVisible + 1);
    }

    if (startPage > 1) {
        html += '<button class="wiki-albums-pagination__btn" data-page="1">1</button>';
        if (startPage > 2) {
            html += '<span class="wiki-albums-pagination__ellipsis">...</span>';
        }
    }

    for (let i = startPage; i <= endPage; i++) {
        if (i === albumPage) {
            html += '<button class="wiki-albums-pagination__btn active">' + i + '</button>';
        } else {
            html += '<button class="wiki-albums-pagination__btn" data-page="' + i + '">' + i + '</button>';
        }
    }

    if (endPage < totalPages) {
        if (endPage < totalPages - 1) {
            html += '<span class="wiki-albums-pagination__ellipsis">...</span>';
        }
        html += '<button class="wiki-albums-pagination__btn" data-page="' + totalPages + '">' + totalPages + '</button>';
    }

    // 下一页
    if (albumPage < totalPages) {
        html += '<button class="wiki-albums-pagination__btn" data-page="' + (albumPage + 1) + '"><i class="fas fa-chevron-right"></i></button>';
    } else {
        html += '<button class="wiki-albums-pagination__btn" disabled><i class="fas fa-chevron-right"></i></button>';
    }

    pagination.html(html);

    pagination.find('.wiki-albums-pagination__btn[data-page]').on('click', function() {
        const page = parseInt($(this).data('page'));
        if (page && page !== albumPage) {
            albumPage = page;
            renderDetailAlbums();
            renderDetailAlbumPagination();
            // 滚动到专辑区
            $('#wikiAlbums')[0].scrollIntoView({ behavior: 'smooth', block: 'start' });
        }
    });
}

// 返回按钮
function initDetailEvents() {
    $('#wikiBackBtn').on('click', function() {
        if (history.state && history.state.view === 'detail') {
            history.back();
        } else {
            showList();
        }
    });
}

// 渲染互联标签：type=artist 渲染为跳转链接，style 为纯 chip
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
        if (!tag || !tag.tag) {
            return;
        }

        if (tag.type === 'artist') {
            container.append($('<a>', {
                class: 'geo-wiki-tag geo-wiki-tag--artist',
                href: 'artist-wiki.html?name=' + encodeURIComponent(tag.tag),
                title: '查看 ' + tag.tag + ' 的 Wiki 条目',
                html: '<i class="fas fa-user-astronaut"></i> ' + escapeHtml(tag.tag)
            }));
        } else {
            container.append($('<span>', {
                class: 'geo-wiki-tag geo-wiki-tag--style',
                html: '<i class="fas fa-tag"></i> ' + escapeHtml(tag.tag)
            }));
        }
    });

    section.show();
}

function escapeHtml(s) {
    return $('<div>').text(String(s)).html();
}

// ===== 工具函数 =====

// 字段兼容：详情接口返回 camelCase（bean），列表接口返回 snake_case（mapper Map）
function pickField(obj) {
    for (let i = 1; i < arguments.length; i++) {
        const key = arguments[i];
        if (obj && obj[key] !== undefined && obj[key] !== null && obj[key] !== '') {
            return obj[key];
        }
    }
    return null;
}

// 极简 markdown 渲染：标题 / 粗体 / 列表 / 段落（先转义防 XSS）
function renderMarkdown(text) {
    const escaped = $('<div>').text(text).html();

    const lines = escaped.split(/\r?\n/);
    let html = '';
    let inList = false;
    let paragraph = [];

    const flushParagraph = function() {
        if (paragraph.length > 0) {
            html += '<p>' + paragraph.join('<br>') + '</p>';
            paragraph = [];
        }
    };

    const closeList = function() {
        if (inList) {
            html += '</ul>';
            inList = false;
        }
    };

    // 剥离开头的 "# 简介"（区块标题已承担）
    if (lines.length > 0 && /^#\s*简介\s*$/.test(lines[0].trim())) {
        lines.shift();
    }

    lines.forEach(function(rawLine) {
        const line = rawLine.trim();

        if (line === '') {
            flushParagraph();
            closeList();
            return;
        }

        // 标题
        const h3 = line.match(/^###?\s+(.*)$/);
        const h4 = line.match(/^####\s+(.*)$/);
        if (h4) {
            flushParagraph();
            closeList();
            html += '<h4>' + inline(h4[1]) + '</h4>';
            return;
        }
        if (h3) {
            flushParagraph();
            closeList();
            html += '<h3>' + inline(h3[1]) + '</h3>';
            return;
        }

        // 无序列表
        const li = line.match(/^[-*]\s+(.*)$/);
        if (li) {
            flushParagraph();
            if (!inList) {
                html += '<ul>';
                inList = true;
            }
            html += '<li>' + inline(li[1]) + '</li>';
            return;
        }

        // 普通文本行
        closeList();
        paragraph.push(inline(line));
    });

    flushParagraph();
    closeList();

    return html;

    // 行内格式：**粗体**、*斜体*、`代码`
    function inline(s) {
        return s
            .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
            .replace(/\*([^*]+)\*/g, '<em>$1</em>')
            .replace(/`([^`]+)`/g, '<code>$1</code>');
    }
}
