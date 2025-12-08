/**
 * 全屏播放器初始化模块
 * 播放器变量定义和初始化逻辑
 */
console.log('player-fullscreen-init.js 已加载');

// 播放器变量
const audioPlayer = $('#audioPlayer')[0];
let currentTrackIndex = 0;
let currentMusicData = null;
let currentLyrics = null;
let currentLyricIndex = -1;
let isPlaying = false;
let isShuffle = false;
let repeatMode = 'none';
let isLyricMode = false;

// 获取URL参数
const urlParams = new URLSearchParams(window.location.search);
const albumId = urlParams.get('album');

console.log('Album ID:', albumId);

// 加载音乐数据
function loadMusicData() {
    // 显示加载状态
    $('#albumTitle').html('<span style="color: rgba(255,255,255,0.7);">加载中...</span>');

    if (albumId) {
        // 加载指定专辑
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

                // 将数据存储到全局变量
                window.currentMusicData = albumData;
                console.log('已设置window.currentMusicData:', window.currentMusicData);

                updateAlbumInfo(albumData);
                generatePlaylist(albumData.tracks, $('#playlist'));

                // 显示播放覆盖层
                showPlayOverlay();

                // 播放第一首
                playFirstTrack();
            },
            error: function (error) {
                console.error('加载专辑数据失败:', error);
                $('#albumTitle').text('加载失败');
            }
        });
    } else {
        // 加载随机专辑
        console.log('未找到专辑参数，加载随机专辑');
        loadRandomAlbum('api/music/random', (albumData) => {
            // 已在loadRandomAlbum函数中设置window.currentMusicData
            updateAlbumInfo(albumData);
            generatePlaylist(albumData.tracks, $('#playlist'));

            // 显示播放覆盖层
            showPlayOverlay();

            // 播放第一首
            playFirstTrack();
        });
    }
}

// 播放第一首曲目
function playFirstTrack() {
    console.log('执行playFirstTrack函数');
    console.log('currentMusicData:', window.currentMusicData);
    console.log('tracks:', window.currentMusicData ? window.currentMusicData.tracks : '无数据');
    console.log('tracks length:', window.currentMusicData && window.currentMusicData.tracks ? window.currentMusicData.tracks.length : '无数据');

    if (window.currentMusicData && window.currentMusicData.tracks && window.currentMusicData.tracks.length > 0) {
        console.log('调用playTrack(0)');
        playTrack(0);
    } else {
        console.warn('无法播放第一首曲目：缺少专辑数据或曲目为空');
    }
}

// 将变量和函数附加到全局对象
window.audioPlayer = audioPlayer;
window.currentTrackIndex = currentTrackIndex;
window.currentMusicData = currentMusicData;
window.currentLyrics = currentLyrics;
window.currentLyricIndex = currentLyricIndex;
window.isPlaying = isPlaying;
window.isShuffle = isShuffle;
window.repeatMode = repeatMode;
window.isLyricMode = isLyricMode;
window.albumId = albumId;
window.loadMusicData = loadMusicData;
window.playFirstTrack = playFirstTrack;

// 导出变量和函数
if (typeof module !== 'undefined' && module.exports) {
    module.exports = {
        audioPlayer,
        currentTrackIndex,
        currentMusicData,
        currentLyrics,
        currentLyricIndex,
        isPlaying,
        isShuffle,
        repeatMode,
        isLyricMode,
        albumId,
        loadMusicData,
        playFirstTrack
    };
}