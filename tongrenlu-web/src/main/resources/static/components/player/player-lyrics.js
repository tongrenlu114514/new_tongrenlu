// 解析LRC格式歌词
function parseLrc(lrcString) {
    if (!lrcString || typeof lrcString !== 'string') {
        return null;
    }
    const timeRegex = /\[(\d{1,2}):(\d{1,2})[:.](\d{2,3})]/g;
    const lines = lrcString.split('\n');
    const lyricLines = [];
    lines.forEach(line => {
        line = line.trim();
        if (!line) return;
        const matches = [...line.matchAll(timeRegex)];
        if (matches.length > 0) {
            let text = line;
            matches.forEach(match => {
                text = text.replace(match[0], '');
            });
            text = text.trim();
            matches.forEach(match => {
                const minutes = parseInt(match[1], 10);
                const seconds = parseInt(match[2], 10);

                let milliseconds;
                const milli = match[3];
                if (milli.length === 2) {
                    milliseconds = parseInt(milli , 10) * 10;
                } else {
                    milliseconds = parseInt(milli , 10);
                }

                // const milliseconds = parseInt(match[3].length == 2 ? , 10) * 10;
                const time = minutes * 60 + seconds + milliseconds / 1000;
                if (text) {
                    lyricLines.push({ time: time, text: text });
                }
            });
        }
    });
    if (lyricLines.length === 0) {
        return null;
    }
    lyricLines.sort((a, b) => a.time - b.time);
    return { type: 'lrc', lines: lyricLines };
}
// 加载歌词
function loadLyrics(trackIdOrLyric) {
    const $lyrics = $('#lyrics');
    $lyrics.empty();
    $lyrics.html('<div class="lyric-line no-lyrics">加载歌词中...</div>');
    if (typeof trackIdOrLyric === 'string' && trackIdOrLyric.trim()) {
        window.currentLyrics = parseLrc(trackIdOrLyric) || trackIdOrLyric;
    } else {
        window.currentLyrics = trackIdOrLyric;
    }
    displayLyrics(window.currentLyrics);
    scrollToTop();
}

// 显示歌词
function displayLyrics(lyrics) {
    const $lyrics = $('#lyrics');
    $lyrics.empty();
    if (!lyrics || (typeof lyrics === 'object' && (!lyrics.lines || lyrics.lines.length === 0))) {
        $lyrics.html('<div class="lyric-line no-lyrics">暂无歌词</div>');
        return;
    }
    if (typeof lyrics === 'object' && lyrics.type === 'lrc' && lyrics.lines) {
        lyrics.lines.forEach((line, index) => {
            const $line = $(`<div class="lyric-line" data-time="${line.time}" data-index="${index}">${line.text}</div>`);
            $lyrics.append($line);
        });
    } else if (Array.isArray(lyrics)) {
        lyrics.forEach((line, index) => {
            const $line = $(`<div class="lyric-line" data-index="${index}">${line}</div>`);
            $lyrics.append($line);
        });
    } else if (typeof lyrics === 'string') {
        const lines = lyrics.split('\n');
        lines.forEach((line, index) => {
            const $line = $(`<div class="lyric-line" data-index="${index}">${line}</div>`);
            $lyrics.append($line);
        });
    } else if (typeof lyrics === 'object' && lyrics.lines && Array.isArray(lyrics.lines)) {
        lyrics.lines.forEach((line, index) => {
            const text = typeof line === 'object' ? line.text || line.content || line : line;
            const $line = $(`<div class="lyric-line" data-index="${index}">${text}</div>`);
            $lyrics.append($line);
        });
    } else {
        $lyrics.html('<div class="lyric-line no-lyrics">暂无歌词</div>');
    }
    currentLyricIndex = -1;
}

// 滚动到顶部
function scrollToTop() {
    const $lyricsContainer = $('#lyricsContainer');
    if ($lyricsContainer.length > 0) {
        $lyricsContainer.stop().animate({ scrollTop: 0 }, 300);
    }
}

// 更新歌词高亮，根据当前播放时间滚动到对应歌词行
function updateLyricsHighlight(currentTime) {
    if (!window.currentLyrics) {
        return;
    }

    let newIndex = -1;

    // 1. 如果是LRC格式歌词（带时间戳）
    if (typeof window.currentLyrics === 'object' && window.currentLyrics.type === 'lrc' && window.currentLyrics.lines) {
        const lyricsLines = window.currentLyrics.lines;

        // 查找当前时间对应的歌词行
        for (let i = 0; i < lyricsLines.length; i++) {
            const currentLineTime = lyricsLines[i].time;
            const nextLineTime = i + 1 < lyricsLines.length ? lyricsLines[i + 1].time : Infinity;

            // 如果当前时间在这行歌词的时间范围内
            if (currentTime >= currentLineTime && currentTime < nextLineTime) {
                newIndex = i;
                break;
            }
        }

        // 如果播放时间超过了最后一行歌词的时间，高亮最后一行
        if (newIndex === -1 && lyricsLines.length > 0 && currentTime >= lyricsLines[lyricsLines.length - 1].time) {
            newIndex = lyricsLines.length - 1;
        }
    }
    // 2. 如果是纯文本歌词（没有时间戳），按平均时间估算
    else if (typeof window.currentLyrics === 'string' && window.currentLyrics.trim()) {
        const lines = window.currentLyrics.split('\n').filter(line => line.trim());
        if (lines.length === 0) return;

        // 确保音频播放器可用
        if (!window.audioPlayer || !window.audioPlayer.duration) return;

        const totalDuration = window.audioPlayer.duration;
        const avgDurationPerLine = totalDuration / lines.length;
        newIndex = Math.min(Math.floor(currentTime / avgDurationPerLine), lines.length - 1);
    }
    // 3. 如果是纯文本数组
    else if (Array.isArray(window.currentLyrics) && window.currentLyrics.length > 0) {
        if (!window.audioPlayer || !window.audioPlayer.duration) return;

        const totalDuration = window.audioPlayer.duration;
        const avgDurationPerLine = totalDuration / window.currentLyrics.length;
        newIndex = Math.min(Math.floor(currentTime / avgDurationPerLine), window.currentLyrics.length - 1);
    }

    // 如果高亮行没有变化，不执行任何操作
    if (newIndex === currentLyricIndex || newIndex === -1) return;

    console.log('歌词高亮变化:', currentLyricIndex, '→', newIndex, '时间:', currentTime.toFixed(2));
    currentLyricIndex = newIndex;

    // 移除所有高亮
    $('.lyric-line').removeClass('active');

    // 高亮当前行
    const $currentLine = $(`.lyric-line[data-index="${newIndex}"]`);
    if ($currentLine.length > 0) {
        $currentLine.addClass('active');

        // 滚动到当前行
        setTimeout(() => scrollLyricsToCurrent(), 50);
    }
}

// 滚动歌词到当前行
function scrollLyricsToCurrent() {
    if (currentLyricIndex < 0) return;

    const $currentLine = $(`.lyric-line[data-index="${currentLyricIndex}"]`);
    const $lyricsContainer = $('#lyricsContainer');

    if ($currentLine.length > 0 && $lyricsContainer.length > 0) {
        console.log('滚动到歌词行:', currentLyricIndex, '元素位置:', $currentLine.position());

        // 简单的居中滚动实现
        const containerHeight = $lyricsContainer.height();
        const lineOffsetTop = $currentLine.position().top;
        const scrollTop = $lyricsContainer.scrollTop();

        // 计算目标滚动位置（行居中）
        const targetScroll = scrollTop + lineOffsetTop - (containerHeight / 2) + ($currentLine.height() / 2);

        console.log('滚动参数:', { containerHeight, lineOffsetTop, scrollTop, targetScroll });

        // 平滑滚动到目标位置
        $lyricsContainer.stop().animate({
            scrollTop: targetScroll
        }, 300, function() {
            console.log('滚动完成');
        });
    }
}

// 导出函数（如果使用ES6模块）
if (typeof module !== 'undefined' && module.exports) {
    module.exports = {
        loadLyrics,
        displayLyrics,
        updateLyricsHighlight,
        scrollLyricsToCurrent,
        scrollToTop,
        parseLrc
    };
}