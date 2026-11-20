-- 重置已导入的 album 词条，使它们重新进入 OKF 生成队列（可重新生成）。
--
-- 背景：findGeneratable 只取 content 为空且 status <> 'NO_SOURCE' 的条目，
-- 并按 CASE WHEN type='album' THEN 0 把专辑排到队头。已生成的专辑 content 非空，
-- 不会再被扫描到，因此改了 prompt / 素材（网易云评论）/ 正文格式后需要清空 content 才能重生。
--
-- 本脚本：把全部 album 条目的 content 清空、status 重置为 IMPORTED、upd_date 置空。
-- 不删 description / source_url / sources_json / tags_json —— 这些是生成素材，保留。
-- 幂等：重复执行无副作用。
UPDATE m_okf_entry
SET content   = NULL,
    status    = 'IMPORTED',
    upd_date  = NULL
WHERE type = 'album';

-- 重置后触发重新生成：
--   批量（本地重组、零成本）：POST /admin/okf/generate/albums
--   或调度周期：            POST /admin/okf/generate/trigger
