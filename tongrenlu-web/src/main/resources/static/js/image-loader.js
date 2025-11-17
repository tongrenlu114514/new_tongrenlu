// 图片缓存和缩略图处理
const imageCache = new Map(); // 图片缓存
const pendingRequests = new Map(); // 正在加载的图片请求

// 使用缓存机制加载图片
function loadImageWithCache(element, url, width, height) {
    // 生成缓存键
    const cacheKey = `${url}_${width}x${height}`;
    
    // 检查是否已经在缓存中
    if (imageCache.has(cacheKey)) {
        const cachedDataUrl = imageCache.get(cacheKey);
        element.style.backgroundImage = `url('${cachedDataUrl}')`;
        // 移除现有的fallback内容
        const fallbackContent = element.querySelector('.fallback-content');
        if (fallbackContent) {
            fallbackContent.remove();
        }
        return;
    }
    
    // 检查是否正在加载
    if (pendingRequests.has(cacheKey)) {
        // 添加到回调队列
        pendingRequests.get(cacheKey).push((dataUrl) => {
            element.style.backgroundImage = `url('${dataUrl}')`;
            // 移除现有的fallback内容
            const fallbackContent = element.querySelector('.fallback-content');
            if (fallbackContent) {
                fallbackContent.remove();
            }
        });
        return;
    }
    
    // 标记为正在加载
    pendingRequests.set(cacheKey, []);
    
    // 创建图片对象
    const img = new Image();
    img.crossOrigin = 'Anonymous';
    img.onload = function() {
        // 创建canvas进行缩略图处理
        const canvas = document.createElement('canvas');
        const ctx = canvas.getContext('2d');
        
        // 设置canvas尺寸
        canvas.width = width;
        canvas.height = height;
        
        // 计算缩放比例以保持宽高比
        const scale = Math.min(img.width, img.height) / Math.max(width, height);
        const newWidth = img.width * (width / Math.max(img.width, img.height));
        const newHeight = img.height * (height / Math.max(img.width, img.height));
        
        // 在canvas上绘制缩略图
        ctx.drawImage(
            img, 
            (width - newWidth) / 2, 
            (height - newHeight) / 2, 
            newWidth, 
            newHeight
        );
        
        // 转换为data URL（提高质量参数从0.8到0.95，图像更清晰）
        const dataUrl = canvas.toDataURL('image/jpeg', 0.95);
        
        // 缓存处理后的图片
        imageCache.set(cacheKey, dataUrl);
        
        // 应用到元素
        element.style.backgroundImage = `url('${dataUrl}')`;
        // 移除现有的fallback内容
        const fallbackContent = element.querySelector('.fallback-content');
        if (fallbackContent) {
            fallbackContent.remove();
        }
        
        // 执行回调队列
        const callbacks = pendingRequests.get(cacheKey) || [];
        callbacks.forEach(callback => callback(dataUrl));
        
        // 清除请求标记
        pendingRequests.delete(cacheKey);
    };
    
    img.onerror = function() {
        console.error('图片加载失败:', url);
        // 显示默认图标
        if (!element.querySelector('.fallback-content')) {
            element.innerHTML = '<div class="fallback-content">🎵</div>';
        }
        
        // 执行回调队列
        const callbacks = pendingRequests.get(cacheKey) || [];
        callbacks.forEach(callback => callback(null));
        
        // 清除请求标记
        pendingRequests.delete(cacheKey);
    };
    
    // 开始加载图片
    img.src = url;
}

// 懒加载音乐卡片封面图片
function lazyLoadAlbumCovers() {
    const albumCovers = document.querySelectorAll('.album-cover[data-original-url]:not([data-loaded])');
    const options = {
        root: null,
        rootMargin: '0px',
        threshold: 0.1
    };
    
    const observer = new IntersectionObserver((entries, observer) => {
        entries.forEach(entry => {
            if (entry.isIntersecting) {
                const cover = entry.target;
                const url = cover.getAttribute('data-original-url');
                
                if (url) {
                    // 使用缓存机制加载图片
                    loadImageWithCache(cover, url, 200, 200);
                    cover.setAttribute('data-loaded', 'true');
                }
                
                // 停止观察已加载的元素
                observer.unobserve(cover);
            }
        });
    }, options);
    
    albumCovers.forEach(cover => {
        observer.observe(cover);
    });
}

// 页面滚动时触发懒加载
let lazyLoadThrottleTimeout;
window.addEventListener('scroll', () => {
    if (!lazyLoadThrottleTimeout) {
        lazyLoadThrottleTimeout = setTimeout(() => {
            lazyLoadAlbumCovers();
            lazyLoadThrottleTimeout = null;
        }, 20);
    }
});

// 页面加载完成后初始化懒加载
document.addEventListener('DOMContentLoaded', function() {
    lazyLoadAlbumCovers();
});

// 每次搜索结果更新后触发懒加载
function triggerLazyLoadAfterSearchForImageLoader() {
    setTimeout(() => {
        lazyLoadAlbumCovers();
    }, 100);
}