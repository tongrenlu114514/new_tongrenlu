-- 回填原曲/原作 tag（OriginalTagBackfillJob）时报错：
--   MysqlDataTruncation: Data too long for column 'type' at row 1
-- 原因：m_tag.type 是 varchar(10)，装不下新 tag 类型 original_track（14 字符）/ original_game（13 字符）。
-- 现有值只有 artist / event（各 ≤ 6 字符），无需迁移数据，直接加宽。
-- r_article_tag.type 已是 varchar(20)，不受影响。
-- 与 r_article_tag.type 对齐到 varchar(20)；保持原 NOT NULL DEFAULT '0' 与 utf8mb4_general_ci。
-- 需要 MySQL 服务端执行（本机无 mysql CLI）。
ALTER TABLE tongrenlu.m_tag
  MODIFY COLUMN type VARCHAR(20) NOT NULL DEFAULT '0'
  COMMENT '词条类型：artist / event / original_track / original_game';
