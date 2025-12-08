/**
 * 全屏播放器主模块
 * 整合所有播放器模块和初始化逻辑
 */

// 浮动音符动画
function startFloatingAnimation() {
    const notes = document.querySelectorAll('.note');

    notes.forEach((note, index) => {
        // 随机初始位置
        note.style.left = Math.random() * 100 + '%';

        // 随机动画延迟
        note.style.animationDelay = (index * 2) + Math.random() * 2 + 's';

        // 随机动画持续时间
        const duration = 10 + Math.random() * 10;
        note.style.animationDuration = duration + 's';

        // 设置动画间隔
        note.style.animationIterationCount = 'infinite';
    });

    console.log('浮动音符动画已启动');
}

// 检查浏览器自动播放策略
function checkAutoplayPolicy() {
    const playPromise = window.audioPlayer.play();

    if (playPromise !== undefined) {
        playPromise.then(() => {
            console.log('自动播放允许');
            window.isPlaying = true;
            updatePlayButton(true);
            hidePlayOverlay();
        }).catch(() => {
            console.log('自动播放被阻止，需要用户交互');
            showPlayOverlay();
        });
    }
}

// 清理资源
function cleanup() {
    if (window.audioPlayer) {
        window.audioPlayer.pause();
        window.audioPlayer.src = '';
        window.audioPlayer.load();
    }
    console.log('播放器资源已清理');
}

// 检查音频格式支持
function checkAudioFormatSupport() {
    const audio = document.createElement('audio');
    const formats = {
        'mp3': 'audio/mpeg',
        'ogg': 'audio/ogg',
        'wav': 'audio/wav',
        'flac': 'audio/flac',
        'm4a': 'audio/mp4',
        'aac': 'audio/aac'
    };

    console.log('音频格式支持检测:');
    for (const [ext, mime] of Object.entries(formats)) {
        const supported = audio.canPlayType(mime);
        console.log(`  ${ext} (${mime}): ${supported || '不支持'}`);
    }
}

// 错误处理
window.addEventListener('error', function(e) {
    console.error('全局错误:', e.error);
    if (e.error && e.error.message) {
        if (e.error.message.includes('playTrack')) {
            showError('播放功能初始化失败，请刷新页面重试');
        } else if (e.error.message.includes('currentMusicData')) {
            showError('音乐数据加载失败，请检查网络连接');
        }
    }
});

// 页面卸载时清理
window.addEventListener('beforeunload', cleanup);

// 页面可见性变化处理
document.addEventListener('visibilitychange', function() {
    if (document.hidden) {
        console.log('页面隐藏，继续播放后台音频');
    } else {
        console.log('页面显示');
    }
});

// 全屏状态变化处理
document.addEventListener('fullscreenchange', function() {
    if (document.fullscreenElement) {
        console.log('进入全屏模式');
        $('#fullscreenBtn i').removeClass('fa-expand').addClass('fa-compress');
    } else {
        console.log('退出全屏模式');
        $('#fullscreenBtn i').removeClass('fa-compress').addClass('fa-expand');
    }
});

// 页面加载完成后初始化
$(document).ready(function() {
    console.log('播放器初始化开始...');

    // 设置初始音量
    window.audioPlayer.volume = 0.7;
    $('#volumeLevel').css('width', '70%');
    updateVolumeIcon(0.7);

    // 检查音频格式支持
    checkAudioFormatSupport();

    // 初始化事件
    console.log('开始初始化播放器事件...');
    try {
        initPlayerEvents();
        console.log('播放器事件初始化完成');
    } catch (error) {
        console.error('播放器事件初始化失败:', error);
    }

    // 检查自动播放策略
    checkAutoplayPolicy();

    // 根据URL参数或加载随机专辑
    loadMusicData();

    console.log('播放器初始化完成');
});

// 导出关键函数供外部使用（如果需要）
window.PlayerFullscreen = {
    playTrack: (index) => playTrack(index),
    pause: () => window.audioPlayer.pause(),
    play: () => window.audioPlayer.play(),
    setVolume: (vol) => {
        window.audioPlayer.volume = vol;
        $('#volumeLevel').css('width', (vol * 100) + '%');
        updateVolumeIcon(vol);
    },
    getCurrentTrack: () => (window.currentMusicData && window.currentMusicData.tracks) ? window.currentMusicData.tracks[window.currentTrackIndex] : null,
    getCurrentTime: () => window.audioPlayer.currentTime,
    getDuration: () => window.audioPlayer.duration,
    seekTo: (time) => {
        if (window.audioPlayer.duration) {
            window.audioPlayer.currentTime = Math.min(Math.max(time, 0), window.audioPlayer.duration);
        }
    }
};

// 导出函数（如果使用ES6模块）
if (typeof module !== 'undefined' && module.exports) {
    module.exports = {
        startFloatingAnimation,
        checkAutoplayPolicy,
        cleanup,
        checkAudioFormatSupport
    };
}