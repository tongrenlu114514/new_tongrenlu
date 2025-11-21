// 首页相关功能 - 完全使用jQuery重构
$(function () {
    // 淡入动画 - 使用jQuery动画和延迟队列
    const fadeElements = $('.music-card, .artist-card, .section-title');
    fadeElements.css({
        'opacity': '0',
        'transform': 'translateY(20px)'
    }).each(function (index) {
        $(this).delay(200 * index).queue(function (next) {
            $(this).css({
                'transition': 'opacity 0.6s ease, transform 0.6s ease',
                'opacity': '1',
                'transform': 'translateY(0)'
            });
            next();
        });
    });

    // "开始探索"按钮点击事件 - 优化事件处理
    $('#start-explore-btn').on('click', function (e) {
        e.preventDefault();
        switchToPage('music');
    });

    // 其他CTA按钮点击事件 - 简化处理
    $('.cta-button:not(#start-explore-btn)').on('click', function (e) {
        e.preventDefault();
        // 其他按钮保持原有的行为（如果需要的话）
    });

    // 轮播功能 - 合并到同一个DOM ready块中
    const carouselContainer = $('.carousel-container');
    const prevBtn = $('#prevBtn');
    const nextBtn = $('#nextBtn');
    const indicators = $('.indicator');
    let currentIndex = 0;
    const totalItems = 3;
    let autoPlayInterval;

    // 更新轮播位置 - 优化jQuery选择器
    function updateCarousel() {
        carouselContainer.css('transform', `translateX(-${currentIndex * 100}%)`);

        // 更新指示器 - 使用jQuery的toggleClass更高效
        indicators.removeClass('active')
            .eq(currentIndex)
            .addClass('active');
    }

    // 下一张 - 箭头函数优化
    function nextSlide() {
        currentIndex = (currentIndex + 1) % totalItems;
        updateCarousel();
    }

    // 上一张
    function prevSlide() {
        currentIndex = (currentIndex - 1 + totalItems) % totalItems;
        updateCarousel();
    }

    // 绑定轮播事件
    nextBtn.on('click', nextSlide);
    prevBtn.on('click', prevSlide);

    // 指示器点击 - 使用jQuery事件委托和data方法
    indicators.on('click', function () {
        currentIndex = $(this).data('index') || 0;
        updateCarousel();
    });

    // 自动播放 - 使用jQuery的setInterval
    function startAutoPlay() {
        autoPlayInterval = setInterval(nextSlide, 5000);
    }

    function stopAutoPlay() {
        clearInterval(autoPlayInterval);
    }

    // 鼠标悬停暂停自动播放
    carouselContainer
        .on('mouseenter', stopAutoPlay)
        .on('mouseleave', startAutoPlay);

    // 启动自动播放
    startAutoPlay();

    // 播放音乐功能 - 使用原生audio元素但jQuery处理UI
    const audioPlayer = $('#audioPlayer')[0];
    let currentPlayingButton = null;

    // 更新播放器UI - 优化jQuery选择器
    function updatePlayerUI(title, artist, playing) {
        const $player = $('.player');

        if (title) {
            $('.now-playing-title').text(title);
        }
        if (artist) {
            $('.now-playing-artist').text(artist);
        }
        // 显示或隐藏播放器
        $player.toggle(playing);
    }

    // 重置播放按钮状态 - 提取公共函数
    function resetPlayingButton() {
        if (currentPlayingButton) {
            $(currentPlayingButton).prop('disabled', false);
            if ($(currentPlayingButton).hasClass('play-btn')) {
                $(currentPlayingButton).text('播放');
            } else if ($(currentPlayingButton).hasClass('play-album-btn')) {
                $(currentPlayingButton).text('立即播放');
            }
            currentPlayingButton = null;
        }
    }

    // 设置播放结束处理
    function setupAudioEndHandler(button, buttonText) {
        $(audioPlayer).off('ended').on('ended', function () {
            resetPlayingButton();
            $('.player').hide();
        });
    }

    // 播放音乐的通用函数
    function playMusic(trackUrl, button, title, artist, buttonText) {
        audioPlayer.src = trackUrl;
        audioPlayer.play()
            .then(() => {
                console.log('音乐开始播放');

                resetPlayingButton();

                // 更新当前播放按钮
                $(button).text('播放中...').prop('disabled', true);
                currentPlayingButton = button;

                // 更新播放器UI
                updatePlayerUI(title, artist, true);

                // 设置播放结束处理
                setupAudioEndHandler(button, buttonText);
            })
            .catch(function (error) {
                console.error('播放失败:', error);
                alert('播放失败，请稍后重试');
            });
    }

    // 播放按钮点击事件 - 事件委托优化
    $(document).on('click', '.play-btn', function (e) {
        e.preventDefault();

        // 如果点击的是当前正在播放的按钮，则暂停
        if (currentPlayingButton === this && !audioPlayer.paused) {
            audioPlayer.pause();
            $(this).text('播放').prop('disabled', false);
            currentPlayingButton = null;
            return;
        }

        const $musicCard = $(this).closest('.music-card');
        if ($musicCard.length === 0) return;

        const trackId = $musicCard.data('track-id');
        if (!trackId) {
            console.error('未找到trackId');
            return;
        }

        // 使用jQuery的get方法简化API调用
        $.get(`/api/music/track?id=${trackId}`)
            .done(function (data) {
                const trackUrl = data && data.url;
                if (!trackUrl) {
                    console.error('API响应中未找到音乐URL');
                    alert('未找到音乐URL');
                    return;
                }

                const title = $musicCard.find('.card-title').text();
                const artist = $musicCard.find('.card-artist').text();

                playMusic(trackUrl, e.currentTarget, title, artist, '播放');
            })
            .fail(function (error) {
                console.error('获取音乐URL失败:', error);
                alert('获取音乐失败，请稍后重试');
            });
    });

    // 专辑播放按钮点击事件 - 优化API调用链
    $(document).on('click', '.play-album-btn', function (e) {
        e.preventDefault();

        // 如果点击的是当前正在播放的按钮，则暂停
        if (currentPlayingButton === this && !audioPlayer.paused) {
            audioPlayer.pause();
            $(this).text('立即播放').prop('disabled', false);
            currentPlayingButton = null;
            return;
        }

        const $button = $(this);
        const albumId = $button.data('album-id');
        if (!albumId) {
            console.error('未找到albumId');
            return;
        }

        // 使用jQuery的Deferred对象来处理异步链
        $.get(`/api/music/detail?albumId=${albumId}`)
            .then(function (albumDetail) {
                if (!albumDetail.tracks || albumDetail.tracks.length === 0) {
                    throw new Error('专辑中没有曲目');
                }

                const firstTrack = albumDetail.tracks[0];
                const trackId = firstTrack.id || firstTrack.cloudMusicId || firstTrack.neteaseId;

                if (!trackId) {
                    throw new Error('未找到曲目ID');
                }

                // 返回第二个AJAX请求的Promise
                return $.get(`/api/music/track?id=${trackId}`);
            })
            .then(function (data) {
                const trackUrl = data && data.url;
                if (!trackUrl) {
                    throw new Error('API响应中未找到音乐URL');
                }

                const $musicCard = $button.closest('.music-card');
                const title = $musicCard.find('.card-title').text() || '专辑';
                const artist = $musicCard.find('.card-artist').text();

                playMusic(trackUrl, e.currentTarget, title, artist, '立即播放');
            })
            .fail(function (error) {
                console.error('播放专辑失败:', error);
                if (error.responseText) {
                    alert('获取专辑详情失败，请稍后重试');
                } else {
                    alert('播放失败，请稍后重试');
                }
            });
    });
});