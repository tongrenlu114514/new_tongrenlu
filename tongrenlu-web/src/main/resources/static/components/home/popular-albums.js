/**
 * 热门推荐组件
 */
(function($) {
    'use strict';

    const PopularAlbums = {
        $grid: null,

        init: function() {
            this.$grid = $('#popularAlbumsGrid');
            this.loadAlbums();
        },

        loadAlbums: function() {
            const self = this;

            $.ajax({
                url: 'api/music/search',
                data: {
                    pageNumber: 1,
                    pageSize: 8
                },
                success: function(response) {
                    if (response && response.records) {
                        self.renderAlbums(response.records);
                    }
                },
                error: function() {
                    self.showError();
                }
            });
        },

        renderAlbums: function(albums) {
            if (!albums || albums.length === 0) {
                this.$grid.html('<p class="home-empty">暂无热门专辑</p>');
                return;
            }

            const html = albums.map(function(album) {
                // 使用优化后的图片URL（200x200）
                const optimizedUrl = album.cloudMusicPicUrl 
                    ? (typeof getOptimizedImageUrl === 'function' ? getOptimizedImageUrl(album.cloudMusicPicUrl, 200, 200) : album.cloudMusicPicUrl)
                    : null;
                const coverStyle = optimizedUrl
                    ? 'background-image: url(' + optimizedUrl + ')'
                    : '';
                const fallback = optimizedUrl ? '' : '<span class="home-album-card__cover-fallback">🎵</span>';

                return '<div class="home-album-card" data-album-id="' + album.id + '">' +
                    '<div class="home-album-card__cover" style="' + coverStyle + '">' +
                    fallback +
                    '<button class="home-album-card__play-btn"><i class="fas fa-play"></i></button>' +
                    '</div>' +
                    '<div class="home-album-card__info">' +
                    '<h3 class="home-album-card__title">' + (album.title || '未知专辑') + '</h3>' +
                    '<p class="home-album-card__artist">' + (album.artist || '未知艺术家') + '</p>' +
                    '</div>' +
                    '</div>';
            }).join('');

            this.$grid.html(html);
        },

        showError: function() {
            this.$grid.html('<p class="home-empty">加载失败，请刷新重试</p>');
        }
    };

    // 暴露到全局
    window.PopularAlbums = PopularAlbums;

    $(document).ready(function() {
        PopularAlbums.init();
    });

})(jQuery);
