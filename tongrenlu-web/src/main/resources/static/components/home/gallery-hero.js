/**
 * 图片画廊 Hero 区域组件
 */
(function($) {
    'use strict';

    const GalleryHero = {
        $grid: null,
        albums: [],
        refreshInterval: null,
        AUTO_REFRESH_INTERVAL: 30000, // 30秒自动刷新
        // 画廊布局配置：15个位置，部分是大图
        layout: [
            { class: 'home-gallery-item--large', row: 0, col: 0 },  // 0: 大图
            { class: '', row: 0, col: 2 },
            { class: '', row: 0, col: 3 },
            { class: 'home-gallery-item--tall', row: 0, col: 4 },   // 3: 竖图
            { class: '', row: 1, col: 2 },
            { class: '', row: 1, col: 3 },
            { class: 'home-gallery-item--wide', row: 2, col: 0 },   // 6: 宽图
            { class: '', row: 2, col: 2 },
            { class: 'home-gallery-item--large', row: 2, col: 3 },  // 8: 大图
            { class: '', row: 3, col: 0 },
            { class: '', row: 3, col: 1 },
            { class: '', row: 3, col: 2 },
            { class: '', row: 3, col: 3 },
            { class: '', row: 3, col: 4 },
            { class: '', row: 1, col: 4 },  // 补充竖图的第二个格子
        ],

        init: function() {
            this.$grid = $('#galleryGrid');
            this.loadAlbums();
            this.startAutoRefresh();
        },

        startAutoRefresh: function() {
            const self = this;
            this.refreshInterval = setInterval(function() {
                self.refreshGallery();
            }, this.AUTO_REFRESH_INTERVAL);
        },

        stopAutoRefresh: function() {
            if (this.refreshInterval) {
                clearInterval(this.refreshInterval);
                this.refreshInterval = null;
            }
        },

        refreshGallery: function() {
            const self = this;
            // 淡出当前画廊
            this.$grid.fadeOut(300, function() {
                // 重新获取随机专辑
                $.ajax({
                    url: 'api/music/random-albums',
                    data: { count: 15 },
                    success: function(response) {
                        if (response && response.length > 0) {
                            self.albums = response;
                            self.renderGallery();
                            // 淡入新画廊
                            self.$grid.hide().fadeIn(500);
                        }
                    }
                });
            });
        },

        loadAlbums: function() {
            const self = this;

            $.ajax({
                url: 'api/music/random-albums',
                data: {
                    count: 15
                },
                success: function(response) {
                    if (response && response.length > 0) {
                        self.albums = response;
                        self.renderGallery();
                    }
                },
                error: function() {
                    self.showFallback();
                }
            });
        },

        renderGallery: function() {
            if (!this.albums || this.albums.length === 0) {
                this.showFallback();
                return;
            }

            const self = this;
            // 随机打乱专辑顺序，让每次刷新看到的封面不同
            const shuffledAlbums = this.shuffleArray([...this.albums]);

            // 渲染15个画廊位置
            const html = this.layout.map(function(item, index) {
                const album = shuffledAlbums[index % shuffledAlbums.length];
                // 使用优化后的图片URL（300x300）
                const optimizedUrl = album.cloudMusicPicUrl 
                    ? (typeof getOptimizedImageUrl === 'function' ? getOptimizedImageUrl(album.cloudMusicPicUrl, 300, 300) : album.cloudMusicPicUrl)
                    : null;
                const coverStyle = optimizedUrl
                    ? 'background-image: url(' + optimizedUrl + ')'
                    : '';
                const title = album.title || '';

                return '<div class="home-gallery-item ' + item.class + '" data-album-id="' + album.id + '" data-title="' + title + '">' +
                    '<div class="home-gallery-item__cover" style="' + coverStyle + '"></div>' +
                    '</div>';
            }).join('');

            this.$grid.html(html);

            // 绑定点击事件
            this.bindEvents();
        },

        bindEvents: function() {
            this.$grid.on('click', '.home-gallery-item', function() {
                const albumId = $(this).data('album-id');
                if (albumId) {
                    window.open('player.html?album=' + albumId, '_blank');
                }
            });
        },

        showFallback: function() {
            // 如果没有专辑数据，显示渐变背景
            this.$grid.html('');
        },

        shuffleArray: function(array) {
            for (let i = array.length - 1; i > 0; i--) {
                const j = Math.floor(Math.random() * (i + 1));
                [array[i], array[j]] = [array[j], array[i]];
            }
            return array;
        }
    };

    // 暴露到全局
    window.GalleryHero = GalleryHero;

    $(document).ready(function() {
        GalleryHero.init();
    });

})(jQuery);
