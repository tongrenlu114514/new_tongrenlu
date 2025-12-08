/**
 * 数据加载模块
 * 负责专辑和音频数据的加载
 */

// 加载随机专辑数据
function loadRandomAlbum(apiEndpoint, callback) {
    console.log('开始加载随机专辑数据...');
    // 显示加载状态
    $('#albumTitle').html('<span style="color: rgba(255,255,255,0.7);">加载随机专辑...</span>');

    $.ajax({
        url: apiEndpoint,
        method: 'GET',
        dataType: 'json',
        success: function (albumData) {
            console.log('随机专辑数据加载成功:', albumData);

            // 检查数据完整性
            if (!albumData) {
                console.error('加载的专辑数据为空');
                $('#albumTitle').text('专辑数据为空');
                return;
            }

            if (!albumData.tracks) {
                console.error('专辑数据缺少tracks字段:', albumData);
                $('#albumTitle').text('专辑格式错误');
                return;
            }

            if (albumData.tracks.length === 0) {
                console.error('专辑曲目为空');
                $('#albumTitle').text('没有找到专辑');
                return;
            }

            // 将数据存储到全局变量
            window.currentMusicData = albumData;
            console.log('已设置window.currentMusicData:', window.currentMusicData);

            if (callback) {
                callback(albumData);
            } else {

                // 显示播放覆盖层
                showPlayOverlay();

                // 播放第一首
                // playFirstTrack();
            }
        },
        error: function (error) {
            console.error('加载随机专辑数据失败:', error);
            $('#albumTitle').text('加载随机专辑失败');
        }
    });
}

// 加载指定专辑数据
function loadAlbumDetail(albumId, callback) {
    // 显示加载状态
    $('#albumTitle').html('<span style="color: rgba(255,255,255,0.7);">加载中...</span>');

    // 如果有album参数，加载指定专辑
    $.ajax({
        url: `api/music/detail?albumId=${albumId}`,
        method: 'GET',
        dataType: 'json',
        success: function (albumData) {
            console.log('专辑数据加载成功:', albumData);

            if (!albumData || !albumData.tracks || albumData.tracks.length === 0) {
                $('#albumTitle').text('专辑为空');
                return;
            }

            if (callback) {
                callback(albumData);
            }

            // 显示播放覆盖层
            showPlayOverlay();

            // 播放第一首
            //playTrack(0);
        },
        error: function (error) {
            console.error('加载专辑数据失败:', error);
            $('#albumTitle').text('加载失败');
        }
    });
}

// 生成播放列表
function generatePlaylist(tracks, playlistContainer) {
    const $playlist = playlistContainer;
    $playlist.empty();

    tracks.forEach((track, index) => {
        const $trackItem = $(`
            <li class="playlist-item ${index === 0 ? 'active' : ''}" data-index="${index}">
                <span class="track-number">${(index + 1).toString().padStart(2, '0')}</span>
                <div class="track-info">
                    <div class="track-name">
                        <i class="fas fa-music icon"></i>
                        ${track.name || '未命名曲目'}
                    </div>
                    <div class="track-duration">${track.duration ? formatTime(track.duration) : '未知'}</div>
                </div>
            </li>
        `);

        // 点击播放
        $trackItem.click(function () {
            const index = $(this).data('index');
            playTrack(index);
        });

        $playlist.append($trackItem);
    });

    console.log('播放列表生成完成，共', tracks.length, '首曲目');
}

// 获取音频URL (需要audioPlayer, track)
function loadTrackUrl(track) {
    return new Promise((resolve, reject) => {
        // 显示加载状态
        $('#albumTitle').html(`${track.name || '加载中...'}<span style="color: rgba(255,255,255,0.7);"> (加载中)</span>`);

        // 调用API获取音频URL
        $.ajax({
            url: `api/music/track?id=${track.id}`,
            method: 'GET',
            dataType: 'json',
            success: function (data) {
                console.log('音频数据:', data);

                if (data && data.url) {
                    resolve(data);
                } else {
                    reject(new Error('未找到音频URL'));
                }
            },
            error: function (error) {
                console.error('获取音频URL失败:', error);
                reject(new Error('获取音频资源失败'));
            }
        });
    });
}

// 导出函数（如果使用ES6模块）
if (typeof module !== 'undefined' && module.exports) {
    module.exports = {
        loadRandomAlbum,
        loadAlbumDetail,
        generatePlaylist,
        loadTrackUrl
    };
}
