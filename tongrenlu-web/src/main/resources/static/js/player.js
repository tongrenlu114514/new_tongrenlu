// 播放器相关功能
// 播放器变量
const audioPlayer = document.getElementById('audioPlayer');
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
        document.querySelector('.now-playing-title').textContent = trackTitle + ' (加载中...)';
        
        fetch(`/api/music/track?id=${trackId}`)
            .then(response => response.json())
            .then(data => {
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
                    document.querySelectorAll('.play-button, .track-play-btn, .control-btn.play-pause').forEach(btn => {
                        const icon = btn.querySelector('i');
                        if (icon) {
                            icon.classList.remove('fa-play');
                            icon.classList.add('fa-pause');
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
                            document.querySelectorAll('.play-button, .track-play-btn, .control-btn.play-pause').forEach(btn => {
                                const icon = btn.querySelector('i');
                                if (icon) {
                                    icon.classList.remove('fa-play');
                                    icon.classList.add('fa-pause');
                                }
                            });
                        }).catch(fallbackError => {
                            console.error('回退播放也失败:', fallbackError);
                            // 恢复播放按钮状态
                            document.querySelectorAll('.play-button, .track-play-btn, .control-btn.play-pause').forEach(btn => {
                                const icon = btn.querySelector('i');
                                if (icon) {
                                    icon.classList.remove('fa-pause');
                                    icon.classList.add('fa-play');
                                }
                            });
                        });
                    } else {
                        // 恢复播放按钮状态
                        document.querySelectorAll('.play-button, .track-play-btn, .control-btn.play-pause').forEach(btn => {
                            const icon = btn.querySelector('i');
                            if (icon) {
                                icon.classList.remove('fa-pause');
                                icon.classList.add('fa-play');
                            }
                        });
                    }
                });
            })
            .catch(error => {
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
                    document.querySelectorAll('.play-button, .track-play-btn, .control-btn.play-pause').forEach(btn => {
                        const icon = btn.querySelector('i');
                        if (icon) {
                            icon.classList.remove('fa-play');
                            icon.classList.add('fa-pause');
                        }
                    });
                }).catch(fallbackError => {
                    console.error('回退播放也失败:', fallbackError);
                    // 恢复播放按钮状态
                    document.querySelectorAll('.play-button, .track-play-btn, .control-btn.play-pause').forEach(btn => {
                        const icon = btn.querySelector('i');
                        if (icon) {
                            icon.classList.remove('fa-pause');
                            icon.classList.add('fa-play');
                        }
                    });
                });
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
            document.querySelectorAll('.play-button, .track-play-btn, .control-btn.play-pause').forEach(btn => {
                const icon = btn.querySelector('i');
                if (icon) {
                    icon.classList.remove('fa-play');
                    icon.classList.add('fa-pause');
                }
            });
        }).catch(error => {
            console.error('播放失败:', error);
            // 恢复播放按钮状态
            document.querySelectorAll('.play-button, .track-play-btn, .control-btn.play-pause').forEach(btn => {
                const icon = btn.querySelector('i');
                if (icon) {
                    icon.classList.remove('fa-pause');
                    icon.classList.add('fa-play');
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
    document.querySelectorAll('.play-button, .track-play-btn, .control-btn.play-pause').forEach(btn => {
        const icon = btn.querySelector('i');
        if (icon) {
            icon.classList.remove('fa-pause');
            icon.classList.add('fa-play');
        }
    });
}

// 更新播放器UI
function updatePlayerUI(title, artist, playing) {
    if (title) {
        document.querySelector('.now-playing-title').textContent = title;
    }
    if (artist) {
        document.querySelector('.now-playing-artist').textContent = artist;
    }
    // 显示或隐藏播放器
    document.querySelector('.player').style.display = playing ? 'flex' : 'none';
}

// 播放按钮功能
const playButtons = document.querySelectorAll('.play-button, .track-play-btn, .control-btn.play-pause');
playButtons.forEach(button => {
    button.addEventListener('click', function(e) {
        e.stopPropagation(); // 防止事件冒泡
        
        const icon = this.querySelector('i');
        const isPlayIcon = icon.classList.contains('fa-play');

        // 如果是专辑卡片的播放按钮
        if (this.classList.contains('play-button')) {
            const card = this.closest('.music-card');
            if (card) {
                const albumId = card.getAttribute('data-album-id');
                if (albumId) {
                    // 获取专辑详情并播放
                    fetch(`/api/music/detail?albumId=${albumId}`)
                        .then(response => response.json())
                        .then(albumDetail => {
                            if (isPlayIcon) {
                                playMusic(albumDetail, 0); // 播放专辑第一首
                            } else {
                                pauseMusic();
                            }
                        })
                        .catch(error => {
                            console.error('获取专辑详情失败:', error);
                        });
                }
            }
        }
        // 如果是曲目列表的播放按钮
        else if (this.classList.contains('track-play-btn')) {
            const track = this.closest('.track');
            const trackIndex = Array.from(track.parentNode.children).indexOf(track);
            const albumId = document.querySelector('.album-title').closest('.modal').querySelector('.album-art').parentNode.getAttribute('data-album-id');
            
            if (albumId) {
                // 获取专辑详情并播放指定曲目
                fetch(`/api/music/detail?albumId=${albumId}`)
                    .then(response => response.json())
                    .then(albumDetail => {
                        if (isPlayIcon) {
                            playMusic(albumDetail, trackIndex);
                        } else {
                            pauseMusic();
                        }
                    })
                    .catch(error => {
                        console.error('获取专辑详情失败:', error);
                    });
            }
        }
        // 如果是播放器的播放/暂停按钮
        else if (this.classList.contains('control-btn') && this.classList.contains('play-pause')) {
            if (isPlayIcon) {
                // 如果当前没有音乐数据，尝试播放当前显示的音乐
                if (!currentMusicData) {
                    const nowPlayingTitle = document.querySelector('.now-playing-title').textContent;
                    if (nowPlayingTitle && nowPlayingTitle !== '未知音乐') {
                        // 这里需要根据当前播放的音乐重新获取数据，简化处理
                        if (confirm('没有当前音乐数据，是否重新播放当前音乐？')) {
                            // 暂时使用当前显示的标题进行示例播放
                            const musicData = {
                                title: nowPlayingTitle,
                                artist: document.querySelector('.now-playing-artist').textContent,
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
                        icon.classList.remove('fa-play');
                        icon.classList.add('fa-pause');
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

// 上一首按钮
document.querySelector('.control-btn:nth-child(2)').addEventListener('click', function() {
    if (currentMusicData && currentMusicData.tracks && currentMusicData.tracks.length > 0) {
        // 播放上一首
        currentTrackIndex = (currentTrackIndex - 1 + currentMusicData.tracks.length) % currentMusicData.tracks.length;
        playMusic(null, currentTrackIndex); // 使用当前音乐数据，但切换曲目
    }
});

// 下一首按钮
document.querySelector('.control-btn:nth-child(4)').addEventListener('click', function() {
    if (currentMusicData && currentMusicData.tracks && currentMusicData.tracks.length > 0) {
        // 播放下一首
        currentTrackIndex = (currentTrackIndex + 1) % currentMusicData.tracks.length;
        playMusic(null, currentTrackIndex); // 使用当前音乐数据，但切换曲目
    }
});

// 进度条控制
audioPlayer.addEventListener('timeupdate', function() {
    const progress = (audioPlayer.currentTime / audioPlayer.duration) * 100;
    document.querySelector('.progress').style.width = progress + '%';
    // 更新时间显示
    const currentTime = formatTime(audioPlayer.currentTime);
    const duration = formatTime(audioPlayer.duration);
    document.querySelectorAll('.progress-time')[0].textContent = currentTime;
    document.querySelectorAll('.progress-time')[1].textContent = duration;
});

// 点击进度条跳转
document.querySelector('.progress-bar').addEventListener('click', function(e) {
    const rect = this.getBoundingClientRect();
    const pos = (e.clientX - rect.left) / rect.width;
    audioPlayer.currentTime = pos * audioPlayer.duration;
});

// 音量控制
document.querySelector('.volume-slider').addEventListener('click', function(e) {
    const rect = this.getBoundingClientRect();
    const pos = (e.clientX - rect.left) / rect.width;
    audioPlayer.volume = pos;
    document.querySelector('.volume-level').style.width = pos * 100 + '%';
});

// 格式化时间
function formatTime(seconds) {
    if (isNaN(seconds)) return '0:00';
    const min = Math.floor(seconds / 60);
    const sec = Math.floor(seconds % 60);
    return min + ':' + sec.toString().padStart(2, '0');
}

// 音频播放结束时自动播放下一首
audioPlayer.addEventListener('ended', function() {
    if (currentMusicData && currentMusicData.tracks && currentMusicData.tracks.length > 0) {
        // 自动播放下一首
        currentTrackIndex = (currentTrackIndex + 1) % currentMusicData.tracks.length;
        playMusic(null, currentTrackIndex); // 使用当前音乐数据，但切换曲目
    } else {
        // 如果只有一首歌，暂停播放器
        pauseMusic();
    }
});