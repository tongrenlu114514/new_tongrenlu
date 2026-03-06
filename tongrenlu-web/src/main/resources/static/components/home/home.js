/**
 * 首页主功能模块
 * 处理专辑数量统计、页面交互和专辑卡片点击事件
 */
(function($) {
    'use strict';

    const HomePage = {
        init: function() {
            this.loadAlbumCount();
            this.bindEvents();
        },

        // 加载专辑数量统计
        loadAlbumCount: function() {
            $.ajax({
                url: 'api/music/album-stats',
                method: 'GET',
                success: function(response) {
                    if (response && response.success && response.data) {
                        const count = response.data.published || response.data.total || 0;
                        $('#navAlbumCount').text(count.toLocaleString());
                    }
                },
                error: function() {
                    $('#navAlbumCount').text('--');
                }
            });
        },

        bindEvents: function() {
            // "开始探索"按钮
            $('#start-explore-btn').on('click', function(e) {
                e.preventDefault();
                window.open('player.html', '_blank');
            });

            // 专辑卡片点击 - 打开专辑详情
            $(document).on('click', '.home-album-card', function(e) {
                // 如果点击的是播放按钮，不触发卡片点击
                if ($(e.target).closest('.home-album-card__play-btn').length) {
                    return;
                }

                const albumId = $(this).data('album-id');
                if (albumId) {
                    // 跳转到播放器页面播放该专辑
                    window.open('player.html?album=' + albumId, '_blank');
                }
            });

            // 专辑卡片播放按钮
            $(document).on('click', '.home-album-card__play-btn', function(e) {
                e.preventDefault();
                e.stopPropagation();

                const $card = $(this).closest('.home-album-card');
                const albumId = $card.data('album-id');

                if (albumId) {
                    window.open('player.html?album=' + albumId, '_blank');
                }
            });

            // 艺术家卡片点击
            $(document).on('click', '.home-artist-card', function(e) {
                e.preventDefault();
                const artistId = $(this).data('artist-id');
                if (artistId) {
                    // 跳转到艺术家页面
                    window.location.href = 'artist.html?id=' + artistId;
                }
            });
        }
    };

    // 暴露到全局
    window.HomePage = HomePage;

    $(document).ready(function() {
        HomePage.init();
    });

})(jQuery);
