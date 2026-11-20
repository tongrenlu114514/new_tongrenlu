package info.tongrenlu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
import java.util.LinkedHashMap;
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
    /** 原作（游戏）词条：从 THBWiki 相同词条导入 */
    public static final String TYPE_ORIGINAL_GAME = "original_game";
    /** 原曲（曲目）词条：从 THBWiki 原作页导入 */
    public static final String TYPE_ORIGINAL_TRACK = "original_track";

    /**
     * 列表页素材的窗口截取上限：与 LlmService.MAX_SOURCE_CHARS 一致。
     * 原作/原曲词条都从列表页取素材，统一以词条名为中心截取窗口，
     * 避免整页从头部截断时新作 / 靠后的曲目信息被丢掉。
     */
    private static final int SOURCE_WINDOW_CHARS = 20000;

    /**
     * 原作词条的统一素材页：THBWiki「官方游戏」列表页（每作含 TH 编号、名称、类别、
     * 贩售日期、简介）。URL 用 percent-encode 形式，避免依赖 HTTP 客户端对中文路径的处理。
     */
    public static final String THBWIKI_GAME_LIST_URL =
            "https://thbwiki.cc/%E5%AE%98%E6%96%B9%E6%B8%B8%E6%88%8F";

    /**
     * 索引页片段（原曲列表 / CD 曲目列表）的截取上限：索引页只有条目名与归属，
     * 信息密度低，取小窗口即可。
     */
    private static final int INDEX_SNIPPET_CHARS = 6000;

    /**
     * 原曲词条素材之一：THBWiki「原曲列表」页（按作品分组列出官方原曲，含曲号与
     * 中/日/英文名；同一曲目仅在其首次出现的作品下列出）。
     */
    public static final String THBWIKI_TRACK_LIST_URL =
            "https://thbwiki.cc/%E5%8E%9F%E6%9B%B2%E5%88%97%E8%A1%A8";

    /**
     * 原曲词条素材之二：THBWiki「官方音乐CD曲目列表」页（曲目归属的 CD 与曲号）。
     */
    public static final String THBWIKI_CD_TRACK_LIST_URL =
            "https://thbwiki.cc/%E5%AE%98%E6%96%B9%E9%9F%B3%E4%B9%90CD%E6%9B%B2%E7%9B%AE%E5%88%97%E8%A1%A8";

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
        if (!isSupportedType(type)) {
            log.warn("词条 {} (id={}) 类型 {} 不支持生成", tag.getTag(), tag.getId(), type);
            return false;
        }
        if (!force && WikiStatus.GENERATED.name().equals(tag.getWikiStatus())) {
            log.info("词条 {} (id={}, type={}) 已生成，跳过", tag.getTag(), tag.getId(), type);
            return false;
        }
        if (TYPE_EVENT.equals(type)) {
            return generateEvent(tag);
        }
        if (TYPE_ORIGINAL_GAME.equals(type)) {
            return generateOriginalGame(tag);
        }
        if (TYPE_ORIGINAL_TRACK.equals(type)) {
            return generateOriginalTrack(tag);
        }
        return generateArtist(tag);
    }

    private boolean isSupportedType(String type) {
        return TYPE_ARTIST.equals(type) || TYPE_EVENT.equals(type)
                || TYPE_ORIGINAL_GAME.equals(type) || TYPE_ORIGINAL_TRACK.equals(type);
    }

    /** artist：THBWiki 社团页 → LLM（沿用原艺术家 Wiki 流程）。 */
    private boolean generateArtist(TagBean tag) {
        String name = tag.getTag();
        Optional<ThbwikiAlbum> matched = thbwikiService.searchArtist(name);
        if (matched.isEmpty()) {
            saveResult(tag, null, null, null, null, WikiStatus.NO_SOURCE);
            log.info("词条 {} (id={}) 在 THBWiki 无匹配页面", name, tag.getId());
            return true;
        }

        String pageUrl = matched.get().getUrl();
        Optional<String> pageText = thbwikiService.fetchArtistPageText(pageUrl);
        if (pageText.isEmpty()) {
            saveResult(tag, null, null, null, pageUrl, WikiStatus.FAILED);
            log.warn("词条 {} (id={}) 页面抓取失败: {}", name, tag.getId(), pageUrl);
            return true;
        }

        // 艺术家名位于页面开头（标题/首段），按名取窗口等价于顺序截断，控制单词条素材成本
        Optional<LlmService.WikiResult> generated = llmService.generateWiki(name, TYPE_ARTIST,
                focusedSource(pageText.get(), name, SOURCE_WINDOW_CHARS));
        if (generated.isEmpty() || !StringUtils.hasText(generated.get().content())) {
            saveResult(tag, null, null, null, pageUrl, WikiStatus.FAILED);
            log.warn("词条 {} (id={}) LLM 生成失败（页面已保存: {}）", name, tag.getId(), pageUrl);
            return true;
        }

        saveResult(tag, generated.get().description(), generated.get().content(),
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
            saveResult(tag, null, null, null, null, WikiStatus.FAILED);
            log.warn("词条 {} (id={}) event LLM 生成失败", name, tag.getId());
            return true;
        }

        saveResult(tag, generated.get().description(), generated.get().content(),
                serializeTags(resolveTags(generated.get().tags())), null, WikiStatus.GENERATED);
        log.info("词条 {} (id={}, event) 生成完成：content {} 字符，素材专辑 {} 张", name, tag.getId(),
                generated.get().content().length(), albums.size());
        return true;
    }

    /**
     * 原作：素材统一来自 THBWiki「官方游戏」列表页（每作含 TH 编号、名称、类别、贩售日期、
     * 简介与最新版本），以原作名为中心截取窗口后交给 LLM；不再按「相同词条」逐个抓取。
     */
    private boolean generateOriginalGame(TagBean tag) {
        return generateFromPage(tag, TYPE_ORIGINAL_GAME, THBWIKI_GAME_LIST_URL);
    }

    /**
     * 原曲：两个索引页（原曲列表 / 官方音乐CD曲目列表）只是条目索引，具体内容需按曲名
     * 「挖掘」——先在索引页定位该曲条目链接，抓取曲目词条页正文作为主素材；
     * 索引页片段（归属与曲号）作为补充。素材完全不可得时 FAILED。
     */
    private boolean generateOriginalTrack(TagBean tag) {
        String name = tag.getTag();

        // 1. 索引页挖掘：定位曲目词条页链接（原曲列表优先，其次 CD 曲目列表）
        Optional<String> detailUrl = thbwikiService.findEntryLink(THBWIKI_TRACK_LIST_URL, name);
        if (detailUrl.isEmpty()) {
            detailUrl = thbwikiService.findEntryLink(THBWIKI_CD_TRACK_LIST_URL, name);
        }

        StringBuilder source = new StringBuilder();
        boolean any = false;

        // 2. 曲目词条页正文（主素材）
        if (detailUrl.isPresent()) {
            String detailText = fetchWindow(detailUrl.get(), name, SOURCE_WINDOW_CHARS);
            if (StringUtils.hasText(detailText)) {
                source.append("【THBWiki 曲目词条页】\n").append(detailText).append('\n');
                any = true;
            }
        }

        // 3. 索引页片段（归属与曲号补充；索引页解析走 ThbwikiService 缓存，整轮复用）
        String trackListWindow = fetchIndexWindow(THBWIKI_TRACK_LIST_URL, name);
        if (StringUtils.hasText(trackListWindow)) {
            source.append("【THBWiki 原曲列表（片段）】\n").append(trackListWindow).append('\n');
            any = true;
        }
        String cdTrackListWindow = fetchIndexWindow(THBWIKI_CD_TRACK_LIST_URL, name);
        if (StringUtils.hasText(cdTrackListWindow)) {
            source.append("【THBWiki 官方音乐CD曲目列表（片段）】\n").append(cdTrackListWindow).append('\n');
            any = true;
        }

        if (!any) {
            saveResult(tag, null, null, null, null, WikiStatus.FAILED);
            log.warn("原曲词条 {} (id={}) 曲目词条页与索引片段均不可得", name, tag.getId());
            return true;
        }

        Optional<LlmService.WikiResult> generated =
                llmService.generateWiki(name, TYPE_ORIGINAL_TRACK, source.toString());
        if (generated.isEmpty() || !StringUtils.hasText(generated.get().content())) {
            saveResult(tag, null, null, null, detailUrl.orElse(THBWIKI_TRACK_LIST_URL), WikiStatus.FAILED);
            log.warn("原曲词条 {} (id={}) LLM 生成失败", name, tag.getId());
            return true;
        }

        saveResult(tag, generated.get().description(), generated.get().content(),
                serializeTags(resolveTags(generated.get().tags())),
                detailUrl.orElse(THBWIKI_TRACK_LIST_URL), WikiStatus.GENERATED);
        log.info("原曲词条 {} (id={}) 生成完成：content {} 字符", name, tag.getId(),
                generated.get().content().length());
        return true;
    }

    /** 抓取页面并按名称取素材窗口；抓取失败返回 null。 */
    private String fetchWindow(String pageUrl, String name, int windowChars) {
        try {
            return thbwikiService.fetchArtistPageText(pageUrl)
                    .map(text -> focusedSource(text, name, windowChars))
                    .orElse(null);
        } catch (Exception e) {
            log.warn("素材页抓取失败: {} ({})", pageUrl, e.getMessage());
            return null;
        }
    }

    /** 索引页片段：正文走 ThbwikiService 的索引页缓存（大页面解析结果整轮复用）。 */
    private String fetchIndexWindow(String indexUrl, String name) {
        return thbwikiService.fetchIndexPageText(indexUrl)
                .map(text -> focusedSource(text, name, INDEX_SNIPPET_CHARS))
                .orElse(null);
    }

    /**
     * 通用：抓取 THBWiki 页面 → 以词条名为中心截取素材窗口（列表页很长，从头部截断会
     * 丢掉靠后条目）→ LLM 生成 → 入库（source_url 记录素材页）。
     */
    private boolean generateFromPage(TagBean tag, String type, String pageUrl) {
        String name = tag.getTag();
        Optional<String> pageText = thbwikiService.fetchArtistPageText(pageUrl);
        if (pageText.isEmpty()) {
            saveResult(tag, null, null, null, pageUrl, WikiStatus.FAILED);
            log.warn("词条 {} (id={}, type={}) 页面抓取失败: {}", name, tag.getId(), type, pageUrl);
            return true;
        }

        String sourceText = focusedSource(pageText.get(), name, SOURCE_WINDOW_CHARS);
        Optional<LlmService.WikiResult> generated = llmService.generateWiki(name, type, sourceText);
        if (generated.isEmpty() || !StringUtils.hasText(generated.get().content())) {
            saveResult(tag, null, null, null, pageUrl, WikiStatus.FAILED);
            log.warn("词条 {} (id={}, type={}) LLM 生成失败（页面已保存: {}）", name, tag.getId(), type, pageUrl);
            return true;
        }

        saveResult(tag, generated.get().description(), generated.get().content(),
                serializeTags(resolveTags(generated.get().tags())), pageUrl, WikiStatus.GENERATED);
        log.info("词条 {} (id={}, type={}) 生成完成：content {} 字符", name, tag.getId(), type,
                generated.get().content().length());
        return true;
    }

    /** 超长素材截取：曲名首次出现处放在窗口前 1/3 处；找不到曲名时保留开头。 */
    private String focusedSource(String text, String name, int maxChars) {
        if (text == null || text.length() <= maxChars) {
            return text;
        }
        int idx = text.indexOf(name);
        if (idx < 0) {
            return text.substring(0, maxChars);
        }
        int start = Math.max(0, idx - maxChars / 3);
        int end = Math.min(text.length(), start + maxChars);
        start = Math.max(0, end - maxChars);
        return text.substring(start, end);
    }

    private void saveResult(TagBean tag, String description, String content, String tags, String sourceUrl, WikiStatus status) {
        tag.setWikiContent(content);
        tag.setWikiTags(tags);
        tag.setWikiSourceUrl(sourceUrl);
        tag.setWikiStatus(status.name());
        tag.setWikiUpdDate(new Date());
        tagMapper.updateById(tag);
    }

    /**
     * 按类型把词条批量重置为 PENDING，让 WikiGenerateJob 用当前素材源重新生成（素材源
     * 调整后的「更新」入口，例如原作词条改从官方游戏列表页获取）。正文/标签保留，
     * 重新生成成功后会被覆盖。
     *
     * @return 重置的词条数
     */
    public int resetTypeToPending(String type) {
        if (!isSupportedType(type)) {
            throw new IllegalArgumentException("unsupported wiki entry type: " + type);
        }
        return tagMapper.update(null, new LambdaUpdateWrapper<TagBean>()
                .eq(TagBean::getType, type)
                .eq(TagBean::getDelFlg, "0")
                .set(TagBean::getWikiStatus, WikiStatus.PENDING.name())
                .set(TagBean::getWikiUpdDate, new Date()));
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
                .in(TagBean::getType, TYPE_ARTIST, TYPE_EVENT, TYPE_ORIGINAL_GAME, TYPE_ORIGINAL_TRACK)
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

    /**
     * 分页词条列表，返回 records/total/page/limit/totalPages。
     * type=artist/event 按类型过滤；type 为空/all 即「百科」视图，排除 artist/event，
     * 只展示其它类型的词条。
     */
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
        String content = (String) fields.get("content");
        return new WikiFields(
                content,
                parseTags((String) fields.get("tags")),
                (String) fields.get("sourceUrl"),
                (String) fields.get("status"));
    }
}
