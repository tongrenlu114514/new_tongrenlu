// 播放器相关功能
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
            url: `/api/music/track?id=${trackId}`,
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

// 更新播放器UI
function updatePlayerUI(title, artist, playing) {
    if (title) {
        $('.now-playing-title').text(title);
    }
    if (artist) {
        $('.now-playing-artist').text(artist);
    }
    // 显示或隐藏播放器
    $('.player').css('display', playing ? 'flex' : 'none');
}

// 播放按钮功能
$(function () {
    $('.play-button, .track-play-btn, .control-btn.play-pause').on('click', function (e) {
        e.stopPropagation(); // 防止事件冒泡

        const icon = $(this).find('i');
        const isPlayIcon = icon.hasClass('fa-play');

        // 如果是专辑卡片的播放按钮
        if ($(this).hasClass('play-button')) {
            const card = $(this).closest('.music-card');
            if (card.length) {
                const albumId = card.attr('data-album-id');
                if (albumId) {
                    // 获取专辑详情并播放
                    $.ajax({
                        url: `/api/music/detail?albumId=${albumId}`,
                        method: 'GET',
                        dataType: 'json',
                        success: function (albumDetail) {
                            if (isPlayIcon) {
                                playMusic(albumDetail, 0); // 播放专辑第一首
                            } else {
                                pauseMusic();
                            }
                        },
                        error: function (error) {
                            console.error('获取专辑详情失败:', error);
                        }
                    });
                }
            }
        }
        // 如果是曲目列表的播放按钮
        else if ($(this).hasClass('track-play-btn')) {
            const track = $(this).closest('.track');
            const trackIndex = track.siblings().addBack().index(track);
            const albumId = $('.album-title').closest('.modal').find('.album-art').parent().attr('data-album-id');

            if (albumId) {
                // 获取专辑详情并播放指定曲目
                $.ajax({
                    url: `/api/music/detail?albumId=${albumId}`,
                    method: 'GET',
                    dataType: 'json',
                    success: function (albumDetail) {
                        if (isPlayIcon) {
                            playMusic(albumDetail, trackIndex);
                        } else {
                            pauseMusic();
                        }
                    },
                    error: function (error) {
                        console.error('获取专辑详情失败:', error);
                    }
                });
            }
        }
        // 如果是播放器的播放/暂停按钮
        else if ($(this).hasClass('control-btn') && $(this).hasClass('play-pause')) {
            if (isPlayIcon) {
                // 如果当前没有音乐数据，尝试播放当前显示的音乐
                if (!currentMusicData) {
                    const nowPlayingTitle = $('.now-playing-title').text();
                    if (nowPlayingTitle && nowPlayingTitle !== '未知音乐') {
                        // 这里需要根据当前播放的音乐重新获取数据，简化处理
                        if (confirm('没有当前音乐数据，是否重新播放当前音乐？')) {
                            // 暂时使用当前显示的标题进行示例播放
                            const musicData = {
                                title: nowPlayingTitle,
                                artist: $('.now-playing-artist').text(),
                                id: 'temp'
                            };
                            playMusic(musicData);
                        }
                    }
                } else {
                    // 继续播放
                    audioPlayer.play().then(() => {
                        isPlaying = true;
                        updatePlayerUI(null, null, true);
                        icon.removeClass('fa-play').addClass('fa-pause');
                    }).catch(error => {
                        console.error('播放失败:', error);
                    });
                }
            } else {
                pauseMusic();
            }
        }
    });
});

// 控制按钮事件
$(function () {
    // 上一首按钮
    $('.control-btn:nth-child(2)').on('click', function () {
        if (currentMusicData && currentMusicData.tracks && currentMusicData.tracks.length > 0) {
            // 播放上一首
            currentTrackIndex = (currentTrackIndex - 1 + currentMusicData.tracks.length) % currentMusicData.tracks.length;
            playMusic(null, currentTrackIndex); // 使用当前音乐数据，但切换曲目
        }
    });

    // 下一首按钮
    $('.control-btn:nth-child(4)').on('click', function () {
        if (currentMusicData && currentMusicData.tracks && currentMusicData.tracks.length > 0) {
            // 播放下一首
            currentTrackIndex = (currentTrackIndex + 1) % currentMusicData.tracks.length;
            playMusic(null, currentTrackIndex); // 使用当前音乐数据，但切换曲目
        }
    });
});

// 进度条控制
$(audioPlayer).on('timeupdate', function () {
    const progress = (audioPlayer.currentTime / audioPlayer.duration) * 100;
    $('.progress').css('width', progress + '%');
    // 更新时间显示
    const currentTime = formatTime(audioPlayer.currentTime);
    const duration = formatTime(audioPlayer.duration);
    $('.progress-time').eq(0).text(currentTime);
    $('.progress-time').eq(1).text(duration);
});

// 点击进度条跳转
$(function () {
    $('.progress-bar').on('click', function (e) {
        const rect = this.getBoundingClientRect();
        const pos = (e.clientX - rect.left) / rect.width;
        audioPlayer.currentTime = pos * audioPlayer.duration;
    });
});

// 音量控制
$(function () {
    $('.volume-slider').on('click', function (e) {
        const rect = this.getBoundingClientRect();
        const pos = (e.clientX - rect.left) / rect.width;
        audioPlayer.volume = pos;
        $('.volume-level').css('width', pos * 100 + '%');
    });
});

// 格式化时间
function formatTime(seconds) {
    if (isNaN(seconds)) return '0:00';
    const min = Math.floor(seconds / 60);
    const sec = Math.floor(seconds % 60);
    return min + ':' + sec.toString().padStart(2, '0');
}

// 音频播放结束时自动播放下一首
$(audioPlayer).on('ended', function () {
    if (currentMusicData && currentMusicData.tracks && currentMusicData.tracks.length > 0) {
        // 自动播放下一首
        currentTrackIndex = (currentTrackIndex + 1) % currentMusicData.tracks.length;
        playMusic(null, currentTrackIndex); // 使用当前音乐数据，但切换曲目
    } else {
        // 如果只有一首歌，暂停播放器
        pauseMusic();
    }
});