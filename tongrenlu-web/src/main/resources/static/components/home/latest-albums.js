/**
 * 最新专辑轮播组件
 */
(function($) {
    'use strict';

    const LatestAlbums = {
        $track: null,
        $prevBtn: null,
        $nextBtn: null,
        currentIndex: 0,
        cardWidth: 235, // 220px card + 15px gap
        autoPlayInterval: null,
        isHovered: false,

        init: function() {
            this.$track = $('#latestAlbumsTrack');
            this.$prevBtn = $('.home-carousel__btn--prev');
            this.$nextBtn = $('.home-carousel__btn--next');

            this.bindEvents();
            this.loadAlbums();
        },

        bindEvents: function() {
            const self = this;

            // 导航按钮
            this.$prevBtn.on('click', function() {
                self.prev();
            });

            this.$nextBtn.on('click', function() {
                self.next();
            });

            // 悬停暂停
            $('#latestAlbumsCarousel')
                .on('mouseenter', function() {
                    self.isHovered = true;
                    self.stopAutoPlay();
                })
                .on('mouseleave', function() {
                    self.isHovered = false;
                    self.startAutoPlay();
                });

            // 触摸滑动支持
            this.bindTouchEvents();
        },

        bindTouchEvents: function() {
            let startX = 0;
            let scrollLeft = 0;
            const $track = this.$track;

            $track.on('touchstart', function(e) {
                startX = e.touches[0].pageX - $track.offset().left;
                scrollLeft = $track.scrollLeft();
            });

            $track.on('touchmove', function(e) {
                const x = e.touches[0].pageX - $track.offset().left;
                const walk = (x - startX) * 1.5;
                $track.scrollLeft(scrollLeft - walk);
            });
        },

        loadAlbums: function() {
            const self = this;

            $.ajax({
                url: 'api/music/search',
                data: {
                    pageNumber: 1,
                    pageSize: 15
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
                this.$track.html('<p class="home-empty">暂无专辑</p>');
                return;
            }

            const self = this;
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

            this.$track.html(html);

            // 启动自动播放
            this.startAutoPlay();
        },

        showError: function() {
            this.$track.html('<p class="home-empty">加载失败，请刷新重试</p>');
        },

        next: function() {
            const scrollPosition = this.$track.scrollLeft();
            this.$track.animate({
                scrollLeft: scrollPosition + this.cardWidth * 3
            }, 300);
        },

        prev: function() {
            const scrollPosition = this.$track.scrollLeft();
            this.$track.animate({
                scrollLeft: scrollPosition - this.cardWidth * 3
            }, 300);
        },

        startAutoPlay: function() {
            const self = this;

            if (this.autoPlayInterval) {
                clearInterval(this.autoPlayInterval);
            }

            this.autoPlayInterval = setInterval(function() {
                if (!self.isHovered) {
                    const scrollPosition = self.$track.scrollLeft();
                    const maxScroll = self.$track[0].scrollWidth - self.$track.width();

                    if (scrollPosition >= maxScroll - 10) {
                        // 回到开头
                        self.$track.animate({ scrollLeft: 0 }, 500);
                    } else {
                        self.$track.animate({
                            scrollLeft: scrollPosition + self.cardWidth
                        }, 300);
                    }
                }
            }, 5000);
        },

        stopAutoPlay: function() {
            if (this.autoPlayInterval) {
                clearInterval(this.autoPlayInterval);
                this.autoPlayInterval = null;
            }
        }
    };

    // 暴露到全局
    window.LatestAlbums = LatestAlbums;

    $(document).ready(function() {
        LatestAlbums.init();
    });

})(jQuery);
