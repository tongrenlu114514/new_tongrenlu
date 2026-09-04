-- ============================================================================
-- 2026-09-04 清理 m_article.cloud_music_id 重复数据 + 增加唯一索引
--
-- 问题背景：
--   导入歌单/专辑时 ArticleService.getByCloudMusicId 使用 selectOne，
--   当 m_article 存在多条相同 cloud_music_id 的记录时抛
--   TooManyResultsException("Expected one result ... but found: 2")，
--   导致 /playlist/import 中对应专辑导入失败。
--   已受影响的网易云专辑ID：71573(C80)、82801003(C78)、2076220(C73)、72428(例大祭4)。
--
-- 本脚本目标：
--   1) 找出 m_article 中 cloud_music_id 的所有重复组（不只上面 4 个）；
--   2) 每组保留"数据最完整"的一行（优先 del_flg='0'，其次曲目数最多、
--      再次标签数最多、最后取最小 id）；
--   3) 把被删行的 m_track / r_article_tag(type='m_article') 迁移/合并到保留行；
--   4) 删除多余 m_article 行；
--   5) 对 cloud_music_id 增加唯一索引，防止复发。
--
-- 使用方式（务必在能连上 121.37.183.44 数据库且有写权限的客户端执行）：
--   mysql -h 121.37.183.44 -u deepseek -p tongrenlu < 本文件
--   或分段执行：先跑 STEP 0 诊断确认，再跑 STEP 1-4，最后确认后单独跑 STEP 5。
--
-- 说明：
--   - STEP 1 会把受影响行备份到 bak_20260904_* 表，误删可回滚；
--   - STEP 2-4 在事务内执行，出错可 ROLLBACK；
--   - STEP 5 是 DDL(隐式提交)，请核对无误后再单独执行。
--   - 若某组希望保留的行与自动选择不同，可先手工 DELETE 掉不想要的行，
--     使该组只剩一行后脚本对该组自动跳过(视为无重复)。
-- ============================================================================


-- ============================================================================
-- STEP 0 (只读) 诊断：查看所有重复组概览
-- ============================================================================
SELECT cloud_music_id,
       COUNT(*)                                              AS dup_cnt,
       GROUP_CONCAT(id ORDER BY id)                          AS article_ids,
       GROUP_CONCAT(del_flg ORDER BY id)                     AS del_flgs,
       GROUP_CONCAT(publish_flg ORDER BY id)                 AS publish_flgs
FROM tongrenlu.m_article
WHERE cloud_music_id IS NOT NULL
GROUP BY cloud_music_id
HAVING COUNT(*) > 1
ORDER BY dup_cnt DESC, cloud_music_id;


-- STEP 0.1 (只读) 诊断：重复组逐行明细（含曲目/标签数，便于核对保留策略）
SELECT a.cloud_music_id,
       a.id                                                 AS article_id,
       a.del_flg,
       a.publish_flg,
       a.title,
       a.upd_date,
       (SELECT COUNT(*) FROM tongrenlu.m_track t
         WHERE t.article_id = a.id)                         AS track_cnt,
       (SELECT COUNT(*) FROM tongrenlu.r_article_tag rt
         WHERE rt.article_id = a.id AND rt.type = 'm_article') AS tag_cnt
FROM tongrenlu.m_article a
WHERE a.cloud_music_id IN (
        SELECT cloud_music_id
        FROM tongrenlu.m_article
        WHERE cloud_music_id IS NOT NULL
        GROUP BY cloud_music_id
        HAVING COUNT(*) > 1
      )
ORDER BY a.cloud_music_id, a.id;


-- ============================================================================
-- STEP 1 备份受影响数据（m_article / m_track / r_article_tag 中相关行）
-- ============================================================================
DROP TABLE IF EXISTS tongrenlu.bak_20260904_m_article_dup;
CREATE TABLE tongrenlu.bak_20260904_m_article_dup AS
SELECT a.*
FROM tongrenlu.m_article a
JOIN (
    SELECT cloud_music_id
    FROM tongrenlu.m_article
    WHERE cloud_music_id IS NOT NULL
    GROUP BY cloud_music_id
    HAVING COUNT(*) > 1
) d ON a.cloud_music_id = d.cloud_music_id;

DROP TABLE IF EXISTS tongrenlu.bak_20260904_m_track_dup;
CREATE TABLE tongrenlu.bak_20260904_m_track_dup AS
SELECT t.*
FROM tongrenlu.m_track t
JOIN tongrenlu.bak_20260904_m_article_dup a ON t.article_id = a.id;

DROP TABLE IF EXISTS tongrenlu.bak_20260904_r_article_tag_dup;
CREATE TABLE tongrenlu.bak_20260904_r_article_tag_dup AS
SELECT rt.*
FROM tongrenlu.r_article_tag rt
JOIN tongrenlu.bak_20260904_m_article_dup a ON rt.article_id = a.id
WHERE rt.type = 'm_article';


-- ============================================================================
-- STEP 2-4 事务：合并子表数据并删除重复 m_article 行
-- ============================================================================
START TRANSACTION;

-- STEP 2 每组选出一个保留行 keep_id：
--        优先 del_flg='0'，其次曲目数多，再次标签数多，最后取 id 最小
DROP TEMPORARY TABLE IF EXISTS tmp_article_dup_keep;
CREATE TEMPORARY TABLE tmp_article_dup_keep AS
SELECT d.cloud_music_id,
       (SELECT a2.id
        FROM tongrenlu.m_article a2
        LEFT JOIN tongrenlu.m_track t2       ON t2.article_id = a2.id
        LEFT JOIN tongrenlu.r_article_tag rt2 ON rt2.article_id = a2.id AND rt2.type = 'm_article'
        WHERE a2.cloud_music_id = d.cloud_music_id
        GROUP BY a2.id
        ORDER BY a2.del_flg ASC,
                 COUNT(t2.id) DESC,
                 COUNT(DISTINCT rt2.tag_id) DESC,
                 a2.id ASC
        LIMIT 1) AS keep_id
FROM (
    SELECT cloud_music_id
    FROM tongrenlu.m_article
    WHERE cloud_music_id IS NOT NULL
    GROUP BY cloud_music_id
    HAVING COUNT(*) > 1
) d;

-- STEP 3.1 删除"被删行"中与保留行完全重复的曲目
--         判定：cloud_music_id 相同；或均无 cloud_music_id 时 disc/track_number/name 相同
DELETE t
FROM tongrenlu.m_track t
JOIN tongrenlu.m_article del   ON t.article_id = del.id
JOIN tmp_article_dup_keep k    ON k.cloud_music_id = del.cloud_music_id
                              AND k.keep_id <> del.id
JOIN tongrenlu.m_track kt      ON kt.article_id = k.keep_id
                              AND ( (t.cloud_music_id IS NOT NULL AND kt.cloud_music_id = t.cloud_music_id)
                                    OR (t.cloud_music_id IS NULL AND kt.cloud_music_id IS NULL
                                        AND (kt.disc = t.disc OR (kt.disc IS NULL AND t.disc IS NULL))
                                        AND kt.track_number = t.track_number
                                        AND kt.name = t.name) );

-- STEP 3.2 其余曲目迁移到保留行
UPDATE tongrenlu.m_track t
JOIN tongrenlu.m_article del ON t.article_id = del.id
JOIN tmp_article_dup_keep k  ON k.cloud_music_id = del.cloud_music_id
                            AND k.keep_id <> del.id
SET t.article_id = k.keep_id;

-- STEP 3.3 重建保留行的 m_article 标签(type='m_article')：取整组合并后的去重集合
DELETE rt
FROM tongrenlu.r_article_tag rt
JOIN tmp_article_dup_keep k ON rt.article_id = k.keep_id AND rt.type = 'm_article';

INSERT INTO tongrenlu.r_article_tag (article_id, tag_id, upd_date, del_flg, type)
SELECT DISTINCT k.keep_id, rt.tag_id, NOW(), '0', 'm_article'
FROM tongrenlu.r_article_tag rt
JOIN tongrenlu.m_article a ON rt.article_id = a.id
JOIN tmp_article_dup_keep k ON k.cloud_music_id = a.cloud_music_id
WHERE rt.type = 'm_article';

-- STEP 3.4 删除被删行的标签关系
DELETE rt
FROM tongrenlu.r_article_tag rt
JOIN tongrenlu.m_article del ON rt.article_id = del.id
JOIN tmp_article_dup_keep k  ON k.cloud_music_id = del.cloud_music_id
                            AND k.keep_id <> del.id
WHERE rt.type = 'm_article';

-- STEP 4 删除多余的 m_article 行
DELETE a
FROM tongrenlu.m_article a
JOIN tmp_article_dup_keep k ON k.cloud_music_id = a.cloud_music_id
                           AND k.keep_id <> a.id;

DROP TEMPORARY TABLE IF EXISTS tmp_article_dup_keep;

COMMIT;


-- ============================================================================
-- STEP 5 (单独执行，DDL 隐式提交) 增加唯一索引防止复发
--        执行前请确认 STEP 0 已无输出（无重复）或已按上面步骤清理完毕。
-- ============================================================================
-- ALTER TABLE tongrenlu.m_article
--     ADD UNIQUE INDEX uk_m_article_cloud_music_id (cloud_music_id);


-- ============================================================================
-- 收尾验证（可选）：应无输出；备份表 bak_20260904_* 确认无误后可删除
-- ============================================================================
-- SELECT cloud_music_id FROM tongrenlu.m_article
-- WHERE cloud_music_id IS NOT NULL
-- GROUP BY cloud_music_id HAVING COUNT(*) > 1;
