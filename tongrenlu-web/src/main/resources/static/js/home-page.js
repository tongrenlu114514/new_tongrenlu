// 首页相关功能
// 添加一些简单的动画效果
document.addEventListener('DOMContentLoaded', function() {
    // 淡入动画
    const fadeElements = document.querySelectorAll('.music-card, .artist-card, .section-title');
    fadeElements.forEach((el, index) => {
        el.style.opacity = '0';
        el.style.transform = 'translateY(20px)';
        setTimeout(() => {
            el.style.transition = 'opacity 0.6s ease, transform 0.6s ease';
            el.style.opacity = '1';
            el.style.transform = 'translateY(0)';
        }, 200 * index);
    });
    
    // "开始探索"按钮点击事件
    const startExploreBtn = document.getElementById('start-explore-btn');
    if (startExploreBtn) {
        startExploreBtn.addEventListener('click', function(e) {
            e.preventDefault();
            switchToPage('music');
        });
    }
    
    // 其他CTA按钮点击事件
    const ctaButtons = document.querySelectorAll('.cta-button');
    ctaButtons.forEach(button => {
        // 跳过"开始探索"按钮，因为已经单独处理
        if (button.id !== 'start-explore-btn') {
            button.addEventListener('click', function(e) {
                e.preventDefault();
                // 其他按钮保持原有的行为（如果需要的话）
            });
        }
    });
});

// 专辑轮播功能
document.addEventListener('DOMContentLoaded', function() {
    const carouselContainer = document.querySelector('.carousel-container');
    const prevBtn = document.getElementById('prevBtn');
    const nextBtn = document.getElementById('nextBtn');
    const indicators = document.querySelectorAll('.indicator');
    let currentIndex = 0;
    const totalItems = 3;
    
    // 更新轮播位置
    function updateCarousel() {
        carouselContainer.style.transform = `translateX(-${currentIndex * 100}%)`;
        
        // 更新指示器
        indicators.forEach((indicator, index) => {
            if (index === currentIndex) {
                indicator.classList.add('active');
            } else {
                indicator.classList.remove('active');
            }
        });
    }
    
    // 下一张
    if (nextBtn) {
        nextBtn.addEventListener('click', () => {
            currentIndex = (currentIndex + 1) % totalItems;
            updateCarousel();
        });
    }
    
    // 上一张
    if (prevBtn) {
        prevBtn.addEventListener('click', () => {
            currentIndex = (currentIndex - 1 + totalItems) % totalItems;
            updateCarousel();
        });
    }
    
    // 指示器点击
    indicators.forEach(indicator => {
        indicator.addEventListener('click', (e) => {
            currentIndex = parseInt(e.target.getAttribute('data-index'));
            updateCarousel();
        });
    });
    
    // 自动播放
    setInterval(() => {
        currentIndex = (currentIndex + 1) % totalItems;
        updateCarousel();
    }, 5000);
});

// 播放音乐功能
document.addEventListener('DOMContentLoaded', function() {
    const audioPlayer = document.getElementById('audioPlayer');
    let currentPlayingButton = null; // 记录当前播放的按钮
    
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
    
    // 为所有播放按钮添加点击事件监听器
    const playButtons = document.querySelectorAll('.play-btn');
    playButtons.forEach(button => {
        button.addEventListener('click', function(e) {
            e.preventDefault();
            
            // 如果点击的是当前正在播放的按钮，则暂停
            if (currentPlayingButton === this && !audioPlayer.paused) {
                audioPlayer.pause();
                this.textContent = '播放';
                currentPlayingButton = null;
                return;
            }
            
            // 获取父级音乐卡片元素
            const musicCard = this.closest('.music-card');
            if (!musicCard) return;
            
            // 从data-track-id属性获取trackId
            const trackId = musicCard.getAttribute('data-track-id');
            if (!trackId) {
                console.error('未找到trackId');
                return;
            }
            
            // 调用API获取音乐URL
            fetch(`/api/music/track?id=${trackId}`)
                .then(response => {
                    if (!response.ok) {
                        throw new Error(`HTTP error! status: ${response.status}`);
                    }
                    return response.json();
                })
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
                    audioPlayer.play()
                        .then(() => {
                            console.log('音乐开始播放');
                            
                            // 恢复之前按钮的状态
                            if (currentPlayingButton && currentPlayingButton !== this) {
                                currentPlayingButton.textContent = '播放';
                                currentPlayingButton.disabled = false;
                            }
                            
                            // 更新当前播放按钮
                            this.textContent = '播放中...';
                            currentPlayingButton = this;
                            
                            // 更新播放器UI
                            const card = this.closest('.music-card');
                            const title = card.querySelector('.card-title').textContent;
                            const artist = card.querySelector('.card-artist').textContent;
                            updatePlayerUI(title, artist, true);
                            
                            // 播放结束后恢复按钮状态
                            audioPlayer.onended = () => {
                                if (currentPlayingButton) {
                                    currentPlayingButton.textContent = '播放';
                                    currentPlayingButton = null;
                                    // 隐藏播放器
                                    document.querySelector('.player').style.display = 'none';
                                }
                            };
                        })
                        .catch(error => {
                            console.error('播放失败:', error);
                            alert('播放失败，请稍后重试');
                        });
                })
                .catch(error => {
                    console.error('获取音乐URL失败:', error);
                    alert('获取音乐失败，请稍后重试');
                });
        });
    });
    
    // 为专辑播放按钮添加点击事件监听器
    const albumPlayButtons = document.querySelectorAll('.play-album-btn');
    albumPlayButtons.forEach(button => {
        button.addEventListener('click', function(e) {
            e.preventDefault();
            
            // 如果点击的是当前正在播放的按钮，则暂停
            if (currentPlayingButton === this && !audioPlayer.paused) {
                audioPlayer.pause();
                this.textContent = '立即播放';
                currentPlayingButton = null;
                return;
            }
            
            // 获取专辑ID
            const albumId = this.getAttribute('data-album-id');
            if (!albumId) {
                console.error('未找到albumId');
                return;
            }
            
            // 先获取专辑详情，然后播放第一首曲目
            fetch(`/api/music/detail?albumId=${albumId}`)
                .then(response => response.json())
                .then(albumDetail => {
                    if (!albumDetail.tracks || albumDetail.tracks.length === 0) {
                        console.error('专辑中没有曲目');
                        alert('该专辑暂无曲目');
                        return;
                    }
                    
                    // 获取第一首曲目的ID
                    const firstTrack = albumDetail.tracks[0];
                    const trackId = firstTrack.id || firstTrack.cloudMusicId || firstTrack.neteaseId;
                    
                    if (!trackId) {
                        console.error('未找到曲目ID');
                        alert('未找到可播放的曲目');
                        return;
                    }
                    
                    // 调用API获取音乐URL
                    fetch(`/api/music/track?id=${trackId}`)
                        .then(response => {
                            if (!response.ok) {
                                throw new Error(`HTTP error! status: ${response.status}`);
                            }
                            return response.json();
                        })
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
                            audioPlayer.play()
                                .then(() => {
                                    console.log('专辑音乐开始播放');
                                    
                                    // 恢复之前按钮的状态
                                    if (currentPlayingButton && currentPlayingButton !== this) {
                                        currentPlayingButton.textContent = '立即播放';
                                        currentPlayingButton.disabled = false;
                                    }
                                    
                                    // 更新当前播放按钮
                                    this.textContent = '播放中...';
                                    currentPlayingButton = this;
                                    
                                    // 更新播放器UI
                                    updatePlayerUI(albumDetail.title, albumDetail.artist, true);
                                    
                                    // 播放结束后恢复按钮状态
                                    audioPlayer.onended = () => {
                                        if (currentPlayingButton) {
                                            currentPlayingButton.textContent = '立即播放';
                                            currentPlayingButton = null;
                                            // 隐藏播放器
                                            document.querySelector('.player').style.display = 'none';
                                        }
                                    };
                                })
                                .catch(error => {
                                    console.error('播放失败:', error);
                                    alert('播放失败，请稍后重试');
                                });
                        })
                        .catch(error => {
                            console.error('获取专辑音乐URL失败:', error);
                            alert('获取音乐失败，请稍后重试');
                        });
                })
                .catch(error => {
                    console.error('获取专辑详情失败:', error);
                    alert('获取专辑详情失败，请稍后重试');
                });
        });
    });
});