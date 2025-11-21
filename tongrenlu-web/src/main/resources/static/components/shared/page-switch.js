// 页面切换功能
function switchToPage(pageName) {
    // 隐藏所有页面
    $('#homePage').hide();
    $('#musicPage').hide();

    // 显示目标页面
    if (pageName === 'home') {
        $('#homePage').show();
    } else if (pageName === 'music') {
        $('#musicPage').css('display', 'flex');
    }

    // 更新导航栏样式
    $('.nav-link').removeClass('active');
    $(`.nav-link[data-page="${pageName}"]`).addClass('active');
}

$(function () {
    // 导航链接点击事件
    $('.nav-link').on('click', function (e) {
        e.preventDefault();
        const pageName = $(this).data('page');
        if (pageName) {
            switchToPage(pageName);
        }
    });
});