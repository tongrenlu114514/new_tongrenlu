/**
 * 基础UI控制模块
 * 包含播放器的基础UI控制函数
 */

// 切换随机播放
function toggleShuffle(currentState, button) {
    const newState = !currentState;
    button.toggleClass('active', newState);
    return newState;
}

// 切换重复模式
function toggleRepeat(currentMode, button) {
    const modes = ['none', 'all', 'one'];
    const currentIndex = modes.indexOf(currentMode);
    const newMode = modes[(currentIndex + 1) % modes.length];

    const $icon = button.find('i');
    $icon.removeClass('fa-redo fa-redo-alt fa-repeat');

    if (newMode === 'one') {
        $icon.addClass('fa-redo-alt');
        button.addClass('active');
    } else if (newMode === 'all') {
        $icon.addClass('fa-redo');
        button.addClass('active');
    } else {
        $icon.addClass('fa-redo');
        button.removeClass('active');
    }

    return newMode;
}


// 获取音频错误消息
function getAudioErrorMessage(errorCode) {
    switch(errorCode) {
        case 1:
            return '获取音频错误';
        case 2:
            return '网络错误';
        case 3:
            return '音频解码错误';
        case 4:
            return '音频格式不支持';
        default:
            return '未知错误';
    }
}

// 更新播放器UI（非全屏页面用）
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

// 初始化播放器
function initializePlayer() {
    // 调用初始化函数
    // loadMusicData();
    initPlayerEvents();
    setupKeyboardShortcuts();
    setupAudioEvents();
    setupUIEvents();
}

// 导出函数（如果使用ES6模块）
if (typeof module !== 'undefined' && module.exports) {
    module.exports = {
        toggleShuffle,
        toggleRepeat,
        getAudioErrorMessage,
        updatePlayerUI,
        initializePlayer
    };
}