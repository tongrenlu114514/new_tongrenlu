/**
 * 播放控制模块
 * 负责音频播放核心功能
 */

// 播放指定索引的曲目
function playTrack(trackIndex) {
    console.log('执行playTrack函数，曲目索引:', trackIndex);
    if (!window.currentMusicData || !window.currentMusicData.tracks || trackIndex < 0 || trackIndex >= window.currentMusicData.tracks.length) {
        console.error('无效的曲目索引或专辑数据');
        return;
    }

    // 更新当前曲目索引
    window.currentTrackIndex = trackIndex;
    const track = window.currentMusicData.tracks[trackIndex];

    console.log('播放曲目:', track);

    // 更新播放列表激活状态
    updatePlaylistActive(window.currentTrackIndex);

    // 显示加载状态
    showLoadingStatus('正在加载音频...');

    // 更新当前曲目信息
    updateCurrentTrackInfo(track, window.currentMusicData, false);

    // 加载音频URL
    loadTrackUrl(track.id || track.trackId, track, window.audioPlayer)
        .then(audioData => {
            console.log('音频URL加载成功:', audioData.url);

            // 设置音频源
            window.audioPlayer.src = audioData.url;

            // 加载歌词
            if (audioData.lyric) {
                loadLyrics(audioData.lyric);
            } else {
                loadLyrics(track.id || track.trackId);
            }

            // 尝试播放
            return window.audioPlayer.play();
        })
        .then(() => {
            console.log('播放开始');
            window.isPlaying = true;

            // 隐藏播放覆盖层
            hidePlayOverlay();

            // 更新播放按钮
            updatePlayButton(true);

            // 更新当前曲目信息为播放状态
            updateCurrentTrackInfo(track, window.currentMusicData, true);

            // 触发播放开始事件
            $(document).trigger('trackPlay', [track, trackIndex]);

            // 如果当前是歌词模式，确保歌词容器滚动到顶部
            if (window.isLyricMode) {
                const $lyricsContainer = $('#lyricsContainer');
                if ($lyricsContainer.length > 0) {
                    $lyricsContainer.scrollTop(0);
                }
            }
        })
        .catch(error => {
            console.error('播放失败:', error);

            // 显示错误
            //showError('播放失败: ' + (error.message || '未知错误'));

            // 更新播放按钮
            updatePlayButton(false);

            // 触发播放错误事件
            $(document).trigger('trackError', [track, trackIndex, error]);
        });
}

// 加载并播放曲目（兼容旧版调用）
function playTrackWithData(trackIndex, musicData, player) {
    window.currentMusicData = musicData;
    window.audioPlayer = player;
    playTrack(trackIndex);
}

// 停止播放
function stopTrack() {
    if (window.audioPlayer) {
        window.audioPlayer.pause();
        window.audioPlayer.currentTime = 0;
        window.isPlaying = false;
        updatePlayButton(false);
    }
}

// 导出函数（如果使用ES6模块）
if (typeof module !== 'undefined' && module.exports) {
    module.exports = {
        playTrack,
        playTrackWithData,
        stopTrack
    };
}

