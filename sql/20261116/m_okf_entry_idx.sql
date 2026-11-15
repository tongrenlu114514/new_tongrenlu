-- OKF 导入修复：重名实体导入冲突
-- 2026-11-16
-- 现象：m_artist 存在重名记录（如两条 'Agent 0'），artist 迁移按 (type, name) 查重后
-- 第二条不再 insert，但 uk_okf_type_name 唯一键仍会在「同名 album 多条」场景拒绝插入
-- （本站专辑同名合法，album 必须按 ref 区分、允许同名多条）。
-- 处理：唯一键降级为普通索引——同名合并语义由导入逻辑保证（THBWiki 类 / artist 按
-- (type, name) 查重合并，album 按 ref 区分），不在 DB 层强制。
-- 需在 MySQL 手工执行；重跑 POST /admin/okf/import 即可续传（幂等）。

ALTER TABLE m_okf_entry DROP INDEX uk_okf_type_name;
ALTER TABLE m_okf_entry ADD INDEX idx_okf_type_name (type, name);
