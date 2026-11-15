-- OKF 导入修复：album 迁移带入 m_article.description（网易云简介全文），
-- 超出 description VARCHAR(1024) 报 Data too long。
-- 扩为 TEXT；OKF frontmatter description 语义仍是摘要，导出时可截取。
-- 需在 MySQL 手工执行；执行后重跑 POST /admin/okf/import 即可续传（幂等）。
ALTER TABLE m_okf_entry
  MODIFY COLUMN description TEXT NULL COMMENT 'OKF description（导入阶段可存全文，导出时截取摘要）';
