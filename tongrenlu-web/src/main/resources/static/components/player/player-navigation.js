/**
 * 播放导航控制模块
 * 负责上一首、下一首、播放模式等控制功能
 */

// 播放上一首
function playPrevious() {
    console.log('执行playPrevious函数');
    if (!window.currentMusicData || !window.currentMusicData.tracks) {
        console.warn('playPrevious: 缺少专辑数据');
        return;
    }

    let newIndex;

    if (window.isShuffle) {
        newIndex = Math.floor(Math.random() * window.currentMusicData.tracks.length);
    } else {
        newIndex = window.currentTrackIndex > 0 ? window.currentTrackIndex - 1 : window.currentMusicData.tracks.length - 1;
    }

    console.log('上一首曲目索引:', newIndex);
    playTrack(newIndex);
}

// 播放下一首
function playNext() {
    console.log('执行playNext函数');
    if (!window.currentMusicData || !window.currentMusicData.tracks) {
        console.warn('playNext: 缺少专辑数据');
        return;
    }

    let newIndex;

    if (window.repeatMode === 'one') {
        newIndex = window.currentTrackIndex;
    } else if (window.isShuffle) {
        newIndex = Math.floor(Math.random() * window.currentMusicData.tracks.length);
    } else {
        newIndex = (window.currentTrackIndex + 1) % window.currentMusicData.tracks.length;
    }

    console.log('下一首曲目索引:', newIndex);
    playTrack(newIndex);
}

// 处理曲目结束
function handleTrackEnd() {
    if (!window.currentMusicData || !window.currentMusicData.tracks) return;

    let newIndex;

    if (window.repeatMode === 'one') {
        // 单曲循环
        newIndex = window.currentTrackIndex;
    } else if (window.repeatMode === 'all') {
        // 列表循环
        if (window.isShuffle) {
            newIndex = Math.floor(Math.random() * window.currentMusicData.tracks.length);
        } else {
            newIndex = (window.currentTrackIndex + 1) % window.currentMusicData.tracks.length;
        }
    } else {
        // 不重复
        if (window.isShuffle) {
            newIndex = Math.floor(Math.random() * window.currentMusicData.tracks.length);
        } else if (window.currentTrackIndex < window.currentMusicData.tracks.length - 1) {
            newIndex = window.currentTrackIndex + 1;
        } else {
            // 播放完毕
            window.isPlaying = false;
            updatePlayButton(false);
            return;
        }
    }

    playTrack(newIndex);
}

// 播放/暂停切换
function togglePlayPause() {
    if (!window.audioPlayer.src) {
        // 如果没有音频源，尝试播放第一首
        if (window.currentMusicData && window.currentMusicData.tracks && window.currentMusicData.tracks.length > 0) {
            playTrack(0);
        }
        return;
    }

    if (window.isPlaying) {
        window.audioPlayer.pause();
        window.isPlaying = false;
        updatePlayButton(false);
    } else {
        window.audioPlayer.play().then(() => {
            window.isPlaying = true;
            updatePlayButton(true);
        }).catch(error => {
            console.error('播放失败:', error);
            showError('播放失败: ' + error.message);
        });
    }
}

// 随机播放切换
function toggleShuffle() {
    window.isShuffle = !window.isShuffle;
    $('#shuffleBtn').toggleClass('active', window.isShuffle);
    console.log('随机播放:', window.isShuffle ? '开启' : '关闭');
}

// 重复模式切换
function toggleRepeat() {
    const modes = ['none', 'all', 'one'];
    const currentIndex = modes.indexOf(window.repeatMode);
    window.repeatMode = modes[(currentIndex + 1) % modes.length];

    const $icon = $('#repeatBtn i');
    $icon.removeClass('fa-redo fa-redo-alt');

    if (window.repeatMode === 'one') {
        $icon.addClass('fa-redo-alt');
        $('#repeatBtn').addClass('active');
    } else if (window.repeatMode === 'all') {
        $icon.addClass('fa-redo');
        $('#repeatBtn').addClass('active');
    } else {
        $icon.addClass('fa-redo');
        $('#repeatBtn').removeClass('active');
    }

    console.log('重复模式:', window.repeatMode);
}

// 切换歌词显示模式
function toggleLyricsMode() {
    const $playlistContainer = $('#playlistContainer');
    const $lyricsContainer = $('#lyricsContainer');

    if (window.isLyricMode) {
        // 切换到播放列表模式
        $lyricsContainer.fadeOut(300, function() {
            $playlistContainer.fadeIn(300);
        });
        window.isLyricMode = false;
    } else {
        // 切换到歌词模式
        $playlistContainer.fadeOut(300, function() {
            $lyricsContainer.fadeIn(300);
            // 确保歌词容器滚动到顶部
            $lyricsContainer.scrollTop(0);
        });
        window.isLyricMode = true;
    }
}

// 导出函数（如果使用ES6模块）
if (typeof module !== 'undefined' && module.exports) {
    module.exports = {
        playPrevious,
        playNext,
        handleTrackEnd,
        togglePlayPause,
        toggleShuffle,
        toggleRepeat,
        toggleLyricsMode
    };
}