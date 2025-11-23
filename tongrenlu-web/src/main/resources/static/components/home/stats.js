// 统计数据和进度条管理
// 同人音乐网站统计数据更新和进度条动画实现

class StatsManager {
    constructor() {
        this.isAnimating = false;
        this.currentProgress = 0;
        this.targetTotal = 0;
        this.targetPublished = 0;
        this.animationDuration = 3000; // 3秒动画时长
        this.progressInterval = null;

        this.init();
    }

    init() {
        // DOM元素 - 使用与HTML中匹配的ID
        this.progressBar = $('#albumProgressBar');
        this.progressText = $('#progressText');
        this.progressInfo = $('#progressInfo');

        // 开始加载统计数据
        this.loadStats();
    }

    async loadStats() {
        try {
            // 模拟从API加载数据
            // 在实际项目中，这里应该调用真实的API端点
            const stats = await this.fetchStatsFromAPI();

            // 开始动画
            this.startAnimation(stats);
        } catch (error) {
            console.error('加载统计数据失败:', error);
            // 使用默认数据
            this.startAnimation({
                total: 200,
                published: 156
            });
        }
    }

    async fetchStatsFromAPI() {
        try {
            const result = await new Promise((resolve, reject) => {
                $.ajax({
                    url: 'api/music/album-stats',
                    method: 'GET',
                    dataType: 'json',
                    success: resolve,
                    error: (xhr, status, error) => {
                        reject(new Error(`HTTP error! status: ${xhr.status}`));
                    }
                });
            });

            // 检查响应是否成功
            if (!result || !result.success) {
                throw new Error(result.message || '获取统计数据失败');
            }

            // 返回统计数据
            if (result.data) {
                return {
                    total: result.data.total || 0,
                    published: result.data.published || 0
                };
            } else {
                throw new Error('返回的数据格式不正确');
            }
        } catch (error) {
            console.error('获取专辑统计数据失败:', error);
            // 如果获取失败，返回默认值
            return {
                total: 0,
                published: 0
            };
        }
    }

    startAnimation(stats) {
        if (this.progressBar.length === 0 || this.progressText.length === 0 || this.progressInfo.length === 0) {
            console.error('DOM elements not found');
            return;
        }

        // 设置目标值
        this.targetTotal = stats.total;
        this.targetPublished = stats.published;

        // 开始数值动画
        if (!this.isAnimating) {
            this.isAnimating = true;
            this.animateValues();
            this.animateProgressBar();
        }
    }

    animateValues() {
        const duration = this.animationDuration;
        const steps = 60; // 60步，每50ms一步
        const stepDuration = duration / steps;
        let currentStep = 0;

        const animate = () => {
            currentStep++;
            const progress = currentStep / steps;

            // 使用缓动函数使动画更平滑
            const easeProgress = this.easeOutQuad(progress);

            // 计算当前值
            const currentPublished = Math.floor(this.targetPublished * easeProgress);
            const percentage = this.targetTotal > 0 ? Math.round((currentPublished / this.targetTotal) * 100) : 0;

            // 更新UI
            this.progressText.text(`${percentage}%`);
            this.progressInfo.text(`专辑发布进度：已发布 ${currentPublished} / ${this.targetTotal} 张`);

            if (currentStep < steps) {
                setTimeout(animate, stepDuration);
            } else {
                // 确保最终显示的是准确值
                const finalPercentage = this.targetTotal > 0 ? Math.round((this.targetPublished / this.targetTotal) * 100) : 0;
                this.progressText.text(`${finalPercentage}%`);
                this.progressInfo.text(`专辑发布进度：已发布 ${this.targetPublished} / ${this.targetTotal} 张`);
                this.isAnimating = false;
            }
        };

        animate();
    }

    animateProgressBar() {
        let progress = 0;
        const increment = 100 / (this.animationDuration / 50); // 每50ms更新一次

        this.progressInterval = setInterval(() => {
            progress += increment;

            if (progress >= 100) {
                progress = 100;
                clearInterval(this.progressInterval);
                this.progressInterval = null;
            }

            // 更新进度条
            if (this.progressBar.length > 0) {
                this.progressBar.css('width', progress + '%');

                // 根据百分比设置不同的颜色
                const percentage = Math.floor(progress);
                if (percentage < 30) {
                    this.progressBar.css('background', '#e74c3c'); // 红色
                } else if (percentage < 70) {
                    this.progressBar.css('background', '#f39c12'); // 橙色
                } else if (percentage < 90) {
                    this.progressBar.css('background', '#3498db'); // 蓝色
                } else {
                    this.progressBar.css('background', '#27ae60'); // 绿色
                }
            }
        }, 50);
    }

    // 缓动函数：二次缓出
    easeOutQuad(t) {
        return t * (2 - t);
    }

    // 重置动画
    reset() {
        if (this.progressInterval) {
            clearInterval(this.progressInterval);
            this.progressInterval = null;
        }

        this.isAnimating = false;
        this.currentProgress = 0;

        if (this.progressBar.length > 0) {
            this.progressBar.css({
                'width': '0%',
                'background': '#3498db'
            });
        }

        if (this.progressText.length > 0) {
            this.progressText.text('0%');
        }

        if (this.progressInfo.length > 0) {
            this.progressInfo.text('专辑发布进度：已发布 0 / 0 张');
        }
    }

    // 公共方法：更新统计数据
    updateStats(newStats) {
        this.reset();
        this.startAnimation(newStats);
    }

    /**
     * 刷新统计信息
     */
    refresh() {
        this.reset();
        this.loadStats();
    }
}

// 创建全局实例
let statsManager;

// 页面加载完成后初始化
$(function () {
    // 等待页面完全加载后再启动统计动画
    setTimeout(() => {
        statsManager = new StatsManager();
    }, 500);

    // 可选：每30秒自动刷新一次数据
    setInterval(() => {
        if (statsManager) {
            statsManager.refresh();
        }
    }, 30000);
});

// 当从其他页面切换到首页时重新触发动画
$(document).on('pageSwitchComplete', function (e, data) {
    // 兼容不同的事件数据格式
    const page = e.originalEvent && e.originalEvent.detail ? e.originalEvent.detail.page : (data ? data.page : null);
    if (page === 'home' && statsManager) {
        // 重置并重新播放动画
        setTimeout(() => {
            statsManager.refresh();
        }, 300);
    }
});

// 导出类供外部调用（如果需要）
if (typeof module !== 'undefined' && module.exports) {
    module.exports = StatsManager;
}