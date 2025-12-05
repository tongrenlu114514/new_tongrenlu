/**
 * UI控制模块
 * 负责UI事件处理和覆盖层管理
 */

// 显示播放覆盖层
function showPlayOverlay() {
    const $overlay = $('#playOverlay');
    if ($overlay.length === 0) {
        console.error('播放覆盖层元素未找到');
        return;
    }

    // 绑定点击事件（只绑定一次）
    if ($('#overlayPlayBtn').data('events-bound') !== true) {
        $('#overlayPlayBtn').click(function() {
            hidePlayOverlay();
            // 尝试播放当前曲目
            if (window.currentTrackIndex >= 0 && window.currentMusicData && window.currentMusicData.tracks) {
                playTrack(window.currentTrackIndex);
            }
        });

        // 点击覆盖层也可以关闭
        $('#playOverlay').click(function(e) {
            if (e.target.id === 'playOverlay') {
                hidePlayOverlay();
                if (window.currentTrackIndex >= 0 && window.currentMusicData && window.currentMusicData.tracks) {
                    playTrack(window.currentTrackIndex);
                }
            }
        });

        // 标记事件已绑定
        $('#overlayPlayBtn').data('events-bound', true);
    }

    // 显示覆盖层
    $overlay.addClass('show');
}

// 隐藏播放覆盖层
function hidePlayOverlay() {
    const $overlay = $('#playOverlay');
    if ($overlay.length > 0) {
        $overlay.removeClass('show');
    }

    // 如果当前是歌词模式，确保歌词容器滚动到顶部
    if (window.isLyricMode) {
        const $lyricsContainer = $('#lyricsContainer');
        if ($lyricsContainer.length > 0) {
            $lyricsContainer.scrollTop(0);
        }
    }
}

// 更新播放列表激活状态
function updatePlaylistActive(currentTrackIndex) {
    $('.playlist-item').removeClass('active');
    $(`.playlist-item[data-index="${currentTrackIndex}"]`).addClass('active');
}

// 更新播放按钮
function updatePlayButton(isPlaying) {
    const $playBtn = $('#playBtn');
    const $icon = $playBtn.find('i');

    if (isPlaying) {
        $icon.removeClass('fa-play').addClass('fa-pause');
    } else {
        $icon.removeClass('fa-pause').addClass('fa-play');
    }
}

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

// 更新音量图标
function updateVolumeIcon(volume) {
    const $volumeIcon = $('#volumeIcon');
    $volumeIcon.removeClass('fa-volume-up fa-volume-down fa-volume-off');

    if (volume === 0) {
        $volumeIcon.addClass('fa-volume-off');
    } else if (volume < 0.5) {
        $volumeIcon.addClass('fa-volume-down');
    } else {
        $volumeIcon.addClass('fa-volume-up');
    }
}

// 更新进度条
function updateProgress(audioPlayer) {
    if (audioPlayer.duration) {
        const progress = (audioPlayer.currentTime / audioPlayer.duration) * 100;
        $('#progress').css('width', progress + '%');
        $('#currentTime').text(formatTime(audioPlayer.currentTime));
        $('#totalTime').text(formatTime(audioPlayer.duration));
    }
}

// 格式化时间
function formatTime(seconds) {
    if (isNaN(seconds)) return '0:00';
    const min = Math.floor(seconds / 60);
    const sec = Math.floor(seconds % 60);
    return min + ':' + sec.toString().padStart(2, '0');
}

// 更新专辑信息
function updateAlbumInfo(albumData) {
    $('#albumTitle').text(albumData.title || '未知专辑');
    $('#albumArtist').text(albumData.artist || '未知艺术家');
    $('#albumImage').attr('src', albumData.cloudMusicPicUrl || albumData.image || albumData.coverUrl || 'assets/images/default-album.png');
}

// 更新当前播放信息
function updateCurrentTrackInfo(track, albumData, isPlaying) {
    if (isPlaying) {
        $('#albumTitle').text(track.name || '未命名曲目');
    }
    $('#albumArtist').text(track.artist || albumData.artist || '未知艺术家');
    document.title = `${track.name || '播放中'} - 全屏播放器`;
}

// 导出函数（如果使用ES6模块）
if (typeof module !== 'undefined' && module.exports) {
    module.exports = {
        showPlayOverlay,
        hidePlayOverlay,
        updatePlaylistActive,
        updatePlayButton,
        toggleFullscreen,
        updateVolumeIcon,
        updateProgress,
        formatTime,
        updateAlbumInfo,
        updateCurrentTrackInfo
    };
}
