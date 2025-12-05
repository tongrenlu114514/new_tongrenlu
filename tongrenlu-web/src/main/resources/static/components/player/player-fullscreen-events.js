/**
 * 全屏播放器事件处理模块
 * 播放器事件监听和处理逻辑
 */

// 播放指定曲目
function handlePlayTrack(index) {
    window.currentTrackIndex = index;
    window.currentLyricIndex = -1;

    playTrack(index);

    // 隐藏覆盖层
    hidePlayOverlay();

    // 加载歌词
    if (window.currentMusicData && window.currentMusicData.tracks[index]) {
        loadLyrics(window.currentMusicData.tracks[index].id || window.currentMusicData.tracks[index].trackId);
    }

    console.log('开始播放:', window.currentMusicData.tracks[index].name);
}

// 播放/暂停切换
function handleTogglePlayPause() {
    if (!window.audioPlayer.src) {
        if (window.currentTrackIndex >= 0 && window.currentMusicData && window.currentMusicData.tracks) {
            handlePlayTrack(window.currentTrackIndex);
        }
        return;
    }

    if (window.isPlaying) {
        window.audioPlayer.pause();
        window.isPlaying = false;
        updatePlayButton(window.isPlaying);
    } else {
        window.audioPlayer.play().then(() => {
            window.isPlaying = true;
            updatePlayButton(window.isPlaying);
        }).catch(error => {
            console.error('播放失败:', error);
            alert('播放失败: ' + error.message);
        });
    }
}

// 播放上一首
function handlePlayPrevious() {
    console.log('点击上一首按钮');
    if (!window.currentMusicData || !window.currentMusicData.tracks) {
        console.warn('无法播放上一首：缺少专辑数据');
        return;
    }

    console.log('调用playPrevious函数');
    playPrevious();
}

// 播放下一首
function handlePlayNext() {
    console.log('点击下一首按钮');
    if (!window.currentMusicData || !window.currentMusicData.tracks) {
        console.warn('无法播放下一首：缺少专辑数据');
        return;
    }

    console.log('调用playNext函数');
    playNext();
}

// 键盘快捷键
function setupKeyboardShortcuts() {
    $(document).keydown(function (e) {
        if (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA') {
            return;
        }

        switch (e.code) {
            case 'Space':
                e.preventDefault();
                handleTogglePlayPause();
                break;
            case 'ArrowLeft':
                e.preventDefault();
                handlePlayPrevious();
                break;
            case 'ArrowRight':
                e.preventDefault();
                handlePlayNext();
                break;
            case 'KeyR':
                e.preventDefault();
                toggleShuffle();
                break;
            case 'KeyL':
                e.preventDefault();
                toggleRepeat();
                break;
        }
    });
}

// 设置音频事件监听
function setupAudioEvents() {
    $(window.audioPlayer).on('timeupdate', function () {
        updateProgress(window.audioPlayer);

        // 歌词同步
        updateLyricsHighlight(window.audioPlayer.currentTime);
    });

    $(window.audioPlayer).on('ended', function () {
        handleTrackEnd();
    });

    $(window.audioPlayer).on('loadstart', function () {
        console.log('开始加载音频...');
    });

    $(window.audioPlayer).on('canplay', function () {
        console.log('音频可以播放');
    });

    $(window.audioPlayer).on('play', function () {
        console.log('音频开始播放');
        window.isPlaying = true;
    });

    $(window.audioPlayer).on('pause', function () {
        console.log('音频暂停');
        window.isPlaying = false;
    });

    $(window.audioPlayer).on('error', function (e) {
        console.error('音频播放错误:', e);
        $('#albumTitle').html('<span style="color: rgba(255,255,255,0.7);">音频加载失败</span>');
        //alert('音频加载失败，请刷新页面重试');
        window.isPlaying = false;
        updatePlayButton(window.isPlaying);
    });
}

// 设置UI事件监听
function setupUIEvents() {
    console.log('开始设置UI事件监听...');

    // UI事件监听
    $(document).on('click', '.playlist-item', function () {
        const index = $(this).data('index');
        handlePlayTrack(index);
    });

    $('#playBtn').click(handleTogglePlayPause);
    console.log('播放按钮事件已绑定');

    $('#prevBtn').click(handlePlayPrevious);
    console.log('上一首按钮事件已绑定');

    $('#nextBtn').click(handlePlayNext);
    console.log('下一首按钮事件已绑定');

    $('#progressBar').click(function (e) {
        if (!window.audioPlayer.duration) return;

        const rect = this.getBoundingClientRect();
        const pos = (e.clientX - rect.left) / rect.width;
        window.audioPlayer.currentTime = pos * window.audioPlayer.duration;
    });
    console.log('进度条事件已绑定');

    $('#volumeSlider').click(function (e) {
        if (!window.audioPlayer.duration) return;

        const rect = this.getBoundingClientRect();
        const pos = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width));
        window.audioPlayer.volume = pos;
        $('#volumeLevel').css('width', pos * 100 + '%');
        updateVolumeIcon(pos);
    });
    console.log('音量控制事件已绑定');

    $('#shuffleBtn').click(function () {
        toggleShuffle();
    });
    console.log('随机播放按钮事件已绑定');

    $('#repeatBtn').click(function () {
        toggleRepeat();
    });
    console.log('循环播放按钮事件已绑定');

    $('#fullscreenBtn').click(toggleFullscreen);
    console.log('全屏按钮事件已绑定');

    $('#albumImage').dblclick(toggleFullscreen);
    console.log('专辑图片双击事件已绑定');

    $('#backBtn').click(function () {
        if (document.referrer) {
            window.location.href = document.referrer;
        } else {
            window.location.href = 'index.html';
        }
    });
    console.log('返回按钮事件已绑定');

    $('#lyricBtn').click(function () {
        toggleLyricsMode();
    });
    console.log('歌词按钮事件已绑定');

    console.log('UI事件监听设置完成');
}

// 导出函数
if (typeof module !== 'undefined' && module.exports) {
    module.exports = {
        handlePlayTrack,
        handleTogglePlayPause,
        handlePlayPrevious,
        handlePlayNext,
        setupKeyboardShortcuts,
        setupAudioEvents,
        setupUIEvents
    };
}