// 音乐库相关功能
// 打开专辑详情模态框
function openAlbumModal() {
    document.getElementById('albumModal').style.display = 'flex';
}

// 关闭专辑详情模态框
function closeAlbumModal() {
    document.getElementById('albumModal').style.display = 'none';
}

// 更新专辑详情模态框内容
async function updateAlbumModal(albumId) {
    try {
        // 显示加载状态
        showLoadingState();

        // 调用后端接口获取专辑详情
        const response = await fetch(`/api/music/detail?albumId=${albumId}`);

        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }

        const albumDetail = await response.json();

        // 更新标题
        document.querySelector('.album-title').textContent = albumDetail.title || '未知专辑';

        // 更新艺术家
        document.querySelector('.album-artist').textContent = albumDetail.artist || '未知艺术家';

        // 更新元信息
        const metaElements = document.querySelectorAll('.album-meta span');
        if (metaElements.length >= 3) {
            const publishDate = albumDetail.publishDate ? new Date(albumDetail.publishDate) : null;
            metaElements[0].textContent = publishDate ? publishDate.getFullYear() + '年' : '未知';
            metaElements[2].textContent = albumDetail.tracks && albumDetail.tracks.length > 0 ?
                `${albumDetail.tracks.length}首曲目` : '10首曲目';
        }

        // 更新描述
        const descriptionElement = document.querySelector('.album-description');
        if (descriptionElement) {
            descriptionElement.textContent = albumDetail.description || '暂无专辑描述';
        }

        // 更新专辑封面 - 使用本地缓存和缩略图优化
        const albumArtElement = document.querySelector('.album-art');
        if (albumArtElement) {
            if (albumDetail.cloudMusicPicUrl) {
                // 使用缓存机制加载图片
                loadImageWithCache(albumArtElement, albumDetail.cloudMusicPicUrl, 300, 300);
            } else {
                // 如果没有封面图片，显示默认图标
                if (!albumArtElement.querySelector('.fallback-content')) {
                    albumArtElement.innerHTML = '<div class="fallback-content">🎵</div>';
                }
            }
        }

        // 更新曲目列表
        const tracksContainer = document.querySelector('.tracks');
        if (tracksContainer) {
            let tracksHtml = '';

            if (albumDetail.tracks && albumDetail.tracks.length > 0) {
                // 使用真实的曲目列表
                albumDetail.tracks.forEach((track, index) => {
                    const trackNumber = (index + 1).toString().padStart(2, '0');
                    const duration = track.duration || '0:00';
                    const trackTitle = track.name || `曲目 ${index + 1}`;

                    // 检查是否有音乐URL，如果没有则显示无法播放的提示
                    const hasMusicUrl = track.url || track.mp3Url || track.musicUrl || track.fileUrl || track.cloudMusicUrl;

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

            tracksContainer.innerHTML = tracksHtml;
        }

        // 更新错误报告按钮的albumId属性
        const errorButton = document.querySelector('.report-error-btn');
        if (errorButton) {
            errorButton.setAttribute('data-album-id', albumId);
            // 重置按钮状态
            errorButton.disabled = false;
            errorButton.innerHTML = '<i class="fas fa-flag"></i> 报告错误';
            errorButton.classList.remove('loading', 'success', 'error');
        }

        // 隐藏加载状态
        hideLoadingState();

        // 重新绑定播放按钮事件
        document.querySelectorAll('.track-play-btn').forEach(button => {
            button.addEventListener('click', function(e) {
                e.stopPropagation(); // 阻止事件冒泡

                // 如果按钮被禁用，不执行任何操作
                if (this.disabled) {
                    return;
                }

                const icon = this.querySelector('i');
                if (icon.classList.contains('fa-play')) {
                    // 播放当前曲目
                    icon.classList.remove('fa-play');
                    icon.classList.add('fa-pause');

                    // 获取当前曲目索引
                    const track = this.closest('.track');
                    if (track) {
                        const trackIndex = Array.from(track.parentNode.children).indexOf(track);
                        const albumArt = document.querySelector('.album-art').closest('.modal').querySelector('.album-art').parentNode;
                        const albumId = albumArt.getAttribute('data-album-id');

                        // 获取专辑详情并播放指定曲目
                        if (albumId) {
                            fetch(`/api/music/detail?albumId=${albumId}`)
                                .then(response => response.json())
                                .then(albumDetail => {
                                    playMusic(albumDetail, trackIndex); // 播放指定曲目
                                })
                                .catch(error => {
                                    console.error('获取专辑详情失败:', error);
                                });
                        }
                    }
                } else {
                    icon.classList.remove('fa-pause');
                    icon.classList.add('fa-play');
                    pauseMusic();
                }
            });
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
    const albumTitle = document.querySelector('.album-title');
    const albumArtElement = document.querySelector('.album-art');
    const tracksContainer = document.querySelector('.tracks');

    if (albumTitle) {
        albumTitle.textContent = '加载中...';
    }

    if (albumArtElement) {
        const fallbackContent = albumArtElement.querySelector('.fallback-content');
        if (fallbackContent) {
            fallbackContent.textContent = '加载中...';
        } else {
            albumArtElement.innerHTML = '<div class="fallback-content">加载中...</div>';
        }
    }

    if (tracksContainer) {
        tracksContainer.innerHTML = '<li class="track"><span class="track-title">正在加载曲目列表...</span></li>';
    }
}

// 隐藏加载状态
function hideLoadingState() {
    // 这个函数会由数据更新时自动处理
}

// 显示错误状态
function showErrorState(message) {
    // 关闭模态框（如果需要）
    const modal = document.getElementById('albumModal');
    if (modal) {
        modal.style.display = 'none';
    }

    // 显示错误消息
    alert(message);
}

// 点击模态框外部关闭
window.onclick = function(event) {
    const modal = document.getElementById('albumModal');
    if (event.target === modal) {
        modal.style.display = 'none';
    }
};

// 页面加载完成后初始化音乐数据
document.addEventListener('DOMContentLoaded', function() {
    // 初始化时加载所有音乐数据
    searchMusic('');
});

// 搜索功能
document.querySelector('.search-button').addEventListener('click', function() {
    const searchTerm = document.querySelector('.search-input').value;
    if (searchTerm.trim() !== '') {
        searchMusic(searchTerm);
    }
});

// 音乐搜索函数
function searchMusic(keyword, page = 1) {
    // 显示加载状态
    const musicGrid = document.querySelector('.music-grid');
    musicGrid.innerHTML = '<div class="loading">搜索中...</div>';

    // 构建查询参数
    const params = new URLSearchParams();
    params.append('keyword', keyword);
    params.append('pageNumber', page.toString());
    params.append('pageSize', '16');

    // 发送请求到后端API
    fetch(`/api/music/search?${params.toString()}`)
        .then(response => {
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            return response.json();
        })
        .then(data => {
            // 渲染搜索结果
            renderSearchResults(data);
            // 渲染分页控件
            renderPagination(data, keyword);
        })
        .catch(error => {
            console.error('搜索出错:', error);
            musicGrid.innerHTML = '<div class="error">搜索出错，请稍后重试</div>';
        });
}

// 渲染搜索结果
function renderSearchResults(data) {
    const musicGrid = document.querySelector('.music-grid');

    if (!data.records || data.records.length === 0) {
        musicGrid.innerHTML = '<div class="no-results">未找到相关音乐</div>';
        return;
    }

    let html = '';
    data.records.forEach((music, index) => {
        html += `
        <div class="music-card" data-index="${index}" data-title="${music.title || '未知专辑'}" data-description="${music.description || '暂无描述'}" data-access-count="${music.accessCount || 0}" data-cover-url="${music.cloudMusicPicUrl || ''}" data-album-id="${music.id || ''}">
            <div class="album-cover" data-original-url="${music.cloudMusicPicUrl || ''}">
                ${music.cloudMusicPicUrl ? '' : '<div class="fallback-content">🎵</div>'}
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

    musicGrid.innerHTML = html;

    // 重新绑定播放按钮事件
    document.querySelectorAll('.play-button').forEach(button => {
        button.addEventListener('click', function(e) {
            e.stopPropagation(); // 阻止事件冒泡，避免触发卡片点击事件

            const icon = this.querySelector('i');
            if (icon.classList.contains('fa-play')) {
                // 播放专辑第一首音乐
                icon.classList.remove('fa-play');
                icon.classList.add('fa-pause');

                // 获取专辑信息并播放
                const card = this.closest('.music-card');
                if (card) {
                    const albumId = card.getAttribute('data-album-id');
                    if (albumId) {
                        // 获取专辑详情并播放
                        fetch(`/api/music/detail?albumId=${albumId}`)
                            .then(response => response.json())
                            .then(albumDetail => {
                                playMusic(albumDetail, 0); // 播放专辑第一首
                            })
                            .catch(error => {
                                console.error('获取专辑详情失败:', error);
                                // 恢复按钮状态
                                icon.classList.remove('fa-pause');
                                icon.classList.add('fa-play');
                            });
                    }
                }
            } else {
                icon.classList.remove('fa-pause');
                icon.classList.add('fa-play');
                pauseMusic();
            }
        });
    });

    // 为音乐卡片添加点击事件，打开模态框
    document.querySelectorAll('.music-card').forEach(card => {
        card.addEventListener('click', function(e) {
            // 如果点击的是播放按钮，则不打开模态框
            if (e.target.closest('.play-button')) {
                return;
            }

            // 获取专辑信息
            const albumId = this.getAttribute('data-album-id');

            if (albumId) {
                // 更新模态框内容
                updateAlbumModal(albumId);

                // 打开模态框
                openAlbumModal();
            } else {
                console.error('未找到专辑ID');
            }
        });
    });
}

// 渲染分页控件
function renderPagination(data, keyword) {
    const paginationContainer = document.querySelector('.pagination');
    if (!paginationContainer) return;

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

    paginationContainer.innerHTML = paginationHtml;

    // 重新绑定分页按钮事件
    document.querySelectorAll('.page-btn').forEach(btn => {
        btn.addEventListener('click', function() {
            const page = this.getAttribute('data-page');
            const keyword = this.getAttribute('data-keyword');
            searchMusic(keyword, page);
        });
    });
}

// 回车搜索
document.querySelector('.search-input').addEventListener('keypress', function(e) {
    if (e.key === 'Enter') {
        document.querySelector('.search-button').click();
    }
});

// 标签切换
const tags = document.querySelectorAll('.tag');
tags.forEach(tag => {
    tag.addEventListener('click', function() {
        tags.forEach(t => t.classList.remove('active'));
        this.classList.add('active');
    });
});

// 排序按钮切换
const sortBtns = document.querySelectorAll('.sort-btn');
sortBtns.forEach(btn => {
    btn.addEventListener('click', function() {
        sortBtns.forEach(b => b.classList.remove('active'));
        this.classList.add('active');
    });
});

// 分页按钮切换
const pageBtns = document.querySelectorAll('.page-btn');
pageBtns.forEach(btn => {
    btn.addEventListener('click', function() {
        if (!this.textContent.includes('...')) {
            pageBtns.forEach(b => b.classList.remove('active'));
            this.classList.add('active');
        }
    });
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

// 修改renderSearchResults函数，在最后添加懒加载触发
// 检查renderSearchResults是否已存在，避免重复声明
if (typeof renderSearchResults === 'function') {
    const originalRenderSearchResults = renderSearchResults;
    renderSearchResults = function(data) {
        originalRenderSearchResults(data);
        triggerLazyLoadAfterSearch();
    };
} else {
    // 如果renderSearchResults不存在，定义一个新的
    function renderSearchResults(data) {
        // 渲染搜索结果的基础逻辑
        const musicGrid = document.querySelector('.music-grid');

        if (!data.records || data.records.length === 0) {
            musicGrid.innerHTML = '<div class="no-results">未找到相关音乐</div>';
            return;
        }

        let html = '';
        data.records.forEach((music, index) => {
            html += `
            <div class="music-card" data-index="${index}" data-title="${music.title || '未知专辑'}" data-description="${music.description || '暂无描述'}" data-access-count="${music.accessCount || 0}" data-cover-url="${music.cloudMusicPicUrl || ''}" data-album-id="${music.id || ''}">
                <div class="album-cover" data-original-url="${music.cloudMusicPicUrl || ''}">
                    ${music.cloudMusicPicUrl ? '' : '<div class="fallback-content">🎵</div>'}
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

        musicGrid.innerHTML = html;

        // 重新绑定播放按钮事件
        document.querySelectorAll('.play-button').forEach(button => {
            button.addEventListener('click', function(e) {
                e.stopPropagation(); // 阻止事件冒泡，避免触发卡片点击事件

                const icon = this.querySelector('i');
                if (icon.classList.contains('fa-play')) {
                    // 播放专辑第一首音乐
                    icon.classList.remove('fa-play');
                    icon.classList.add('fa-pause');

                    // 获取专辑信息并播放
                    const card = this.closest('.music-card');
                    if (card) {
                        const albumId = card.getAttribute('data-album-id');
                        if (albumId) {
                            // 获取专辑详情并播放
                            fetch(`/api/music/detail?albumId=${albumId}`)
                                .then(response => response.json())
                                .then(albumDetail => {
                                    playMusic(albumDetail, 0); // 播放专辑第一首
                                })
                                .catch(error => {
                                    console.error('获取专辑详情失败:', error);
                                    // 恢复按钮状态
                                    icon.classList.remove('fa-pause');
                                    icon.classList.add('fa-play');
                                });
                        }
                    }
                } else {
                    icon.classList.remove('fa-pause');
                    icon.classList.add('fa-play');
                    pauseMusic();
                }
            });
        });

        // 为音乐卡片添加点击事件，打开模态框
        document.querySelectorAll('.music-card').forEach(card => {
            card.addEventListener('click', function(e) {
                // 如果点击的是播放按钮，则不打开模态框
                if (e.target.closest('.play-button')) {
                    return;
                }

                // 获取专辑信息
                const albumId = this.getAttribute('data-album-id');

                if (albumId) {
                    // 更新模态框内容
                    updateAlbumModal(albumId);

                    // 打开模态框
                    openAlbumModal();
                } else {
                    console.error('未找到专辑ID');
                }
            });
        });
        triggerLazyLoadAfterSearch();
    }
}

// 报告专辑错误功能
async function reportAlbumError() {
    const errorButton = document.querySelector('.report-error-btn');

    if (!errorButton) {
        console.error('未找到错误报告按钮');
        return;
    }

    const albumId = errorButton.getAttribute('data-album-id');

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
        errorButton.disabled = true;
        errorButton.innerHTML = '<i class="fas fa-spinner fa-spin"></i> 提交中...';
        errorButton.classList.add('loading');

        // 发送POST请求到后端API
        const response = await fetch(`/api/music/report-error?albumId=${albumId}`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            }
        });

        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }

        const result = await response.json();

        // 显示成功状态
        errorButton.innerHTML = '<i class="fas fa-check"></i> 已报告';
        errorButton.classList.remove('loading');
        errorButton.classList.add('success');

        // 显示成功消息
        alert('错误报告已提交，感谢您的反馈！我们会尽快处理。');

    } catch (error) {
        console.error('报告错误失败:', error);

        // 显示错误状态
        errorButton.innerHTML = '<i class="fas fa-exclamation-triangle"></i> 报告失败';
        errorButton.classList.remove('loading');
        errorButton.classList.add('error');

        // 显示错误消息
        alert('提交失败，请稍后重试。如果问题持续存在，请联系管理员。');

    } finally {
        // 3秒后恢复按钮状态
        setTimeout(() => {
            if (errorButton) {
                errorButton.disabled = false;
                errorButton.innerHTML = '<i class="fas fa-flag"></i> 报告错误';
                errorButton.classList.remove('loading', 'success', 'error');
            }
        }, 3000);
    }
}