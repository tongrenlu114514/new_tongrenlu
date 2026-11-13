-- 艺术家 Wiki 内容生成（THBWiki + LLM）
-- 2026-11-14

ALTER TABLE m_artist
  ADD COLUMN thb_wiki_url    VARCHAR(1024) NULL COMMENT 'THBWiki社团页面链接',
  ADD COLUMN thb_wiki_status VARCHAR(16)   NULL DEFAULT 'PENDING' COMMENT 'THBWiki抓取状态：PENDING/MATCHED/NOT_FOUND/FETCH_FAILED',
  ADD COLUMN wiki_content    MEDIUMTEXT    NULL COMMENT 'LLM生成的wiki内容（markdown）',
  ADD COLUMN wiki_tags       VARCHAR(1024) NULL COMMENT '互联标签JSON数组：[{"tag":"...","artistId":1,"type":"artist|style"}]',
  ADD COLUMN wiki_upd_date   DATETIME      NULL COMMENT 'wiki内容更新时间';

ALTER TABLE m_artist
  ADD INDEX idx_m_artist_thb_wiki_status (thb_wiki_status);

-- 存量数据全部置为待处理
UPDATE m_artist SET thb_wiki_status = 'PENDING' WHERE thb_wiki_status IS NULL AND tag_id IS NOT NULL;
