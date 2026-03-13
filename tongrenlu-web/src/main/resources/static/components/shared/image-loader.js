// 图片缓存和缩略图处理
const imageCache = new Map(); // 图片缓存
const pendingRequests = new Map(); // 正在加载的图片请求

/**
 * 获取优化后的图片URL
 * 网易云音乐图片支持 ?param=宽y高 参数控制尺寸
 * @param {string} url - 原始图片URL
 * @param {number} width - 目标宽度
 * @param {number} height - 目标高度
 * @returns {string} - 优化后的URL
 */
function getOptimizedImageUrl(url, width, height) {
    if (!url) return url;
    
    // 检测是否是网易云音乐图片URL
    if (url.includes('music.126.net') || url.includes('127.net')) {
        // 移除已有的param参数
        const baseUrl = url.split('?')[0];
        // 添加尺寸参数
        return `${baseUrl}?param=${width}y${height}`;
    }
    
    return url;
}

/**
 * 直接设置图片URL（不经过canvas处理）
 * 用于支持服务器端缩略图的图片
 */
function setImageDirectly(element, url) {
    $(element).css('background-image', `url('${url}')`);
    $(element).find('.fallback-content').remove();
}

// 使用缓存机制加载图片
function loadImageWithCache(element, url, width, height) {
    // 生成缓存键
    const cacheKey = `${url}_${width}x${height}`;

    // 检查是否已经在缓存中
    if (imageCache.has(cacheKey)) {
        const cachedDataUrl = imageCache.get(cacheKey);
        if (cachedDataUrl.startsWith('http')) {
            setImageDirectly(element, cachedDataUrl);
        } else {
            $(element).css('background-image', `url('${cachedDataUrl}')`);
            $(element).find('.fallback-content').remove();
        }
        return;
    }

    // 检查是否正在加载
    if (pendingRequests.has(cacheKey)) {
        // 添加到回调队列
        const callbackArray = pendingRequests.get(cacheKey);
        callbackArray.push((dataUrl) => {
            if (dataUrl) {
                if (dataUrl.startsWith('http')) {
                    setImageDirectly(element, dataUrl);
                } else {
                    $(element).css('background-image', `url('${dataUrl}')`);
                    $(element).find('.fallback-content').remove();
                }
            }
        });
        return;
    }

    // 标记为正在加载
    pendingRequests.set(cacheKey, []);

    // 检测是否是网易云音乐图片，直接使用服务器端缩略图
    if (url.includes('music.126.net') || url.includes('127.net')) {
        const optimizedUrl = getOptimizedImageUrl(url, width, height);
        
        // 直接使用优化后的URL
        const img = new Image();
        img.crossOrigin = 'Anonymous';
        
        $(img).on('load', function() {
            // 缓存优化后的URL
            imageCache.set(cacheKey, optimizedUrl);
            setImageDirectly(element, optimizedUrl);
            
            // 执行回调队列
            const callbacks = pendingRequests.get(cacheKey) || [];
            $.each(callbacks, function(index, callback) {
                callback(optimizedUrl);
            });
            pendingRequests.delete(cacheKey);
        });
        
        $(img).on('error', function() {
            console.error('图片加载失败:', optimizedUrl);
            if ($(element).find('.fallback-content').length === 0) {
                $(element).html('<div class="fallback-content">🎵</div>');
            }
            
            const callbacks = pendingRequests.get(cacheKey) || [];
            $.each(callbacks, function(index, callback) {
                callback(null);
            });
            pendingRequests.delete(cacheKey);
        });
        
        img.src = optimizedUrl;
        return;
    }

    // 非网易云音乐图片，使用canvas处理
    const img = new Image();
    img.crossOrigin = 'Anonymous';

    $(img).on('load', function () {
        // 创建canvas进行缩略图处理
        const $canvas = $('<canvas>')[0];
        const ctx = $canvas.getContext('2d', {alpha: false});

        // 设置canvas尺寸
        $canvas.width = width;
        $canvas.height = height;

        // 计算缩放比例以保持宽高比（填充模式）
        const widthRatio = width / img.width;
        const heightRatio = height / img.height;
        const ratio = Math.max(widthRatio, heightRatio); // 使用最大比例以保证填满容器

        const newWidth = img.width * ratio;
        const newHeight = img.height * ratio;

        // 配置高质量绘制设置
        ctx.imageSmoothingEnabled = true;
        ctx.imageSmoothingQuality = 'high';

        // 在canvas上绘制缩略图（居中裁剪）
        ctx.drawImage(
            img,
            (width - newWidth) / 2,
            (height - newHeight) / 2,
            newWidth,
            newHeight
        );

        // 转换为PNG格式（无损），避免JPEG压缩导致的质量损失
        const dataUrl = $canvas.toDataURL('image/png');

        // 缓存处理后的图片
        imageCache.set(cacheKey, dataUrl);

        // 应用到元素
        $(element).css('background-image', `url('${dataUrl}')`);
        // 移除现有的fallback内容
        $(element).find('.fallback-content').remove();

        // 执行回调队列
        const callbacks = pendingRequests.get(cacheKey) || [];
        $.each(callbacks, function (index, callback) {
            callback(dataUrl);
        });

        // 清除请求标记
        pendingRequests.delete(cacheKey);
    });

    $(img).on('error', function () {
        console.error('图片加载失败:', url);
        // 显示默认图标
        if ($(element).find('.fallback-content').length === 0) {
            $(element).html('<div class="fallback-content">🎵</div>');
        }

        // 执行回调队列
        const callbacks = pendingRequests.get(cacheKey) || [];
        $.each(callbacks, function (index, callback) {
            callback(null);
        });

        // 清除请求标记
        pendingRequests.delete(cacheKey);
    });

    // 开始加载图片
    img.src = url;
}

// 懒加载音乐卡片封面图片
function lazyLoadAlbumCovers() {
    const albumCovers = $('.album-cover[data-original-url]:not([data-loaded])');
    const options = {
        root: null,
        rootMargin: '0px',
        threshold: 0.1
    };

    const observer = new IntersectionObserver((entries, observer) => {
        $.each(entries, function (index, entry) {
            if (entry.isIntersecting) {
                const cover = $(entry.target);
                const url = cover.data('original-url');

                if (url) {
                    // 使用缓存机制加载图片
                    loadImageWithCache(entry.target, url, 300, 300);
                    cover.attr('data-loaded', 'true');
                }

                // 停止观察已加载的元素
                observer.unobserve(entry.target);
            }
        });
    }, options);

    albumCovers.each(function () {
        observer.observe(this);
    });
}

// 页面滚动时触发懒加载
let lazyLoadThrottleTimeout;
$(window).on('scroll', function () {
    if (!lazyLoadThrottleTimeout) {
        lazyLoadThrottleTimeout = setTimeout(function () {
            lazyLoadAlbumCovers();
            lazyLoadThrottleTimeout = null;
        }, 20);
    }
});

// 页面加载完成后初始化懒加载
$(function () {
    lazyLoadAlbumCovers();
});

// 每次搜索结果更新后触发懒加载
function triggerLazyLoadAfterSearchForImageLoader() {
    setTimeout(function () {
        lazyLoadAlbumCovers();
    }, 100);
}