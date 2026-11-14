/**
 * Wiki 页面共享工具（wiki.html 使用；其它页面需要时可直接引入）。
 * 全局函数，依赖 jQuery（$），须在 jquery.min.js 之后、页面逻辑之前引入。
 */

function escapeHtml(s) {
    return $('<div>').text(String(s)).html();
}

// 字段兼容：详情接口返回 camelCase（bean），列表接口返回 snake_case（mapper Map）
function pickField(obj) {
    for (let i = 1; i < arguments.length; i++) {
        const key = arguments[i];
        if (obj && obj[key] !== undefined && obj[key] !== null && obj[key] !== '') {
            return obj[key];
        }
    }
    return null;
}

// 仅允许 http(s) 外链，避免 DB / LLM 内容注入 javascript: 等协议
function safeHttpUrl(url) {
    if (!url || typeof url !== 'string') {
        return null;
    }
    const trimmed = url.trim();
    return /^https?:\/\//i.test(trimmed) ? trimmed : null;
}

// 极简 markdown 渲染：标题 / 粗体 / 列表 / 段落（先转义防 XSS）
function renderMarkdown(text) {
    const escaped = $('<div>').text(text).html();

    const lines = escaped.split(/\r?\n/);
    let html = '';
    let inList = false;
    let paragraph = [];

    const flushParagraph = function() {
        if (paragraph.length > 0) {
            html += '<p>' + paragraph.join('<br>') + '</p>';
            paragraph = [];
        }
    };

    const closeList = function() {
        if (inList) {
            html += '</ul>';
            inList = false;
        }
    };

    // 剥离开头的 "# 简介"（区块标题已承担）
    if (lines.length > 0 && /^#\s*简介\s*$/.test(lines[0].trim())) {
        lines.shift();
    }

    lines.forEach(function(rawLine) {
        const line = rawLine.trim();

        if (line === '') {
            flushParagraph();
            closeList();
            return;
        }

        // 标题
        const h4 = line.match(/^####\s+(.*)$/);
        const h3 = line.match(/^###?\s+(.*)$/);
        if (h4) {
            flushParagraph();
            closeList();
            html += '<h4>' + inline(h4[1]) + '</h4>';
            return;
        }
        if (h3) {
            flushParagraph();
            closeList();
            html += '<h3>' + inline(h3[1]) + '</h3>';
            return;
        }

        // 无序列表
        const li = line.match(/^[-*]\s+(.*)$/);
        if (li) {
            flushParagraph();
            if (!inList) {
                html += '<ul>';
                inList = true;
            }
            html += '<li>' + inline(li[1]) + '</li>';
            return;
        }

        // 普通文本行
        closeList();
        paragraph.push(inline(line));
    });

    flushParagraph();
    closeList();

    return html;

    // 行内格式：**粗体**、*斜体*、`代码`
    function inline(s) {
        return s
            .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
            .replace(/\*([^*]+)\*/g, '<em>$1</em>')
            .replace(/`([^`]+)`/g, '<code>$1</code>');
    }
}
