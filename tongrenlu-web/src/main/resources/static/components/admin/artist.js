$(document).ready(function () {
    let currentPage = 1;
    const pageSize = 30;
    let currentArtist = null;
    let searchKeyword = '';
    let currentAlbumPage = 0;
    const albumPageSize = 30;
    let isLoadingAlbums = false;
    let hasMoreAlbums = true;

    function searchArtists() {
        const keyword = $('#searchInput').val().trim();

        searchKeyword = keyword;
        currentPage = 1;
        loadArtists();
    }

    function loadArtists() {
        const $loading = $('#loading');
        const $artistGrid = $('#artistGrid');
        const $noResults = $('#noResults');
        const $pagination = $('#pagination');

        $loading.show();
        $artistGrid.hide();
        $noResults.hide();
        $pagination.hide();

        $.ajax({
            url: 'api/artist/list',
            method: 'GET',
            data: {
                keyword: searchKeyword || null,
                page: currentPage,
                limit: pageSize
            },
            success: function (response) {
                $loading.hide();

                if (response.success && response.data) {
                    const artists = response.data.records || [];
                    const totalPages = response.data.pages || 1;

                    if (artists.length > 0) {
                        renderArtists(artists);
                        renderPagination(totalPages, currentPage);
                        $artistGrid.show();
                        $pagination.show();
                    } else {
                        $noResults.show();
                    }
                } else {
                    $noResults.show();
                    alert('加载失败: ' + (response.message || '未知错误'));
                }
            },
            error: function (xhr, status, error) {
                $loading.hide();
                $noResults.show();
                alert('加载失败: ' + error);
            }
        });
    }

    function renderArtists(artists) {
        const $artistGrid = $('#artistGrid');

        $artistGrid.empty();

        artists.forEach(artist => {
            const $card = $('<div class="artist-card"></div>');
            $card.data('artist', artist);

            const $pic = $(`<div class="artist-pic album-cover"
                             data-original-url="${artist.cloudMusicPicUrl || ''}"></div>`);

            const $info = $('<div class="artist-info"></div>');
            $info.append(`<div class="artist-name" title="${artist.name}">${artist.name}</div>`);

            if (artist.cloudMusicName) {
                $info.append(`<div class="artist-alias" title="${artist.cloudMusicName}">${artist.cloudMusicName}</div>`);
            }

            $card.append($pic);
            $card.append($info);
            $artistGrid.append($card);
        });

        // 触发懒加载
        setTimeout(lazyLoadAlbumCovers, 100);
    }

    function renderPagination(totalPages, currentPage) {
        const $pagination = $('#pagination');

        if (totalPages <= 1) {
            $pagination.hide();
            return;
        }

        let html = '';

        html += `<span class="page-item ${currentPage === 1 ? 'disabled' : ''}" data-page="${currentPage - 1}">上一页</span>`;

        for (let i = 1; i <= totalPages; i++) {
            if (i === 1 || i === totalPages || (i >= currentPage - 2 && i <= currentPage + 2)) {
                html += `<span class="page-item ${i === currentPage ? 'active' : ''}" data-page="${i}">${i}</span>`;
            } else if (i === currentPage - 3 || i === currentPage + 3) {
                html += '<span class="page-item disabled">...</span>';
            }
        }

        html += `<span class="page-item ${currentPage === totalPages ? 'disabled' : ''}" data-page="${currentPage + 1}">下一页</span>`;

        $pagination.html(html);
    }

    function showArtistDetail(artist) {
        currentArtist = artist;

        // 重置专辑加载状态
        currentAlbumPage = 0;
        hasMoreAlbums = true;
        isLoadingAlbums = false;

        // 使用image-loader加载头像
        if (artist.cloudMusicPicUrl) {
            loadImageWithCache($('#artistPic')[0], artist.cloudMusicPicUrl, 200, 200);
        }
        $('#artistName').text(artist.name);
        $('#modalArtistName').text(artist.name);

        if (artist.alias && artist.alias.length > 0) {
            $('#artistAlias').text('别名: ' + artist.alias.join(', '));
        } else {
            $('#artistAlias').text('');
        }

        $('#albumCount').text(artist.albumSize || 0);
        $('#albumGrid').empty();

        $('#artistModal').addClass('active');

        loadArtistAlbums(artist.id);
    }

    function loadArtistAlbums(artistId, append = false) {
        if (isLoadingAlbums || (!append && !hasMoreAlbums)) {
            return;
        }

        const $albumLoading = $('#albumLoading');
        const $albumGrid = $('#albumGrid');

        isLoadingAlbums = true;
        $albumLoading.show();

        const requestData = {
            id: artistId,
            limit: albumPageSize,
            offset: currentAlbumPage * albumPageSize
        };

        $.ajax({
            url: 'api/artist/albums',
            method: 'POST',
            contentType: 'application/json',
            data: JSON.stringify(requestData),
            success: function (response) {
                $albumLoading.hide();
                isLoadingAlbums = false;

                if (response.success && response.data) {
                    const albums = response.data.albums || [];
                    const more = response.data.more || false;

                    hasMoreAlbums = more;

                    if (albums.length > 0) {
                        renderAlbums(albums, append);
                        $albumGrid.show();

                        currentAlbumPage++;
                    } else if (!append) {
                        $albumGrid.html('<div class="no-results">暂无专辑</div>');
                        $albumGrid.show();
                    }
                } else {
                    if (!append) {
                        $albumGrid.html('<div class="no-results">加载专辑失败: ' + (response.message || '未知错误') + '</div>');
                        $albumGrid.show();
                    }
                    hasMoreAlbums = false;
                }
            },
            error: function (xhr, status, error) {
                $albumLoading.hide();
                isLoadingAlbums = false;

                if (!append) {
                    $albumGrid.html('<div class="no-results">加载专辑失败: ' + error + '</div>');
                    $albumGrid.show();
                }
                hasMoreAlbums = false;
            }
        });
    }

    function renderAlbums(albums, append = false) {
        const $albumGrid = $('#albumGrid');

        if (albums.length === 0 && !append) {
            $albumGrid.html('<div class="no-results">暂无专辑</div>');
            return;
        }

        const $albumContainer = append ? $albumGrid : $([]);
        if (!append) {
            $albumGrid.empty();
        }

        albums.forEach(album => {
            const publishDate = album.publishTime ? new Date(parseInt(album.publishTime)).toLocaleDateString('zh-CN') : '未知';
            const existsInDb = album.existsInDb || false;
            const company = album.company || '暂无发行商';

            const $albumCard = $('<div class="album-card"></div>');

            const $albumPic = $(`<div class="album-pic album-cover"
                                  data-original-url="${album.picUrl || ''}">
                                  <div class="album-description-overlay">
                                      <div class="album-description-text">${company}</div>
                                  </div>
                              </div>`);

            const $albumInfo = $('<div class="album-info"></div>');
            $albumInfo.append(`<div class="album-name" title="${album.name}">${album.name}</div>`);
            $albumInfo.append(`<div class="album-date">${publishDate}</div>`);

            const $albumStatus = $(`<div class="album-status ${existsInDb ? 'saved' : 'unsaved'}"></div>`);
            $albumStatus.data('album', album);
            $albumStatus.text(existsInDb ? '已保存' : '保存到库里');

            $albumCard.append($albumPic);
            $albumCard.append($albumInfo);
            $albumCard.append($albumStatus);

            if (append) {
                $albumContainer.append($albumCard);
            } else {
                $albumGrid.append($albumCard);
            }
        });

        // 触发懒加载
        setTimeout(lazyLoadAlbumCovers, 100);
    }

    function saveAlbum(albumData, $button) {
        $.ajax({
            url: 'api/artist/save-album',
            method: 'POST',
            contentType: 'application/json',
            data: JSON.stringify(albumData),
            success: function (response) {
                if (response.success) {
                    // alert('专辑保存成功！');

                    albumData.existsInDb = true;
                    albumData.dbAlbumId = response.albumId;

                    $button.removeClass('unsaved').addClass('saved').text('已保存');
                    $button.data('album', albumData);
                } else {
                    alert('保存失败: ' + (response.message || '未知错误'));
                }
            },
            error: function (xhr, status, error) {
                alert('保存失败: ' + error);
            }
        });
    }

    $('#searchBtn').on('click', searchArtists);

    $('#searchInput').on('keypress', function (e) {
        if (e.key === 'Enter') {
            searchArtists();
        }
    });

    $('#artistGrid').on('click', '.artist-card', function () {
        const artist = $(this).data('artist');
        showArtistDetail(artist);
    });

    $('#closeModal').on('click', function () {
        $('#artistModal').removeClass('active');
    });

    $('#artistModal').on('click', function (e) {
        if ($(e.target).is('#artistModal')) {
            $('#artistModal').removeClass('active');
        }
    });

    // 监听专辑列表容器的滚动事件，实现懒加载
    $('#albumGrid').on('scroll', function () {
        if (!currentArtist || isLoadingAlbums || !hasMoreAlbums) {
            return;
        }

        const $grid = $(this);
        const scrollTop = $grid.scrollTop();
        const scrollHeight = $grid[0].scrollHeight;
        const clientHeight = $grid[0].clientHeight;

        // 当滚动到底部（距离底部100px时）加载更多
        if (scrollHeight - scrollTop - clientHeight < 100) {
            loadArtistAlbums(currentArtist.id, true);
        }
    });

    $('#albumGrid').on('click', '.album-status.unsaved', function () {
        const album = $(this).data('album');
        saveAlbum(album, $(this));
    });

    $('#pagination').on('click', '.page-item:not(.disabled)', function () {
        const page = $(this).data('page');
        if (page && page > 0) {
            currentPage = page;
            loadArtists();
        }
    });
    // 页面加载时自动加载所有本地歌手
    loadArtists();
});
