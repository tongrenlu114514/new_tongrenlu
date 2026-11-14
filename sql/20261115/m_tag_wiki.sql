-- Wiki 词条：以 m_tag 为词条主体，合并艺术家 Wiki，新增 event 类型词条
-- 2026-11-15
-- 说明：本机无 mysql CLI，本文件需在 MySQL 手工执行一次。
--       ALTER 段不可重复执行；下方 UPDATE 段幂等（只填仍为空的行），可单独重跑。
--       m_artist 的 wiki_* 列保留为历史数据，自此不再写入，存量 PENDING 行也不再被处理。

ALTER TABLE m_tag
  ADD COLUMN wiki_content    MEDIUMTEXT   NULL COMMENT 'LLM 生成的 wiki 内容（markdown）',
  ADD COLUMN wiki_tags       VARCHAR(1024) NULL COMMENT '互联标签 JSON 数组：[{"tag":"...","tagId":1,"artistId":1,"type":"artist|event|style"}]',
  ADD COLUMN wiki_source_url VARCHAR(1024) NULL COMMENT 'THBWiki 社团页面链接（artist 词条）',
  ADD COLUMN wiki_status     VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT '词条生成状态：PENDING/GENERATED/NO_SOURCE/FAILED',
  ADD COLUMN wiki_upd_date   DATETIME      NULL COMMENT '词条内容更新时间';

ALTER TABLE m_tag
  ADD INDEX idx_m_tag_type_wiki_status (type, wiki_status);

-- 把 m_artist 已处理过的艺术家 wiki 迁到对应 m_tag 词条（tag_id 关联）。
-- 状态必须翻译：旧值是 ThbWikiStatus(PENDING/MATCHED/NOT_FOUND/FETCH_FAILED)，
-- 新列用 WikiStatus(PENDING/GENERATED/NO_SOURCE/FAILED)，直接拷贝会让 valueOf 抛异常。
UPDATE m_tag t
JOIN m_artist a ON a.tag_id = t.id
SET t.wiki_content    = a.wiki_content,
    t.wiki_tags       = a.wiki_tags,
    t.wiki_source_url = a.thb_wiki_url,
    t.wiki_upd_date   = a.wiki_upd_date,
    t.wiki_status     = CASE
        WHEN a.wiki_content IS NOT NULL AND a.wiki_content <> '' THEN 'GENERATED'
        WHEN a.thb_wiki_status = 'NOT_FOUND' THEN 'NO_SOURCE'
        WHEN a.thb_wiki_status IN ('FETCH_FAILED', 'MATCHED') THEN 'FAILED'
        ELSE 'PENDING'
    END
WHERE (t.wiki_content IS NULL OR t.wiki_content = '')
  AND (a.wiki_content IS NOT NULL
       OR a.thb_wiki_status IN ('MATCHED', 'NOT_FOUND', 'FETCH_FAILED'));

-- 其余 m_tag（含全部 event）由 wiki_status 默认值置为 PENDING，等待统一生成任务处理。
