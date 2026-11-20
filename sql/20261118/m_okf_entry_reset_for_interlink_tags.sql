-- 让已生成的 OKF 条目重新生成，以补齐详情页的「互联标签」(tags_json)。
--
-- 背景：此前 OkfGenerateService 生成时没有把 LLM 输出的 tags 写入 m_okf_entry.tags_json，
-- 已生成条目详情页的「互联标签」区为空。修复后仅新生成 / 强制重生的条目会写入标签。
-- findGeneratable 只取 content 为空的条目，因此存量条目需清空 content 才会重新进入队列。
--
-- 注意：重新生成会调用 LLM，有成本。按需要选择范围，执行后按需触发生成。
-- 幂等：重复执行无副作用。

-- 方案 A（推荐，成本较低）：仅非 album 词条（artist/event/original_game/game_track/cd_track）
UPDATE m_okf_entry
SET content   = NULL,
    status    = 'IMPORTED',
    upd_date  = NULL,
    tags_json = NULL
WHERE status = 'GENERATED'
  AND type <> 'album';

-- 方案 B（成本高）：连 album 一起重生成（上万张专辑），取消注释后再执行
-- UPDATE m_okf_entry
-- SET content   = NULL,
--     status    = 'IMPORTED',
--     upd_date  = NULL,
--     tags_json = NULL
-- WHERE status = 'GENERATED'
--   AND type = 'album';

-- 重置后触发生成：
--   通用调度：   POST /admin/okf/generate/trigger
--   仅专辑批量： POST /admin/okf/generate/albums
--   单条立即试： POST /admin/okf/generate/force?id=<id>
