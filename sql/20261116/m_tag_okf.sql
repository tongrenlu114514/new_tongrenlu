-- Wiki 词条按 OKF（Open Knowledge Format）规范生成：增加 OKF frontmatter 相关字段。
-- 2026-11-16
-- OKF v0.1（Google Cloud 开放规范）：概念文档 = YAML frontmatter + markdown 正文。
-- frontmatter 字段与现有列的对应：
--   type      → m_tag.type（artist / event / original_game / original_track）
--   title     → m_tag.tag
--   description → m_tag.okf_description（本次新增：LLM 生成的一两句摘要）
--   resource  → m_tag.wiki_source_url
--   tags      → m_tag.wiki_tags（JSON，导出时解析）
--   timestamp → m_tag.wiki_upd_date
-- 需在 MySQL 手工执行；执行后通过 /admin/wiki/regenerate?type=<type> 重新生成即可补齐摘要。
ALTER TABLE m_tag
  ADD COLUMN okf_description VARCHAR(1024) NULL COMMENT 'OKF frontmatter description（LLM 生成的一两句摘要）';
