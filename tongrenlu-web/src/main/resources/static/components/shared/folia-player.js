// Folia 播放器跳转工具
// 统一封装对 Folia 播放器的打开逻辑，避免散落的 player.html 链接

// Folia 基础 URL，与后端 FoliaConfig#folia.base-url 对齐
const FOLIA_BASE_URL = (window.FOLIA_BASE_URL || 'http://121.37.183.44/folia/').replace(/\/$/, '/');

/**
 * 在新窗口中打开 Folia 播放器，定位到指定专辑
 * @param {number|string} albumId 专辑 ID
 * @param {object} [options] 额外选项
 * @param {boolean} [options.newTab=true] 是否在新标签页打开
 */
function openFoliaPlayer(albumId, options = {}) {
    if (albumId === undefined || albumId === null) {
        console.warn('openFoliaPlayer: missing albumId');
        return;
    }
    const { newTab = true } = options;
    const url = buildFoliaAlbumUrl(albumId);
    if (newTab) {
        window.open(url, '_blank');
    } else {
        window.location.href = url;
    }
}

/**
 * 拼装 Folia 专辑页 URL
 * @param {number|string} albumId
 * @returns {string}
 */
function buildFoliaAlbumUrl(albumId) {
    return `${FOLIA_BASE_URL}#collection/online/tongrenlu/album/${albumId}`;
}
