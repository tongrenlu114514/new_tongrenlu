// 全屏播放器功能
$(function () {
    console.log('全屏播放器初始化...');

    // 播放器变量
    const audioPlayer = $('#audioPlayer')[0];
    let currentTrackIndex = 0;
    let currentMusicData = null;
    let isPlaying = false;
    let isShuffle = false;
    let repeatMode = 'none'; // none, one, all

    // 获取URL参数
    const urlParams = new URLSearchParams(window.location.search);
    const albumId = urlParams.get('album');

    console.log('Album ID:', albumId);

    // 加载随机专辑数据
    function loadRandomAlbum() {
        // 显示加载状态
        $('#albumTitle').html('<span style="color: rgba(255,255,255,0.7);">加载随机专辑...</span>');

        $.ajax({
            url: 'api/music/random',
            method: 'GET',
            dataType: 'json',
            success: function (albumData) {
                console.log('随机专辑数据加载成功:', albumData);

                if (!albumData || !albumData.tracks || albumData.tracks.length === 0) {
                    $('#albumTitle').text('没有找到专辑');
                    return;
                }

                currentMusicData = albumData;

                // 更新专辑信息
                $('#albumTitle').text(albumData.title || '未知专辑');
                $('#albumArtist').text(albumData.artist || '未知艺术家');
                $('#albumImage').attr('src', albumData.cloudMusicPicUrl || albumData.image || 'assets/images/default-album.png');

                // 生成播放列表
                generatePlaylist(albumData.tracks);

                // 显示播放覆盖层（强制用户交互）
                showPlayOverlay();

                // 播放第一首（将尝试自动播放，但会被浏览器阻止，自动显示覆盖层）
                playTrack(0);
            },
            error: function (error) {
                console.error('加载随机专辑数据失败:', error);
                $('#albumTitle').text('加载随机专辑失败');
            }
        });
    }

    // 加载音乐数据
    function loadMusicData() {
        // 显示加载状态
        $('#albumTitle').html('<span style="color: rgba(255,255,255,0.7);">加载中...</span>');

        // 获取专辑详情
        // 如果是测试模式，使用测试数据
        if (albumId === 'test001') {
            // 模拟测试数据
            const testAlbumData = {
                id: "test001",
                title: "东方红魔乡",
                artist: "上海爱丽丝幻乐团",
                coverUrl: "https://p1.music.126.net/24234234/test-album.jpg",
                tracks: [
                    {
                        id: "track001",
                        name: "梦月仙境",
                        duration: 285,
                        url: "https://example.com/music/dream-moon.mp3"
                    },
                    {
                        id: "track002",
                        name: "红色的迷路少女",
                        duration: 198,
                        url: "https://example.com/music/red-girl.mp3"
                    },
                    {
                        id: "track003",
                        name: "上海红茶馆 ~ Chinese Tea",
                        duration: 245,
                        url: "https://example.com/music/shanghai-tea.mp3"
                    },
                    {
                        id: "track004",
                        name: "明治十七年的上海爱丽丝",
                        duration: 312,
                        url: "https://example.com/music/meiji-shanghai.mp3"
                    }
                ]
            };

            setTimeout(function () {
                console.log('测试专辑数据加载成功:', testAlbumData);

                currentMusicData = testAlbumData;

                // 更新专辑信息
                $('#albumTitle').text(testAlbumData.title || '未知专辑');
                $('#albumArtist').text(testAlbumData.artist || '未知艺术家');
                $('#albumImage').attr('src', testAlbumData.coverUrl || 'https://p1.music.126.net/24234234/default-album.jpg');

                // 生成播放列表
                generatePlaylist(testAlbumData.tracks);

                // 显示播放覆盖层（强制用户交互）
                showPlayOverlay();

                // 播放第一首（将尝试自动播放，但会被浏览器阻止，自动显示覆盖层）
                playTrack(0);
            }, 500);
        } else if (albumId) {
            // 如果有album参数，加载指定专辑
            $.ajax({
                url: `api/music/detail?albumId=${albumId}`,
                method: 'GET',
                dataType: 'json',
                success: function (albumData) {
                    console.log('专辑数据加载成功:', albumData);

                    if (!albumData || !albumData.tracks || albumData.tracks.length === 0) {
                        $('#albumTitle').text('专辑为空');
                        return;
                    }

                    currentMusicData = albumData;

                    // 更新专辑信息
                    $('#albumTitle').text(albumData.title || '未知专辑');
                    $('#albumArtist').text(albumData.artist || '未知艺术家');
                    $('#albumImage').attr('src', albumData.cloudMusicPicUrl || albumData.image || 'assets/images/default-album.png');

                    // 生成播放列表
                    generatePlaylist(albumData.tracks);

                    // 显示播放覆盖层（强制用户交互）
                    showPlayOverlay();

                    // 播放第一首（将尝试自动播放，但会被浏览器阻止，自动显示覆盖层）
                    playTrack(0);
                },
                error: function (error) {
                    console.error('加载专辑数据失败:', error);
                    $('#albumTitle').text('加载失败');
                }
            });
        } else {
            // 如果没有album参数，加载随机专辑
            console.log('未找到专辑参数，加载随机专辑');
            loadRandomAlbum();
        }
    }

    // 生成播放列表
    function generatePlaylist(tracks) {
        const $playlist = $('#playlist');
        $playlist.empty();

        tracks.forEach((track, index) => {
            const $trackItem = $(`
                <li class="playlist-item ${index === 0 ? 'active' : ''}" data-index="${index}">
                    <span class="track-number">${(index + 1).toString().padStart(2, '0')}</span>
                    <div class="track-info">
                        <div class="track-name">
                            <i class="fas fa-music icon"></i>
                            ${track.name || '未命名曲目'}
                        </div>
                        <div class="track-duration">${track.duration ? formatTime(track.duration) : '未知'}</div>
                    </div>
                </li>
            `);

            // 点击播放
            $trackItem.click(function () {
                const index = $(this).data('index');
                playTrack(index);
            });

            $playlist.append($trackItem);
        });

        console.log('播放列表生成完成，共', tracks.length, '首曲目');
    }

    // 播放指定曲目
    function playTrack(index) {
        if (!currentMusicData || !currentMusicData.tracks || index >= currentMusicData.tracks.length) {
            console.error('无效的音乐数据或索引');
            return;
        }

        currentTrackIndex = index;
        const track = currentMusicData.tracks[index];

        if (!track) {
            console.error('曲目不存在');
            return;
        }

        // 获取音频URL
        const trackId = track.id || track.trackId;

        if (!trackId) {
            console.error('曲目ID为空');
            $('#albumTitle').html(`${track.name || '未知曲目'}<span style="color: rgba(255,255,255,0.7);"> (未找到ID)</span>`);
            return;
        }

        // 显示加载状态
        $('#albumTitle').html(`${track.name || '加载中...'}<span style="color: rgba(255,255,255,0.7);"> (加载中)</span>`);

        // 调用API获取音频URL
        // 如果是测试模式，直接使用测试数据中的URL
        if (albumId === 'test001') {
            // 测试模式直接使用track中的URL
            const testUrl = track.url;
            if (testUrl) {
                setTimeout(function () {
                    audioPlayer.src = testUrl;
                    attemptPlayAudio(track);
                }, 300);
            } else {
                console.error('测试数据中未找到音频URL');
                $('#albumTitle').html(`${track.name || '未知曲目'}<span style="color: rgba(255,255,255,0.7);"> (未找到资源)</span>`);
                alert('未找到音频资源');
            }
        } else {
            $.ajax({
                url: `api/music/track?id=${trackId}`,
                method: 'GET',
                dataType: 'json',
                success: function (data) {
                    console.log('音频数据:', data);

                    if (data && data.url) {
                        // 获取成功，播放音乐
                        audioPlayer.src = data.url;
                        attemptPlayAudio(track);
                    } else {
                        console.error('未找到音频URL');
                        $('#albumTitle').html(`${track.name || '未知曲目'}<span style="color: rgba(255,255,255,0.7);"> (未找到资源)</span>`);
                        alert('未找到音频资源');
                    }
                },
                error: function (error) {
                    console.error('获取音频URL失败:', error);

                    // 尝试使用备用URL
                    const fallbackUrl = track.cloudMusicUrl || track.url || track.mp3Url || track.musicUrl || track.fileUrl;

                    if (fallbackUrl) {
                        console.log('使用备用URL:', fallbackUrl);
                        audioPlayer.src = fallbackUrl;
                        attemptPlayAudio(track);
                    } else {
                        $('#albumTitle').html(`${track.name || '未知曲目'}<span style="color: rgba(255,255,255,0.7);"> (获取失败)</span>`);
                        alert('获取音频资源失败');
                    }
                }
            });
        }
    }

    // 显示播放覆盖层
    function showPlayOverlay() {
        const $overlay = $('#playOverlay');
        if ($overlay.length === 0) {
            // 创建覆盖层 HTML
            const overlayHtml = `
                <div class="play-overlay" id="playOverlay">
                    <div class="overlay-content">
                        <i class="fas fa-play-circle play-overlay-icon"></i>
                        <h2>点击播放您的音乐</h2>
                        <p>请与页面交互以开始播放</p>
                        <button class="play-overlay-btn" id="overlayPlayBtn">开始播放</button>
                    </div>
                </div>
            `;
            $('body').append(overlayHtml);

            // 绑定点击事件
            $('#overlayPlayBtn').click(function() {
                hidePlayOverlay();
                // 尝试播放当前曲目
                if (currentTrackIndex >= 0 && currentMusicData && currentMusicData.tracks) {
                    playTrack(currentTrackIndex);
                }
            });

            // 点击覆盖层也可以关闭（可选）
            $('#playOverlay').click(function(e) {
                if (e.target.id === 'playOverlay') {
                    hidePlayOverlay();
                    if (currentTrackIndex >= 0 && currentMusicData && currentMusicData.tracks) {
                        playTrack(currentTrackIndex);
                    }
                }
            });
        }

        $('#playOverlay').fadeIn(300);
    }

    // 隐藏播放覆盖层
    function hidePlayOverlay() {
        $('#playOverlay').fadeOut(300);
    }

    // 尝试播放音频并处理自动播放限制
    function attemptPlayAudio(track) {
        audioPlayer.play().then(() => {
            isPlaying = true;
            $('#albumTitle').text(track.name || '未命名曲目');
            $('#albumArtist').text(track.artist || currentMusicData.artist || '未知艺术家');
            updatePlayButton();
            updatePlaylistActive();
            document.title = `${track.name || '播放中'} - 全屏播放器`;
            // 隐藏覆盖层（如果显示）
            hidePlayOverlay();

            console.log('开始播放:', track.name);
        }).catch(error => {
            console.error('播放失败:', error);
            isPlaying = false;
            updatePlayButton();

            // 处理浏览器自动播放限制
            if (error.name === 'NotAllowedError') {
                $('#albumTitle').html(`${track.name || '未命名曲目'}<span class="hint">点击底部播放按钮开始播放</span>`);
                // 显示覆盖层和大按钮
                showPlayOverlay();
                console.log('需要用户交互才能播放音频');
            } else {
                $('#albumTitle').text(track.name || '播放失败');
                alert('播放失败: ' + error.message);
            }
        });
    }

    // 更新播放列表激活状态
    function updatePlaylistActive() {
        $('.playlist-item').removeClass('active');
        $(`.playlist-item[data-index="${currentTrackIndex}"]`).addClass('active');
    }

    // 更新播放按钮
    function updatePlayButton() {
        const $playBtn = $('#playBtn');
        const $icon = $playBtn.find('i');

        if (isPlaying) {
            $icon.removeClass('fa-play').addClass('fa-pause');
        } else {
            $icon.removeClass('fa-pause').addClass('fa-play');
        }
    }

    // 播放/暂停
    $('#playBtn').click(function () {
        if (!audioPlayer.src) {
            if (currentTrackIndex >= 0 && currentMusicData && currentMusicData.tracks) {
                playTrack(currentTrackIndex);
            }
            return;
        }

        if (isPlaying) {
            audioPlayer.pause();
            isPlaying = false;
        } else {
            audioPlayer.play().catch(error => {
                console.error('播放失败:', error);
                alert('播放失败: ' + error.message);
            });
            isPlaying = true;
        }

        updatePlayButton();
    });

    // 上一首
    $('#prevBtn').click(function () {
        if (!currentMusicData || !currentMusicData.tracks) return;

        if (isShuffle) {
            // 随机播放模式
            const randomIndex = Math.floor(Math.random() * currentMusicData.tracks.length);
            playTrack(randomIndex);
        } else {
            currentTrackIndex = currentTrackIndex > 0 ? currentTrackIndex - 1 : currentMusicData.tracks.length - 1;
            playTrack(currentTrackIndex);
        }
    });

    // 下一首
    $('#nextBtn').click(function () {
        if (!currentMusicData || !currentMusicData.tracks) return;

        if (isShuffle) {
            // 随机播放模式
            const randomIndex = Math.floor(Math.random() * currentMusicData.tracks.length);
            playTrack(randomIndex);
        } else {
            currentTrackIndex = (currentTrackIndex + 1) % currentMusicData.tracks.length;
            playTrack(currentTrackIndex);
        }
    });

    // 进度条更新
    $(audioPlayer).on('timeupdate', function () {
        if (audioPlayer.duration) {
            const progress = (audioPlayer.currentTime / audioPlayer.duration) * 100;
            $('#progress').css('width', progress + '%');
            $('#currentTime').text(formatTime(audioPlayer.currentTime));
            $('#totalTime').text(formatTime(audioPlayer.duration));
        }
    });

    // 点击进度条
    $('#progressBar').click(function (e) {
        if (!audioPlayer.duration) return;

        const rect = this.getBoundingClientRect();
        const pos = (e.clientX - rect.left) / rect.width;
        audioPlayer.currentTime = pos * audioPlayer.duration;
    });

    // 音量控制
    $('#volumeSlider').click(function (e) {
        if (!audioPlayer.duration) return;

        const rect = this.getBoundingClientRect();
        const pos = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width));
        audioPlayer.volume = pos;
        $('#volumeLevel').css('width', pos * 100 + '%');

        // 更新音量图标
        const $volumeIcon = $('#volumeIcon');
        $volumeIcon.removeClass('fa-volume-up fa-volume-down fa-volume-off');

        if (pos === 0) {
            $volumeIcon.addClass('fa-volume-off');
        } else if (pos < 0.5) {
            $volumeIcon.addClass('fa-volume-down');
        } else {
            $volumeIcon.addClass('fa-volume-up');
        }
    });

    // 音频播放结束
    $(audioPlayer).on('ended', function () {
        if (!currentMusicData || !currentMusicData.tracks) return;

        // 根据循环模式播放下一首
        if (repeatMode === 'one') {
            // 单曲循环
            playTrack(currentTrackIndex);
        } else if (repeatMode === 'all') {
            // 列表循环
            if (isShuffle) {
                const randomIndex = Math.floor(Math.random() * currentMusicData.tracks.length);
                playTrack(randomIndex);
            } else {
                currentTrackIndex = (currentTrackIndex + 1) % currentMusicData.tracks.length;
                playTrack(currentTrackIndex);
            }
        } else {
            // 不循环，播放完就停止
            if (currentTrackIndex < currentMusicData.tracks.length - 1) {
                if (isShuffle) {
                    const randomIndex = Math.floor(Math.random() * currentMusicData.tracks.length);
                    playTrack(randomIndex);
                } else {
                    currentTrackIndex = (currentTrackIndex + 1) % currentMusicData.tracks.length;
                    playTrack(currentTrackIndex);
                }
            } else {
                // 最后一首播放完毕
                isPlaying = false;
                updatePlayButton();
            }
        }
    });

    // 随机播放按钮
    $('#shuffleBtn').click(function () {
        isShuffle = !isShuffle;
        const $icon = $(this).find('i');

        if (isShuffle) {
            $icon.addClass('active');
            $icon.removeClass('fa-random').addClass('fa-random active-icon');
        } else {
            $icon.removeClass('active');
            $icon.removeClass('fa-random active-icon').addClass('fa-random');
        }

        console.log('随机播放:', isShuffle ? '开启' : '关闭');
    });

    // 循环播放按钮
    $('#repeatBtn').click(function () {
        const $icon = $(this).find('i');
        $icon.removeClass('fa-redo fa-redo active-icon');

        if (repeatMode === 'none') {
            repeatMode = 'all';
            $icon.addClass('fa-redo active-icon');
            console.log('循环模式: 列表循环');
        } else if (repeatMode === 'all') {
            repeatMode = 'one';
            $icon.addClass('fa-redo active-icon one');
            console.log('循环模式: 单曲循环');
        } else {
            repeatMode = 'none';
            $icon.addClass('fa-redo');
            console.log('循环模式: 不循环');
        }
    });

    // 返回按钮
    $('#backBtn').click(function () {
        if (document.referrer) {
            window.location.href = document.referrer;
        } else {
            window.location.href = 'index.html';
        }
    });

    // 键盘快捷键
    $(document).keydown(function (e) {
        // 只有在输入框没有焦点时才响应快捷键
        if (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA') {
            return;
        }

        if (e.code === 'Space') {
            e.preventDefault();
            $('#playBtn').click();
        } else if (e.code === 'ArrowLeft') {
            e.preventDefault();
            $('#prevBtn').click();
        } else if (e.code === 'ArrowRight') {
            e.preventDefault();
            $('#nextBtn').click();
        } else if (e.code === 'KeyR') {
            // R键切换随机播放
            e.preventDefault();
            $('#shuffleBtn').click();
        } else if (e.code === 'KeyL') {
            // L键切换循环模式
            e.preventDefault();
            $('#repeatBtn').click();
        }
    });

    // 全屏切换
    function toggleFullscreen() {
        if (!document.fullscreenElement) {
            document.documentElement.requestFullscreen().catch(err => {
                console.error('无法进入全屏模式:', err);
            });
        } else {
            document.exitFullscreen();
        }
    }

    // 全屏按钮
    $('#fullscreenBtn').click(function () {
        toggleFullscreen();
    });

    // 双击专辑图片也可以进入全屏
    $('#albumImage').dblclick(function () {
        toggleFullscreen();
    });

    // 格式化时间
    function formatTime(seconds) {
        if (isNaN(seconds)) return '0:00';
        const min = Math.floor(seconds / 60);
        const sec = Math.floor(seconds % 60);
        return min + ':' + sec.toString().padStart(2, '0');
    }

    // 检测音频播放器错误
    $(audioPlayer).on('error', function (e) {
        console.error('音频播放错误:', e);
        $('#albumTitle').html('<span style="color: rgba(255,255,255,0.7);">音频加载失败</span>');
        alert('音频加载失败，请刷新页面重试');
        isPlaying = false;
        updatePlayButton();
    });

    // 初始化
    loadMusicData();

    // 音频加载开始事件
    $(audioPlayer).on('loadstart', function () {
        console.log('开始加载音频...');
    });

    // 音频可以播放事件
    $(audioPlayer).on('canplay', function () {
        console.log('音频可以播放');
    });

    // 音频播放事件
    $(audioPlayer).on('play', function () {
        console.log('音频开始播放');
    });

    // 音频暂停事件
    $(audioPlayer).on('pause', function () {
        console.log('音频暂停');
    });
});