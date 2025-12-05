/**
 * 播放器事件处理模块
 * 负责初始化播放器的各种事件监听
 */

// 初始化播放器事件
function initPlayerEvents() {
    console.log('开始初始化播放器事件...');

    // 设置键盘快捷键
    if (typeof setupKeyboardShortcuts === 'function') {
        console.log('正在初始化键盘快捷键...');
        setupKeyboardShortcuts();
        console.log('键盘快捷键初始化完成');
    } else {
        console.warn('setupKeyboardShortcuts函数未定义');
    }

    // 设置音频事件
    if (typeof setupAudioEvents === 'function') {
        console.log('正在初始化音频事件...');
        setupAudioEvents();
        console.log('音频事件初始化完成');
    } else {
        console.warn('setupAudioEvents函数未定义');
    }

    // 设置UI事件
    if (typeof setupUIEvents === 'function') {
        console.log('正在初始化UI事件...');
        setupUIEvents();
        console.log('UI事件初始化完成');
    } else {
        console.warn('setupUIEvents函数未定义');
    }

    console.log('播放器事件初始化完成');
}

// 导出函数（如果使用ES6模块）
if (typeof module !== 'undefined' && module.exports) {
    module.exports = {
        initPlayerEvents
    };
}
