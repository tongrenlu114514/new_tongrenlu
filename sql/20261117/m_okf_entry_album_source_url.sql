-- 修复 album 词条的 source_url：此前误存入网易云封面图 URL（cloud_music_pic_url），
-- 实际应为 THBWiki 词条页链接（m_article.thb_wiki_url）。
-- 封面图不受影响：列表/详情的 cover_url 由 ref join 的 cloud_music_pic_url 单独提供。
-- 幂等：可重复执行。thb_wiki_url 为空或历史哨兵串（NOT_FOUND/FETCH_FAILED）时置 NULL。
UPDATE m_okf_entry e
JOIN m_article a ON e.ref_type = 'm_article' AND e.ref_id = a.id
SET e.source_url = CASE
        WHEN a.thb_wiki_url LIKE 'http://%' OR a.thb_wiki_url LIKE 'https://%' THEN a.thb_wiki_url
        ELSE NULL
    END
WHERE e.type = 'album';
