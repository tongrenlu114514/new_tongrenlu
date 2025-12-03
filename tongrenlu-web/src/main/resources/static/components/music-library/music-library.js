// 音乐库相关功能
// 打开专辑详情模态框
function openAlbumModal() {
    $('#albumModal').css('display', 'flex');
}

// 关闭专辑详情模态框
function closeAlbumModal() {
    $('#albumModal').css('display', 'none');
}

// 更新专辑详情模态框内容
async function updateAlbumModal(albumId) {
    try {
        // 显示加载状态
        showLoadingState();

        // 调用后端接口获取专辑详情
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

        // 更新标题
        $('.album-title').text(albumDetail.title || '未知专辑');

        // 更新艺术家
        $('.album-artist').text(albumDetail.artist || '未知艺术家');

        // 更新元信息
        const metaElements = $('.album-meta span');
        if (metaElements.length >= 3) {
            const publishDate = albumDetail.publishDate ? new Date(albumDetail.publishDate) : null;
            metaElements.eq(0).text(publishDate ? publishDate.getFullYear() + '年' : '未知');
            metaElements.eq(2).text(albumDetail.tracks && albumDetail.tracks.length > 0 ?
                `${albumDetail.tracks.length}首曲目` : '10首曲目');
        }

        // 更新描述
        const descriptionElement = $('.album-description');
        if (descriptionElement.length > 0) {
            descriptionElement.text(albumDetail.description || '暂无专辑描述');
        }

        // 更新专辑封面 - 使用本地缓存和缩略图优化
        const albumArtElement = $('.album-art');
        if (albumArtElement.length > 0) {
            // 设置专辑ID属性，供播放按钮使用
            albumArtElement.attr('data-album-id', albumId);

            if (albumDetail.cloudMusicPicUrl) {
                // 使用缓存机制加载图片
                loadImageWithCache(albumArtElement[0], albumDetail.cloudMusicPicUrl, 300, 300);
            } else {
                // 如果没有封面图片，显示默认图标
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
                // 使用真实的曲目列表
                $.each(albumDetail.tracks, (index, track) => {
                    const trackNumber = (index + 1).toString().padStart(2, '0');
                    const duration = track.duration || '0:00';
                    const trackTitle = track.name || `曲目 ${index + 1}`;

                    // 检查是否有音乐URL，如果没有则显示无法播放的提示
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
                // 如果没有曲目数据，显示模拟数据
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

        // 更新错误报告按钮的albumId属性
        const errorButton = $('.report-error-btn');
        if (errorButton.length > 0) {
            errorButton.data('album-id', albumId);
            // 重置按钮状态
            errorButton.prop('disabled', false);
            errorButton.html('<i class="fas fa-flag"></i> 报告错误');
            errorButton.removeClass('loading', 'success', 'error');
        }

        // 隐藏加载状态
        hideLoadingState();

        // 重新绑定播放按钮事件
        $('.track-play-btn').off('click').on('click', function (e) {
            e.stopPropagation(); // 阻止事件冒泡

            // 如果按钮被禁用，不执行任何操作
            if ($(this).prop('disabled')) {
                return;
            }

            const icon = $(this).find('i');
            if (icon.hasClass('fa-play')) {
                // 打开全屏播放器
                const track = $(this).closest('.track');
                if (track.length > 0) {
                    const trackIndex = track.siblings().addBack().index(track);
                    const albumArt = $('.album-art');
                    const albumId = albumArt.attr('data-album-id');

                    // 打开全屏播放器页面
                    if (albumId) {
                        // 构建全屏播放器URL
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

        // 隐藏加载状态
        hideLoadingState();

        // 显示错误状态
        showErrorState('加载专辑详情失败，请稍后重试');
    }
}

// 显示加载状态
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

// 隐藏加载状态
function hideLoadingState() {
    // 这个函数会由数据更新时自动处理
}

// 显示错误状态
function showErrorState(message) {
    // 关闭模态框（如果需要）
    const modal = $('#albumModal');
    if (modal.length > 0) {
        modal.css('display', 'none');
    }

    // 显示错误消息
    alert(message);
}

// 点击模态框外部关闭
$(window).on('click', function (event) {
    const modal = $('#albumModal');
    if (event.target === modal[0]) {
        modal.css('display', 'none');
    }
});

// 页面加载完成后初始化音乐数据
$(function () {
    // 初始化时加载所有音乐数据
    searchMusic('');
});

// 搜索功能
$('.search-button').on('click', function () {
    const searchTerm = $('.search-input').val();
    if (searchTerm.trim() !== '') {
        searchMusic(searchTerm);
    }
});

// 音乐搜索函数
function searchMusic(keyword, page = 1) {
    // 显示加载状态
    const musicGrid = $('.music-grid');
    musicGrid.html('<div class="loading">搜索中...</div>');

    // 构建查询参数
    const params = new URLSearchParams();
    params.append('keyword', keyword);
    params.append('pageNumber', page.toString());
    params.append('pageSize', '16');

    // 发送请求到后端API
    $.ajax({
        url: `api/music/search?${params.toString()}`,
        method: 'GET',
        dataType: 'json',
        success: function (data) {
            // 渲染搜索结果
            renderSearchResults(data);
            // 渲染分页控件
            renderPagination(data, keyword);
        },
        error: function (xhr, status, error) {
            console.error('搜索出错:', error);
            musicGrid.html('<div class="error">搜索出错，请稍后重试</div>');
        }
    });
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

    // 重新绑定播放按钮事件
    $('.play-button').off('click').on('click', function (e) {
        e.stopPropagation(); // 阻止事件冒泡，避免触发卡片点击事件

        const icon = $(this).find('i');
        if (icon.hasClass('fa-play')) {
            // 打开全屏播放器并播放专辑第一首音乐
            const card = $(this).closest('.music-card');
            if (card.length > 0) {
                const albumId = card.data('album-id');
                if (albumId) {
                    // 打开全屏播放器页面
                    const playerUrl = `player.html?album=${albumId}`;
                    window.open(playerUrl, '_blank');
                }
            }
        } else {
            icon.removeClass('fa-pause').addClass('fa-play');
            pauseMusic();
        }
    });

    // 为音乐卡片添加点击事件，打开模态框
    $('.music-card').off('click').on('click', function (e) {
        // 如果点击的是播放按钮，则不打开模态框
        if ($(e.target).closest('.play-button').length > 0) {
            return;
        }

        // 获取专辑信息
        const albumId = $(this).data('album-id');

        if (albumId) {
            // 更新模态框内容
            updateAlbumModal(albumId);

            // 打开模态框
            openAlbumModal();
        } else {
            console.error('未找到专辑ID');
        }
    });

    // 触发懒加载
    triggerLazyLoadAfterSearch();
}

// 渲染分页控件
function renderPagination(data, keyword) {
    const paginationContainer = $('.pagination');
    if (paginationContainer.length === 0) return;

    let paginationHtml = '';

    // 上一页按钮
    if (data.current > 1) {
        paginationHtml += `<button class="page-btn" data-page="${data.current - 1}" data-keyword="${keyword}">‹</button>`;
    }

    // 页码按钮
    const startPage = Math.max(1, data.current - 2);
    const endPage = Math.min(data.pages, data.current + 2);

    for (let i = startPage; i <= endPage; i++) {
        if (i === data.current) {
            paginationHtml += `<button class="page-btn active" data-page="${i}" data-keyword="${keyword}">${i}</button>`;
        } else {
            paginationHtml += `<button class="page-btn" data-page="${i}" data-keyword="${keyword}">${i}</button>`;
        }
    }

    // 下一页按钮
    if (data.current < data.pages) {
        paginationHtml += `<button class="page-btn" data-page="${data.current + 1}" data-keyword="${keyword}">›</button>`;
    }

    paginationContainer.html(paginationHtml);

    // 重新绑定分页按钮事件
    $('.page-btn').off('click').on('click', function () {
        const page = $(this).data('page');
        const keyword = $(this).data('keyword');
        searchMusic(keyword, page);
    });
}

// 回车搜索
$('.search-input').on('keypress', function (e) {
    if (e.key === 'Enter') {
        $('.search-button').click();
    }
});

// 标签切换
$('.tag').on('click', function () {
    $('.tag').removeClass('active');
    $(this).addClass('active');
});

// 排序按钮切换
$('.sort-btn').on('click', function () {
    $('.sort-btn').removeClass('active');
    $(this).addClass('active');
});

// 分页按钮切换
$('.page-btn').on('click', function () {
    if (!$(this).text().includes('...')) {
        $('.page-btn').removeClass('active');
        $(this).addClass('active');
    }
});

// 每次搜索结果更新后触发懒加载
function triggerLazyLoadAfterSearch() {
    setTimeout(() => {
        if (typeof lazyLoadAlbumCovers === 'function') {
            lazyLoadAlbumCovers();
        } else {
            console.warn('lazyLoadAlbumCovers function is not available');
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

    // 显示确认对话框
    const isConfirmed = confirm('确定要报告这个专辑的错误吗？我们会尽快处理。');

    if (!isConfirmed) {
        return; // 用户取消操作
    }

    try {
        // 禁用按钮并显示加载状态
        errorButton.prop('disabled', true);
        errorButton.html('<i class="fas fa-spinner fa-spin"></i> 提交中...');
        errorButton.addClass('loading');

        // 发送POST请求到后端API
        await $.ajax({
            url: `api/music/report-error?albumId=${albumId}`,
            method: 'POST',
            dataType: 'json',
            headers: {
                'Content-Type': 'application/json',
            }
        });

        // 显示成功状态
        errorButton.html('<i class="fas fa-check"></i> 已报告');
        errorButton.removeClass('loading');
        errorButton.addClass('success');

        // 显示成功消息
        alert('错误报告已提交，感谢您的反馈！我们会尽快处理。');

    } catch (error) {
        console.error('报告错误失败:', error);

        // 显示错误状态
        errorButton.html('<i class="fas fa-exclamation-triangle"></i> 报告失败');
        errorButton.removeClass('loading');
        errorButton.addClass('error');

        // 显示错误消息
        alert('提交失败，请稍后重试。如果问题持续存在，请联系管理员。');

    } finally {
        // 3秒后恢复按钮状态
        setTimeout(() => {
            if (errorButton.length > 0) {
                errorButton.prop('disabled', false);
                errorButton.html('<i class="fas fa-flag"></i> 报告错误');
                errorButton.removeClass('loading', 'success', 'error');
            }
        }, 3000);
    }
}