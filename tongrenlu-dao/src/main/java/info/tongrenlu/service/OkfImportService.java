package info.tongrenlu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import info.tongrenlu.domain.ArticleBean;
import info.tongrenlu.domain.ArtistBean;
import info.tongrenlu.domain.OkfEntryBean;
import info.tongrenlu.domain.OkfRawBean;
import info.tongrenlu.domain.TagBean;
import info.tongrenlu.mapper.ArticleMapper;
import info.tongrenlu.mapper.ArtistMapper;
import info.tongrenlu.mapper.OkfEntryMapper;
import info.tongrenlu.mapper.OkfRawMapper;
import info.tongrenlu.mapper.TagMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OKF 阶段 1 导入：把 6 类概念写入 {@code m_okf_entry} 与原始数据表 {@code m_okf_raw}，
 * 概念与原始记录是 M:N（桥表 {@code r_okf_raw_entry}）：
 * <ul>
 *   <li>一条 raw（来源页上的一条原始记录）可派生多个概念（CD 曲目行 → 曲目 + 所属 CD）；</li>
 *   <li>一个概念由多条 raw 支撑（同名原曲在不同作品/CD 索引页的行合并为一个词条）。</li>
 * </ul>
 * 全部幂等可重跑：raw 按 url 查重、迁移类 entry 按 (type, ref) 查重、THBWiki 类按
 * (type, name) 查重（同名合并，即搜索去重后的规范名）、桥表 INSERT IGNORE。
 * 本服务只做导入；OKF 内容生成与读路径切换是下一阶段。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OkfImportService {

    public static final String TYPE_ORIGINAL_GAME = "original_game";
    public static final String TYPE_GAME_TRACK = "game_track";
    public static final String TYPE_CD_TRACK = "cd_track";
    public static final String TYPE_ARTIST = "artist";
    public static final String TYPE_EVENT = "event";
    public static final String TYPE_ALBUM = "album";

    public static final List<String> ALL_TYPES = List.of(
            TYPE_ARTIST, TYPE_EVENT, TYPE_ALBUM, TYPE_ORIGINAL_GAME, TYPE_GAME_TRACK, TYPE_CD_TRACK);

    private static final int MAX_NAME_LENGTH = 255;

    private final OkfEntryMapper okfEntryMapper;
    private final OkfRawMapper okfRawMapper;
    private final ArtistMapper artistMapper;
    private final TagMapper tagMapper;
    private final ArticleMapper articleMapper;
    private final ThbwikiService thbwikiService;
    private final ObjectMapper objectMapper;

    /** 全量导入（6 类按序执行）。 */
    public Map<String, Object> importAll() {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String type : ALL_TYPES) {
            result.put(type, importByType(type));
        }
        return result;
    }

    /** 按 type 导入单类。 */
    public Map<String, Object> importByType(String type) {
        if (!ALL_TYPES.contains(type)) {
            throw new IllegalArgumentException("unsupported okf entry type: " + type);
        }
        return switch (type) {
            case TYPE_ARTIST -> importArtists();
            case TYPE_EVENT -> importEvents();
            case TYPE_ALBUM -> importAlbums();
            case TYPE_ORIGINAL_GAME -> importTrackListIndex(TYPE_ORIGINAL_GAME);
            case TYPE_GAME_TRACK -> importTrackListIndex(TYPE_GAME_TRACK);
            case TYPE_CD_TRACK -> importCdTracks();
            default -> Map.of();
        };
    }

    // ==================== 本站迁移 ====================

    /** entry 幂等 upsert 结果：entryId + 是否新建。 */
    private record Upsert(Long entryId, boolean created) {
    }

    /** artist：m_artist 全量迁移，wiki 内容（经 tag_id 关联的 m_tag 词条）原样带入。 */
    private Map<String, Object> importArtists() {
        int imported = 0;
        int updated = 0;
        int skipped = 0;
        int rawCount = 0;
        for (ArtistBean artist : artistMapper.selectList(null)) {
            if (!StringUtils.hasText(artist.getName()) || artist.getName().strip().length() > MAX_NAME_LENGTH) {
                continue;
            }
            Map<String, Object> raw = new LinkedHashMap<>();
            raw.put("cloud_music_id", artist.getCloudMusicId());
            raw.put("tag_id", artist.getTagId());
            raw.put("thb_wiki_url", artist.getThbWikiUrl());
            var rawImport = importRawRecord("local", "local://m_artist/" + artist.getId(), rawJson(raw));
            if (rawImport.created()) {
                rawCount++;
            }

            // 按 (type, name) 查重：重名 m_artist（脏数据）合并为一个词条，两条 raw 桥接同一 entry
            var upsert = upsertArtistEntry(artist);
            if (rawImport.rawId() != null && upsert.entryId() != null) {
                okfRawMapper.linkRawEntry(rawImport.rawId(), upsert.entryId(), new Date());
            }
            if (upsert.created()) {
                imported++;
            } else {
                skipped++;
            }
        }
        return stats(imported, updated, skipped, rawCount);
    }

    /** artist：按 (type, name) 查重——重名 m_artist 合并为一个词条；wiki 内容补空字段。 */
    private Upsert upsertArtistEntry(ArtistBean artist) {
        String name = artist.getName().strip();
        OkfEntryBean existing = okfEntryMapper.findByTypeAndName(TYPE_ARTIST, name);
        TagBean wiki = artist.getTagId() == null ? null : tagMapper.selectById(artist.getTagId());
        if (existing != null) {
            fillMissing(existing,
                    wiki != null ? wiki.getWikiContent() : null,
                    wiki != null ? wiki.getOkfDescription() : null,
                    wiki != null && StringUtils.hasText(wiki.getWikiSourceUrl())
                            ? wiki.getWikiSourceUrl()
                            : (StringUtils.hasText(artist.getThbWikiUrl()) ? artist.getThbWikiUrl() : null));
            fillTags(existing, wiki != null ? wiki.getWikiTags() : null);
            return new Upsert(existing.getId(), false);
        }

        OkfEntryBean bean = new OkfEntryBean();
        bean.setType(TYPE_ARTIST);
        bean.setName(name);
        bean.setDescription(wiki != null ? wiki.getOkfDescription() : null);
        bean.setContent(wiki != null ? wiki.getWikiContent() : null);
        bean.setTagsJson(wiki != null ? wiki.getWikiTags() : null);
        bean.setSourceUrl(wiki != null && StringUtils.hasText(wiki.getWikiSourceUrl())
                ? wiki.getWikiSourceUrl()
                : (StringUtils.hasText(artist.getThbWikiUrl()) ? artist.getThbWikiUrl() : null));
        bean.setSourcesJson(buildArtistSourcesJson(artist));
        bean.setStatus(wiki != null && StringUtils.hasText(wiki.getWikiStatus())
                ? wiki.getWikiStatus() : "IMPORTED");
        bean.setRefType("m_artist");
        bean.setRefId(artist.getId());
        bean.setUpdDate(new Date());
        okfEntryMapper.insert(bean);
        return new Upsert(bean.getId(), true);
    }

    /** event：m_tag(type=event) 迁移，按 (type, name) 查重（同名展会合并），wiki 内容原样带入。 */
    private Map<String, Object> importEvents() {
        int imported = 0;
        int skipped = 0;
        int rawCount = 0;
        List<TagBean> events = tagMapper.selectList(new LambdaQueryWrapper<TagBean>()
                .eq(TagBean::getType, "event")
                .eq(TagBean::getDelFlg, "0"));
        for (TagBean tag : events) {
            if (!StringUtils.hasText(tag.getTag()) || tag.getTag().strip().length() > MAX_NAME_LENGTH) {
                continue;
            }
            Map<String, Object> raw = new LinkedHashMap<>();
            raw.put("text", tag.getText());
            var rawImport = importRawRecord("local", "local://m_tag/" + tag.getId(), rawJson(raw));
            if (rawImport.created()) {
                rawCount++;
            }

            var upsert = upsertEventEntry(tag);
            if (rawImport.rawId() != null && upsert.entryId() != null) {
                okfRawMapper.linkRawEntry(rawImport.rawId(), upsert.entryId(), new Date());
            }
            if (upsert.created()) {
                imported++;
            } else {
                skipped++;
            }
        }
        return stats(imported, 0, skipped, rawCount);
    }

    /** event：按 (type, name) 查重；wiki 内容补空字段。 */
    private Upsert upsertEventEntry(TagBean tag) {
        String name = tag.getTag().strip();
        OkfEntryBean existing = okfEntryMapper.findByTypeAndName(TYPE_EVENT, name);
        if (existing != null) {
            fillMissing(existing, tag.getWikiContent(), tag.getOkfDescription(), tag.getWikiSourceUrl());
            fillTags(existing, tag.getWikiTags());
            return new Upsert(existing.getId(), false);
        }
        OkfEntryBean bean = new OkfEntryBean();
        bean.setType(TYPE_EVENT);
        bean.setName(name);
        bean.setDescription(tag.getOkfDescription());
        bean.setContent(tag.getWikiContent());
        bean.setTagsJson(tag.getWikiTags());
        bean.setSourceUrl(tag.getWikiSourceUrl());
        bean.setStatus(StringUtils.hasText(tag.getWikiStatus()) ? tag.getWikiStatus() : "IMPORTED");
        bean.setRefType("m_tag");
        bean.setRefId(tag.getId());
        bean.setUpdDate(new Date());
        okfEntryMapper.insert(bean);
        return new Upsert(bean.getId(), true);
    }

    /** album：m_article 全量迁移（同名专辑合法，按 ref 区分为多条概念）。 */
    private Map<String, Object> importAlbums() {
        int imported = 0;
        int skipped = 0;
        int rawCount = 0;
        for (ArticleBean article : articleMapper.selectList(null)) {
            if (!StringUtils.hasText(article.getTitle())
                    || article.getTitle().strip().length() > MAX_NAME_LENGTH) {
                continue;
            }
            Map<String, Object> raw = new LinkedHashMap<>();
            raw.put("artist", article.getArtist());
            raw.put("code", article.getCode());
            raw.put("publish_date", article.getPublishDate());
            raw.put("cloud_music_id", article.getCloudMusicId());
            raw.put("cloud_music_pic_url", article.getCloudMusicPicUrl());
            raw.put("publish_flg", article.getPublishFlg());
            var rawImport = importRawRecord("local", "local://m_article/" + article.getId(), rawJson(raw));
            if (rawImport.created()) {
                rawCount++;
            }

            var upsert = upsertAlbumEntry(article);
            if (rawImport.rawId() != null && upsert.entryId() != null) {
                okfRawMapper.linkRawEntry(rawImport.rawId(), upsert.entryId(), new Date());
            }
            if (upsert.created()) {
                imported++;
            } else {
                skipped++;
            }
        }
        return stats(imported, 0, skipped, rawCount);
    }

    /** album：按 (type, ref) 查重——本站同名专辑合法，按 ref 区分为多条概念。 */
    private Upsert upsertAlbumEntry(ArticleBean article) {
        OkfEntryBean existing = okfEntryMapper.findByRef(TYPE_ALBUM, "m_article", article.getId());
        if (existing != null) {
            return new Upsert(existing.getId(), false);
        }
        OkfEntryBean bean = new OkfEntryBean();
        bean.setType(TYPE_ALBUM);
        bean.setName(article.getTitle().strip());
        bean.setDescription(article.getDescription());
        bean.setSourceUrl(article.getCloudMusicPicUrl());
        bean.setStatus("IMPORTED");
        bean.setRefType("m_article");
        bean.setRefId(article.getId());
        bean.setUpdDate(new Date());
        okfEntryMapper.insert(bean);
        return new Upsert(bean.getId(), true);
    }

    // ==================== THBWiki 解析导入 ====================

    /** 原作游戏 / 游戏原曲：解析 THBWiki「原曲列表」页（仅「游戏」h3 分组）。 */
    private Map<String, Object> importTrackListIndex(String type) {
        String html = thbwikiService.fetchIndexHtml(WikiService.THBWIKI_TRACK_LIST_URL).orElse(null);
        if (html == null) {
            return errorMap("索引页抓取失败: " + WikiService.THBWIKI_TRACK_LIST_URL);
        }
        List<ThbwikiService.OkfIndexEntry> entries = thbwikiService.parseTrackListIndex(html).stream()
                .filter(entry -> type.equals(entry.type()))
                .toList();
        return upsertThbwikiEntries("tracklist", entries);
    }

    /** 官方CD原曲：解析 THBWiki「官方音乐CD曲目列表」页。 */
    private Map<String, Object> importCdTracks() {
        String html = thbwikiService.fetchIndexHtml(WikiService.THBWIKI_CD_TRACK_LIST_URL).orElse(null);
        if (html == null) {
            return errorMap("索引页抓取失败: " + WikiService.THBWIKI_CD_TRACK_LIST_URL);
        }
        return upsertThbwikiEntries("cd_tracklist", thbwikiService.parseCdTrackIndex(html));
    }

    private Map<String, Object> upsertThbwikiEntries(String source,
                                                     List<ThbwikiService.OkfIndexEntry> entries) {
        int imported = 0;
        int updated = 0;
        int skipped = 0;
        int rawCount = 0;
        for (ThbwikiService.OkfIndexEntry entry : entries) {
            if (!StringUtils.hasText(entry.name()) || entry.name().length() > MAX_NAME_LENGTH) {
                continue;
            }
            var rawImport = importRawRecord(source, entry.sourceUrl(), entry.rawJson());
            if (rawImport.created()) {
                rawCount++;
            }
            Long rawId = rawImport.rawId();

            OkfEntryBean existing = okfEntryMapper.findByTypeAndName(entry.type(), entry.name());
            Long entryId;
            if (existing == null) {
                OkfEntryBean bean = new OkfEntryBean();
                bean.setType(entry.type());
                bean.setName(entry.name());
                bean.setGame(entry.game());
                bean.setDescription(entry.description());
                bean.setSourceUrl(entry.sourceUrl());
                bean.setStatus("IMPORTED");
                bean.setUpdDate(new Date());
                okfEntryMapper.insert(bean);
                entryId = bean.getId();
                imported++;
            } else if (fillMissing(existing, null, entry.description(), entry.sourceUrl())) {
                entryId = existing.getId();
                updated++;
            } else {
                entryId = existing.getId();
                skipped++;
            }
            if (rawId != null && entryId != null) {
                okfRawMapper.linkRawEntry(rawId, entryId, new Date());
            }
        }
        return stats(imported, updated, skipped, rawCount);
    }

    // ==================== 共用 ====================

    private record RawImport(Long rawId, boolean created) {
    }

    /**
     * raw 幂等落库（按 url 查重）；已存在时返回既有 id（created=false）。
     * raw 与 entry 的 M:N 关联由桥表（{@link OkfRawMapper#linkRawEntry}）在 entry 侧完成。
     */
    private RawImport importRawRecord(String source, String url, String rawJson) {
        if (!StringUtils.hasText(url)) {
            return new RawImport(null, false);
        }
        OkfRawBean existing = okfRawMapper.findByUrl(url);
        if (existing != null) {
            return new RawImport(existing.getId(), false);
        }
        OkfRawBean bean = new OkfRawBean();
        bean.setSource(source);
        bean.setUrl(url);
        bean.setRawJson(StringUtils.hasText(rawJson) ? rawJson : "{}");
        bean.setUpdDate(new Date());
        okfRawMapper.insert(bean);
        return new RawImport(bean.getId(), true);
    }

    /** 补齐既有条目的空字段（不覆盖已有内容）；有变更时保存并返回 true。 */
    private boolean fillMissing(OkfEntryBean existing, String content, String description, String sourceUrl) {
        boolean changed = false;
        if (existing.getContent() == null && StringUtils.hasText(content)) {
            existing.setContent(content);
            changed = true;
        }
        if ((existing.getDescription() == null || existing.getDescription().isBlank())
                && StringUtils.hasText(description)) {
            existing.setDescription(description);
            changed = true;
        }
        if ((existing.getSourceUrl() == null || existing.getSourceUrl().isBlank())
                && StringUtils.hasText(sourceUrl)) {
            existing.setSourceUrl(sourceUrl);
            changed = true;
        }
        if (changed) {
            existing.setUpdDate(new Date());
            okfEntryMapper.updateById(existing);
        }
        return changed;
    }

    /** 补齐既有条目的空 tags。 */
    private void fillTags(OkfEntryBean existing, String tagsJson) {
        if (!StringUtils.hasText(existing.getTagsJson()) && StringUtils.hasText(tagsJson)) {
            existing.setTagsJson(tagsJson);
            existing.setUpdDate(new Date());
            okfEntryMapper.updateById(existing);
        }
    }

    /**
     * 艺术家条目的 OKF v0.2 sources（两个信息来源）：
     * <ul>
     *   <li>THBWiki 社团页：{@code https://thbwiki.cc/<artistName>}（优先用 THBWiki 匹配
     *       写入的规范 URL，回退按名拼接）</li>
     *   <li>网易云音乐艺人描述页：{@code https://music.163.com/#/artist/desc?id=<cloudMusicId>}</li>
     * </ul>
     * 输出为 frontmatter sources 列表 JSON。
     */
    private String buildArtistSourcesJson(ArtistBean artist) {
        List<Map<String, String>> sources = new ArrayList<>();
        String thbUrl = StringUtils.hasText(artist.getThbWikiUrl())
                ? artist.getThbWikiUrl()
                : "https://thbwiki.cc/" + URLEncoder.encode(artist.getName(), StandardCharsets.UTF_8)
                        .replace("+", "_");
        sources.add(Map.of("id", "thbwiki", "resource", thbUrl, "title", "THBWiki 社团页"));
        if (artist.getCloudMusicId() != null) {
            sources.add(Map.of("id", "netease-desc",
                    "resource", "https://music.163.com/#/artist/desc?id=" + artist.getCloudMusicId(),
                    "title", "网易云音乐艺人描述"));
        }
        try {
            return objectMapper.writeValueAsString(sources);
        } catch (Exception e) {
            log.warn("序列化 artist sources 失败: {}", artist.getName(), e);
            return null;
        }
    }

    /** 结构化字段 → raw JSON；null 值跳过，序列化失败回落 "{}"。 */
    private String rawJson(Map<String, ?> fields) {
        Map<String, Object> cleaned = new LinkedHashMap<>();
        fields.forEach((key, value) -> {
            if (value != null && StringUtils.hasText(String.valueOf(value))) {
                cleaned.put(key, value);
            }
        });
        try {
            return objectMapper.writeValueAsString(cleaned);
        } catch (Exception e) {
            log.warn("Failed to serialize raw fields: {}", e.getMessage());
            return "{}";
        }
    }

    private Map<String, Object> stats(int imported, int updated, int skipped, int raw) {
        return Map.of("imported", imported, "updated", updated, "skipped", skipped, "raw", raw);
    }

    private Map<String, Object> errorMap(String message) {
        return Map.of("imported", 0, "updated", 0, "skipped", 0, "raw", 0, "error", message);
    }
}
