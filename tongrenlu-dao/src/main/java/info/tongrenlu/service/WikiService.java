package info.tongrenlu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import info.tongrenlu.domain.ArtistBean;
import info.tongrenlu.domain.TagBean;
import info.tongrenlu.enums.WikiStatus;
import info.tongrenlu.mapper.ArtistMapper;
import info.tongrenlu.mapper.TagMapper;
import info.tongrenlu.mapper.WikiMapper;
import info.tongrenlu.model.ThbwikiAlbum;
import info.tongrenlu.support.TextNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Wiki 词条编排：以 m_tag（type=artist / event）为词条主体。
 * artist 走 THBWiki 社团页 → LLM，event 走本站收录数据 → LLM，统一入库 m_tag.wiki_*。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WikiService {

    public static final String TYPE_ARTIST = "artist";
    public static final String TYPE_EVENT = "event";

    private final ThbwikiService thbwikiService;
    private final LlmService llmService;
    private final WikiMapper wikiMapper;
    private final TagMapper tagMapper;
    private final ArtistMapper artistMapper;
    private final ObjectMapper objectMapper;

    /** 互联标签：命中现有词条的带 tagId / artistId，未命中的为 style。 */
    public record ResolvedTag(String tag, Long tagId, Long artistId, String type) {
    }

    /** 供 artist-detail / 展会详情轻量读取的词条字段。 */
    public record WikiFields(String content, List<Map<String, Object>> tags, String sourceUrl, String status) {
    }

    private record TagRef(Long tagId, String type, Long artistId) {
    }

    /**
     * 为单个词条执行生成。非 force 时跳过已生成条目。
     *
     * @param tag   词条（m_tag 行）
     * @param force 是否强制重新生成
     * @return 是否产生了更新
     */
    public boolean generateForTag(TagBean tag, boolean force) {
        if (tag == null || tag.getId() == null) {
            return false;
        }
        String type = tag.getType();
        if (!TYPE_ARTIST.equals(type) && !TYPE_EVENT.equals(type)) {
            log.warn("词条 {} (id={}) 类型 {} 不支持生成", tag.getTag(), tag.getId(), type);
            return false;
        }
        if (!force && WikiStatus.GENERATED.name().equals(tag.getWikiStatus())) {
            log.info("词条 {} (id={}, type={}) 已生成，跳过", tag.getTag(), tag.getId(), type);
            return false;
        }
        return TYPE_EVENT.equals(type) ? generateEvent(tag) : generateArtist(tag);
    }

    /** artist：THBWiki 社团页 → LLM（沿用原艺术家 Wiki 流程）。 */
    private boolean generateArtist(TagBean tag) {
        String name = tag.getTag();
        Optional<ThbwikiAlbum> matched = thbwikiService.searchArtist(name);
        if (matched.isEmpty()) {
            saveResult(tag, null, null, null, WikiStatus.NO_SOURCE);
            log.info("词条 {} (id={}) 在 THBWiki 无匹配页面", name, tag.getId());
            return true;
        }

        String pageUrl = matched.get().getUrl();
        Optional<String> pageText = thbwikiService.fetchArtistPageText(pageUrl);
        if (pageText.isEmpty()) {
            saveResult(tag, null, null, pageUrl, WikiStatus.FAILED);
            log.warn("词条 {} (id={}) 页面抓取失败: {}", name, tag.getId(), pageUrl);
            return true;
        }

        Optional<LlmService.WikiResult> generated = llmService.generateWiki(name, TYPE_ARTIST, pageText.get());
        if (generated.isEmpty() || !StringUtils.hasText(generated.get().content())) {
            saveResult(tag, null, null, pageUrl, WikiStatus.FAILED);
            log.warn("词条 {} (id={}) LLM 生成失败（页面已保存: {}）", name, tag.getId(), pageUrl);
            return true;
        }

        saveResult(tag, generated.get().content(),
                serializeTags(resolveTags(generated.get().tags())), pageUrl, WikiStatus.GENERATED);
        log.info("词条 {} (id={}, artist) 生成完成：content {} 字符", name, tag.getId(),
                generated.get().content().length());
        return true;
    }

    /** event：仅用本站收录数据作素材，不抓 THBWiki，避免编造举办信息。 */
    private boolean generateEvent(TagBean tag) {
        String name = tag.getTag();
        long albumCount = tagMapper.countAlbumsByEventTagId(tag.getId());
        List<Map<String, Object>> albums = tagMapper.findAlbumsByEventTagId(tag.getId(), 0, 30);

        StringBuilder source = new StringBuilder();
        source.append("展会名称：").append(name).append('\n');
        source.append("本站收录专辑数：").append(albumCount).append('\n');
        if (StringUtils.hasText(tag.getText())) {
            source.append("补充说明：").append(tag.getText()).append('\n');
        }
        source.append("部分收录专辑：\n");
        if (albums.isEmpty()) {
            source.append("（暂无）\n");
        } else {
            for (Map<String, Object> album : albums) {
                Object title = album.get("title");
                if (title != null && StringUtils.hasText(title.toString())) {
                    source.append("- ").append(title).append('\n');
                }
            }
        }

        Optional<LlmService.WikiResult> generated = llmService.generateWiki(name, TYPE_EVENT, source.toString());
        if (generated.isEmpty() || !StringUtils.hasText(generated.get().content())) {
            saveResult(tag, null, null, null, WikiStatus.FAILED);
            log.warn("词条 {} (id={}) event LLM 生成失败", name, tag.getId());
            return true;
        }

        saveResult(tag, generated.get().content(),
                serializeTags(resolveTags(generated.get().tags())), null, WikiStatus.GENERATED);
        log.info("词条 {} (id={}, event) 生成完成：content {} 字符，素材专辑 {} 张", name, tag.getId(),
                generated.get().content().length(), albums.size());
        return true;
    }

    private void saveResult(TagBean tag, String content, String tags, String sourceUrl, WikiStatus status) {
        tag.setWikiContent(content);
        tag.setWikiTags(tags);
        tag.setWikiSourceUrl(sourceUrl);
        tag.setWikiStatus(status.name());
        tag.setWikiUpdDate(new Date());
        tagMapper.updateById(tag);
    }

    /**
     * 校验 LLM 输出标签：命中现有词条的标记为 artist / event（携带 tagId），其余为 style。
     */
    public List<ResolvedTag> resolveTags(List<String> tags) {
        List<ResolvedTag> resolved = new ArrayList<>();
        if (tags == null || tags.isEmpty()) {
            return resolved;
        }

        Map<String, TagRef> index = loadTagIndex();
        for (String tag : tags) {
            if (tag == null || tag.isBlank()) {
                continue;
            }
            String key = TextNormalizer.normalizeForComparison(tag.trim());
            TagRef ref = index.get(key);
            if (ref == null) {
                resolved.add(new ResolvedTag(tag.trim(), null, null, "style"));
            } else {
                resolved.add(new ResolvedTag(tag.trim(), ref.tagId(), ref.artistId(), ref.type()));
            }
        }
        return resolved;
    }

    /** 全部 m_tag（artist+event）归一化名索引 + m_artist 的 tag_id→artistId。 */
    private Map<String, TagRef> loadTagIndex() {
        Map<Long, Long> artistIdByTagId = new HashMap<>();
        for (ArtistBean artist : artistMapper.selectList(new LambdaQueryWrapper<ArtistBean>()
                .isNotNull(ArtistBean::getTagId)
                .select(ArtistBean::getId, ArtistBean::getTagId))) {
            artistIdByTagId.putIfAbsent(artist.getTagId(), artist.getId());
        }

        Map<String, TagRef> index = new HashMap<>();
        for (TagBean tag : tagMapper.selectList(new LambdaQueryWrapper<TagBean>()
                .in(TagBean::getType, TYPE_ARTIST, TYPE_EVENT)
                .eq(TagBean::getDelFlg, "0")
                .select(TagBean::getId, TagBean::getTag, TagBean::getType))) {
            if (!StringUtils.hasText(tag.getTag())) {
                continue;
            }
            index.putIfAbsent(TextNormalizer.normalizeForComparison(tag.getTag()),
                    new TagRef(tag.getId(), tag.getType(), artistIdByTagId.get(tag.getId())));
        }
        return index;
    }

    /** 序列化标签为 JSON 数组。 */
    String serializeTags(List<ResolvedTag> tags) {
        try {
            var array = objectMapper.createArrayNode();
            for (ResolvedTag tag : tags) {
                var node = array.addObject();
                node.put("tag", tag.tag());
                if (tag.tagId() != null) {
                    node.put("tagId", tag.tagId());
                }
                if (tag.artistId() != null) {
                    node.put("artistId", tag.artistId());
                }
                node.put("type", tag.type());
            }
            return objectMapper.writeValueAsString(array);
        } catch (Exception e) {
            log.error("序列化 wiki 标签失败", e);
            return null;
        }
    }

    /** 解析 wiki_tags JSON 为前端结构，解析失败返回空列表。 */
    public List<Map<String, Object>> parseTags(String json) {
        List<Map<String, Object>> tags = new ArrayList<>();
        if (!StringUtils.hasText(json)) {
            return tags;
        }
        try {
            JsonNode array = objectMapper.readTree(json);
            if (array.isArray()) {
                for (JsonNode node : array) {
                    Map<String, Object> tag = new HashMap<>();
                    tag.put("tag", node.path("tag").asText(""));
                    if (node.hasNonNull("tagId")) {
                        tag.put("tagId", node.get("tagId").asLong());
                    }
                    if (node.hasNonNull("artistId")) {
                        tag.put("artistId", node.get("artistId").asLong());
                    }
                    tag.put("type", node.path("type").asText("style"));
                    tags.add(tag);
                }
            }
        } catch (Exception e) {
            log.warn("解析 wiki_tags 失败: {}", json, e);
        }
        return tags;
    }

    // ==================== 查询 ====================

    /** 分页词条列表，返回 records/total/page/limit/totalPages。 */
    public Map<String, Object> getEntries(String type, String keyword, int page, int limit) {
        int safePage = Math.max(1, page);
        int safeLimit = Math.min(Math.max(1, limit), 100);
        long offset = (long) (safePage - 1) * safeLimit;

        String normalizedType = (type == null || type.isBlank() || "all".equalsIgnoreCase(type))
                ? null : type.trim();
        String normalizedKeyword = (keyword == null || keyword.isBlank()) ? null : keyword.trim();

        List<Map<String, Object>> records = wikiMapper.findWikiEntries(
                normalizedType, normalizedKeyword, offset, safeLimit);
        long total = wikiMapper.countWikiEntries(normalizedType, normalizedKeyword);

        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("total", total);
        result.put("page", safePage);
        result.put("limit", safeLimit);
        result.put("totalPages", (int) Math.ceil((double) total / safeLimit));
        return result;
    }

    /** 词条关联专辑：服务端分页，避免一次拉全量专辑。 */
    public Map<String, Object> getAlbums(Long tagId, int page, int limit) {
        int safePage = Math.max(1, page);
        int safeLimit = Math.min(Math.max(1, limit), 50);
        long offset = (long) (safePage - 1) * safeLimit;

        List<Map<String, Object>> records = wikiMapper.findAlbumsByTagId(tagId, offset, safeLimit);
        long total = wikiMapper.countAlbumsByTagId(tagId);

        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("total", total);
        result.put("page", safePage);
        result.put("limit", safeLimit);
        result.put("totalPages", (int) Math.ceil((double) total / safeLimit));
        return result;
    }

    /** 词条详情：按 tagId 或词条名查询，wikiTags 解析为数组。 */
    public Map<String, Object> getDetail(Long id, String name) {
        Long tagId = id;
        if (tagId == null && StringUtils.hasText(name)) {
            String normalized = name.trim();
            tagId = wikiMapper.selectTagIdByName(normalized);
            if (tagId == null) {
                // 兼容旧 artist-wiki.html?name= 深链（按 m_artist.name 解析）
                tagId = wikiMapper.selectTagIdByArtistName(normalized);
            }
        }
        if (tagId == null) {
            return null;
        }
        Map<String, Object> detail = wikiMapper.selectWikiDetail(tagId);
        if (detail == null) {
            return null;
        }
        detail.put("wikiTags", parseTags((String) detail.get("wikiTags")));
        return detail;
    }

    /** 轻量读取词条字段（不带专辑统计）。 */
    public WikiFields getWikiFields(Long tagId) {
        if (tagId == null) {
            return new WikiFields(null, List.of(), null, null);
        }
        Map<String, Object> fields = wikiMapper.selectWikiFields(tagId);
        if (fields == null) {
            return new WikiFields(null, List.of(), null, null);
        }
        return new WikiFields(
                (String) fields.get("content"),
                parseTags((String) fields.get("tags")),
                (String) fields.get("sourceUrl"),
                (String) fields.get("status"));
    }
}
