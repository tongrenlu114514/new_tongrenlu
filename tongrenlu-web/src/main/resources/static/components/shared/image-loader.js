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
        $(element).css('background-image', `url('${cachedDataUrl}')`);
        // 移除现有的fallback内容
        $(element).find('.fallback-content').remove();
        return;
    }

    // 检查是否正在加载
    if (pendingRequests.has(cacheKey)) {
        // 添加到回调队列
        const callbackArray = pendingRequests.get(cacheKey);
        callbackArray.push((dataUrl) => {
            $(element).css('background-image', `url('${dataUrl}')`);
            // 移除现有的fallback内容
            $(element).find('.fallback-content').remove();
        });
        return;
    }

    // 标记为正在加载
    pendingRequests.set(cacheKey, []);

    // 创建图片对象
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
                    loadImageWithCache(entry.target, url, 200, 200);
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