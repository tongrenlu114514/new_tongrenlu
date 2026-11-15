-- OKF 阶段 1：OKF 概念表（与 m_tag 并行的词条库）
-- 2026-11-16
-- 六类概念：original_game(原作游戏) / game_track(游戏原曲) / cd_track(官方CD原曲) /
--           artist(艺术家) / event(展会) / album(专辑)
-- 导入入口：POST /admin/okf/import[?type=<type>]（THBWiki 解析 + 本站迁移，幂等可重跑）
-- 需在 MySQL 手工执行。

CREATE TABLE m_okf_entry (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  type         VARCHAR(20)  NOT NULL COMMENT 'original_game/game_track/cd_track/artist/event/album',
  name         VARCHAR(255) NOT NULL COMMENT '概念名（OKF title）',
  game         VARCHAR(255) NULL COMMENT '所属作品/CD（game_track、cd_track 用）',
  description  VARCHAR(1024) NULL COMMENT 'OKF description（LLM 摘要，导入阶段存索引行信息）',
  content      MEDIUMTEXT NULL COMMENT 'markdown 正文（迁移类原样带入 m_tag.wiki_content）',
  tags_json    VARCHAR(1024) NULL COMMENT 'OKF tags（沿用 wiki_tags JSON 结构）',
  source_url   VARCHAR(1024) NULL COMMENT 'OKF resource：THBWiki 词条页 URL / 素材页',
  sources_json VARCHAR(2048) NULL COMMENT 'OKF v0.2 sources 列表 JSON（如 artist 的 THBWiki 社团页 + 网易云艺人描述页）',
  status       VARCHAR(16) NOT NULL DEFAULT 'IMPORTED' COMMENT 'IMPORTED/GENERATED/FAILED',
  ref_type     VARCHAR(16) NULL COMMENT '关联本站实体类型：m_artist/m_tag/m_article',
  ref_id       BIGINT NULL COMMENT '关联本站实体 id',
  upd_date     DATETIME NULL,
  UNIQUE KEY uk_okf_type_name (type, name),
  KEY idx_okf_type_status (type, status),
  KEY idx_okf_ref (ref_type, ref_id)
) COMMENT 'OKF 概念表：与 m_tag 并行的词条库';

-- 原始数据表：来源页上的每条原始记录（如原曲列表页里某作品表的一行、CD 曲目列表里
-- 某张 CD 的一首曲目、本站迁移的一条实体字段）。url 为「来源页内条目链接」的唯一
-- 标识，幂等导入按 url 查重。raw 与 entry 是 M:N：一条 raw 可派生多个概念（曲目 +
-- 其所属 CD 等），一个概念也可由多条 raw 支撑（同名曲的多个来源行）。
CREATE TABLE m_okf_raw (
  id        BIGINT AUTO_INCREMENT PRIMARY KEY,
  source    VARCHAR(32) NOT NULL COMMENT '来源标识：tracklist / cd_tracklist / local',
  url       VARCHAR(1024) NOT NULL COMMENT '来源页内条目链接（唯一标识）',
  raw_json  MEDIUMTEXT NOT NULL COMMENT '结构化原始字段（曲号/日英文名/时长/labels 等）',
  upd_date  DATETIME NULL,
  UNIQUE KEY uk_okf_raw_url (url),
  KEY idx_okf_raw_source (source)
) COMMENT 'OKF 原始数据表：来源观察记录，M:N 关联概念';

-- raw ↔ entry 多对多桥表（一个 raw 可派生多个 entry，一个 entry 可由多条 raw 支撑）
CREATE TABLE r_okf_raw_entry (
  id        BIGINT AUTO_INCREMENT PRIMARY KEY,
  raw_id    BIGINT NOT NULL,
  entry_id  BIGINT NOT NULL,
  upd_date  DATETIME NULL,
  UNIQUE KEY uk_okf_raw_entry (raw_id, entry_id),
  KEY idx_okf_re_entry (entry_id)
) COMMENT 'OKF raw↔entry 多对多桥表';
