/**
 * 基础播放器核心模块
 * 用于非全屏页面的音乐播放功能核心
 */

// 播放器变量
const audioPlayer = $('#audioPlayer')[0];
let currentTrackIndex = 0; // 当前播放的曲目索引
let isPlaying = false; // 播放状态
let currentMusicData = null; // 当前播放的音乐数据

// 播放音乐函数
function playMusic(musicData, trackIndex = 0) {
    // 如果传入了音乐数据，更新当前音乐数据
    if (musicData) {
        currentMusicData = musicData;
        currentTrackIndex = trackIndex;
    }

    // 如果没有当前音乐数据，无法播放
    if (!currentMusicData) {
        console.error('没有可播放的音乐数据');
        return;
    }

    // 获取当前曲目的信息
    let trackTitle, trackArtist, trackId;
    if (currentMusicData.tracks && currentMusicData.tracks.length > 0 && currentTrackIndex < currentMusicData.tracks.length) {
        const currentTrack = currentMusicData.tracks[currentTrackIndex];
        trackTitle = currentTrack.name || `曲目 ${currentTrackIndex + 1}`;
        trackArtist = currentMusicData.artist || '未知艺术家';
        // 获取ID用于调用API
        trackId = currentTrack.id;
    } else {
        // 如果没有曲目数据，使用专辑信息
        trackTitle = currentMusicData.title || '未知音乐';
        trackArtist = currentMusicData.artist || '未知艺术家';
        trackId = undefined;
    }

    // 如果有trackId，先调用API获取URL
    if (trackId) {
        // 显示加载状态
        $('.now-playing-title').text(trackTitle + ' (加载中...)');

        $.ajax({
            url: `api/music/track?id=${trackId}`,
            method: 'GET',
            dataType: 'json',
            success: function (data) {
                // 从API响应中提取音乐URL
                let trackUrl = '';
                if (data && data.url) {
                    trackUrl = data.url;
                } else {
                    console.error('API响应中未找到音乐URL');
                    alert('未找到音乐URL');
                    return;
                }

                // 设置音频源并播放
                audioPlayer.src = trackUrl;
                audioPlayer.play().then(() => {
                    isPlaying = true;
                    // 更新播放器UI
                    updatePlayerUI(trackTitle, trackArtist, true);
                    // 更新播放按钮图标
                    $('.play-button, .track-play-btn, .control-btn.play-pause').each(function () {
                        const icon = $(this).find('i');
                        if (icon.length) {
                            icon.removeClass('fa-play').addClass('fa-pause');
                        }
                    });
                }).catch(error => {
                    console.error('播放失败:', error);
                    // 如果通过API获取的URL无法播放，也尝试回退到原始URL
                    if (currentMusicData.tracks && currentMusicData.tracks.length > 0 && currentTrackIndex < currentMusicData.tracks.length) {
                        const currentTrack = currentMusicData.tracks[currentTrackIndex];
                        const fallbackUrl = currentTrack.cloudMusicUrl || currentTrack.url || currentTrack.mp3Url || currentTrack.musicUrl || currentTrack.fileUrl || `http://example.com/music/${currentMusicData.id}/${currentTrackIndex + 1}.mp3`;
                        audioPlayer.src = fallbackUrl;
                        audioPlayer.play().then(() => {
                            isPlaying = true;
                            updatePlayerUI(trackTitle, trackArtist, true);
                            $('.play-button, .track-play-btn, .control-btn.play-pause').each(function () {
                                const icon = $(this).find('i');
                                if (icon.length) {
                                    icon.removeClass('fa-play').addClass('fa-pause');
                                }
                            });
                        }).catch(fallbackError => {
                            console.error('回退播放也失败:', fallbackError);
                            // 恢复播放按钮状态
                            $('.play-button, .track-play-btn, .control-btn.play-pause').each(function () {
                                const icon = $(this).find('i');
                                if (icon.length) {
                                    icon.removeClass('fa-pause').addClass('fa-play');
                                }
                            });
                        });
                    } else {
                        // 恢复播放按钮状态
                        $('.play-button, .track-play-btn, .control-btn.play-pause').each(function () {
                            const icon = $(this).find('i');
                            if (icon.length) {
                                icon.removeClass('fa-pause').addClass('fa-play');
                            }
                        });
                    }
                });
            },
            error: function (error) {
                console.error('获取音乐URL失败:', error);
                // 如果API调用失败，使用回退URL
                let fallbackUrl = '';
                if (currentMusicData.tracks && currentMusicData.tracks.length > 0 && currentTrackIndex < currentMusicData.tracks.length) {
                    const currentTrack = currentMusicData.tracks[currentTrackIndex];
                    fallbackUrl = currentTrack.cloudMusicUrl || currentTrack.url || currentTrack.mp3Url || currentTrack.musicUrl || currentTrack.fileUrl || `http://example.com/music/${currentMusicData.id}/${currentTrackIndex + 1}.mp3`;
                } else {
                    fallbackUrl = currentMusicData.cloudMusicUrl || currentMusicData.url || currentMusicData.mp3Url || currentMusicData.musicUrl || currentMusicData.fileUrl || `http://example.com/music/${currentMusicData.id}.mp3`;
                }

                audioPlayer.src = fallbackUrl;
                audioPlayer.play().then(() => {
                    isPlaying = true;
                    updatePlayerUI(trackTitle, trackArtist, true);
                    $('.play-button, .track-play-btn, .control-btn.play-pause').each(function () {
                        const icon = $(this).find('i');
                        if (icon.length) {
                            icon.removeClass('fa-play').addClass('fa-pause');
                        }
                    });
                }).catch(fallbackError => {
                    console.error('回退播放也失败:', fallbackError);
                    // 恢复播放按钮状态
                    $('.play-button, .track-play-btn, .control-btn.play-pause').each(function () {
                        const icon = $(this).find('i');
                        if (icon.length) {
                            icon.removeClass('fa-pause').addClass('fa-play');
                        }
                    });
                });
            }
        });
    } else {
        // 如果没有trackId，直接使用原始URL
        let trackUrl = '';
        if (currentMusicData.tracks && currentMusicData.tracks.length > 0 && currentTrackIndex < currentMusicData.tracks.length) {
            const currentTrack = currentMusicData.tracks[currentTrackIndex];
            trackUrl = currentTrack.cloudMusicUrl || currentTrack.url || currentTrack.mp3Url || currentTrack.musicUrl || currentTrack.fileUrl || `http://example.com/music/${currentMusicData.id}/${currentTrackIndex + 1}.mp3`;
        } else {
            trackUrl = currentMusicData.cloudMusicUrl || currentMusicData.url || currentMusicData.mp3Url || currentMusicData.musicUrl || currentMusicData.fileUrl || `http://example.com/music/${currentMusicData.id}.mp3`;
        }

        // 设置音频源并播放
        audioPlayer.src = trackUrl;
        audioPlayer.play().then(() => {
            isPlaying = true;
            // 更新播放器UI
            updatePlayerUI(trackTitle, trackArtist, true);
            // 更新播放按钮图标
            $('.play-button, .track-play-btn, .control-btn.play-pause').each(function () {
                const icon = $(this).find('i');
                if (icon.length) {
                    icon.removeClass('fa-play').addClass('fa-pause');
                }
            });
        }).catch(error => {
            console.error('播放失败:', error);
            // 恢复播放按钮状态
            $('.play-button, .track-play-btn, .control-btn.play-pause').each(function () {
                const icon = $(this).find('i');
                if (icon.length) {
                    icon.removeClass('fa-pause').addClass('fa-play');
                }
            });
        });
    }
}

// 暂停音乐
function pauseMusic() {
    audioPlayer.pause();
    isPlaying = false;
    // 更新播放器UI
    updatePlayerUI(null, null, false);
    // 更新播放按钮图标
    $('.play-button, .track-play-btn, .control-btn.play-pause').each(function () {
        const icon = $(this).find('i');
        if (icon.length) {
            icon.removeClass('fa-pause').addClass('fa-play');
        }
    });
}

// 导出函数和变量
if (typeof module !== 'undefined' && module.exports) {
    module.exports = {
        audioPlayer,
        currentTrackIndex,
        isPlaying,
        currentMusicData,
        playMusic,
        pauseMusic
    };
}