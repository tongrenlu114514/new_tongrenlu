/**
 * 艺术家推荐组件
 */
(function($) {
    'use strict';

    const ArtistRecommendation = {
        $grid: null,

        init: function() {
            this.$grid = $('#artistsGrid');
            this.loadArtists();
        },

        loadArtists: function() {
            const self = this;

            $.ajax({
                url: 'api/artist/list',
                data: {
                    page: 1,
                    limit: 6
                },
                success: function(response) {
                    if (response && response.success && response.data && response.data.records) {
                        self.renderArtists(response.data.records);
                    }
                },
                error: function() {
                    self.showError();
                }
            });
        },

        renderArtists: function(artists) {
            if (!artists || artists.length === 0) {
                this.$grid.html('<p class="home-empty">暂无推荐艺术家</p>');
                return;
            }

            const html = artists.map(function(artist) {
                const avatarStyle = artist.avatarUrl
                    ? 'background-image: url(' + artist.avatarUrl + ')'
                    : '';
                const initial = artist.name ? artist.name.charAt(0) : '?';

                return '<div class="home-artist-card" data-artist-id="' + artist.id + '">' +
                    '<div class="home-artist-card__avatar" style="' + avatarStyle + '">' +
                    (artist.avatarUrl ? '' : initial) +
                    '</div>' +
                    '<h3 class="home-artist-card__name">' + (artist.name || '未知艺术家') + '</h3>' +
                    '<p class="home-artist-card__count">' + (artist.albumCount || 0) + ' 张专辑</p>' +
                    '</div>';
            }).join('');

            this.$grid.html(html);
        },

        showError: function() {
            this.$grid.html('<p class="home-empty">加载失败，请刷新重试</p>');
        }
    };

    // 暴露到全局
    window.ArtistRecommendation = ArtistRecommendation;

    $(document).ready(function() {
        ArtistRecommendation.init();
    });

})(jQuery);
