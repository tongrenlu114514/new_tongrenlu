/**
 * 工具函数模块
 * 通用工具函数和全局状态管理
 */

// 格式化时间显示
function formatTime(seconds) {
    if (isNaN(seconds)) return '0:00';
    const min = Math.floor(seconds / 60);
    const sec = Math.floor(seconds % 60);
    return min + ':' + sec.toString().padStart(2, '0');
}

// 解析URL参数
function getUrlParams() {
    const urlParams = new URLSearchParams(window.location.search);
    return {
        album: urlParams.get('album')
    };
}

// 生成唯一ID
function generateId() {
    return 'id_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9);
}

// 防抖函数
function debounce(func, wait) {
    let timeout;
    return function executedFunction(...args) {
        const later = () => {
            clearTimeout(timeout);
            func(...args);
        };
        clearTimeout(timeout);
        timeout = setTimeout(later, wait);
    };
}

// 节流函数
function throttle(func, limit) {
    let inThrottle;
    return function() {
        const args = arguments;
        const context = this;
        if (!inThrottle) {
            func.apply(context, args);
            inThrottle = true;
            setTimeout(() => inThrottle = false, limit);
        }
    };
}

// 检查是否为测试模式
function isTestMode(albumId) {
    return albumId === 'test001';
}

// 获取测试专辑数据
function getTestAlbumData() {
    return {
        id: "test001",
        title: "东方红魔乡",
        artist: "上海爱丽丝幻乐团",
        coverUrl: "https://p1.music.126.net/24234234/test-album.jpg",
        cloudMusicPicUrl: "https://p1.music.126.net/24234234/test-album.jpg",
        tracks: [
            {
                id: "track001",
                trackId: "track001",
                name: "梦月仙境",
                duration: 285,
                url: "https://example.com/music/dream-moon.mp3"
            },
            {
                id: "track002",
                trackId: "track002",
                name: "红色的迷路少女",
                duration: 198,
                url: "https://example.com/music/red-girl.mp3"
            },
            {
                id: "track003",
                trackId: "track003",
                name: "上海红茶馆 ~ Chinese Tea",
                duration: 245,
                url: "https://example.com/music/shanghai-tea.mp3"
            },
            {
                id: "track004",
                trackId: "track004",
                name: "明治十七年的上海爱丽丝",
                duration: 312,
                url: "https://example.com/music/meiji-shanghai.mp3"
            }
        ]
    };
}

// 显示加载状态
function showLoadingStatus(message, elementId = 'albumTitle') {
    $(`#${elementId}`).html(`<span style="color: rgba(255,255,255,0.7);">${message}</span>`);
}

// 显示错误消息
function showError(message, elementId = 'albumTitle') {
    $(`#${elementId}`).html(`<span style="color: #ff6b6b;">${message}</span>`);
}

// 日志函数
function log(message, ...args) {
    if (console && console.log) {
        console.log(message, ...args);
    }
}

// 错误日志
function error(message, ...args) {
    if (console && console.error) {
        console.error(message, ...args);
    }
}

// 导出函数（如果使用ES6模块）
if (typeof module !== 'undefined' && module.exports) {
    module.exports = {
        formatTime,
        getUrlParams,
        generateId,
        debounce,
        throttle,
        isTestMode,
        getTestAlbumData,
        showLoadingStatus,
        showError,
        log,
        error
    };
}
