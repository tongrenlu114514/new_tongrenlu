// 页面切换功能
function switchToPage(pageName) {
    // 隐藏所有页面
    document.getElementById('homePage').style.display = 'none';
    document.getElementById('musicPage').style.display = 'none';
    
    // 显示目标页面
    if (pageName === 'home') {
        document.getElementById('homePage').style.display = 'block';
    } else if (pageName === 'music') {
        document.getElementById('musicPage').style.display = 'flex';
    }
    
    // 更新导航栏样式
    document.querySelectorAll('.nav-link').forEach(link => {
        link.classList.remove('active');
        if (link.getAttribute('data-page') === pageName) {
            link.classList.add('active');
        }
    });
}

// 导航链接点击事件
document.querySelectorAll('.nav-link').forEach(link => {
    link.addEventListener('click', function(e) {
        e.preventDefault();
        const pageName = this.getAttribute('data-page');
        if (pageName) {
            switchToPage(pageName);
        }
    });
});