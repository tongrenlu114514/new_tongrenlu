package info.tongrenlu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import info.tongrenlu.domain.OkfEntryBean;
import info.tongrenlu.domain.TrackBean;
import info.tongrenlu.mapper.OkfEntryMapper;
import info.tongrenlu.mapper.TagMapper;
import info.tongrenlu.mapper.TrackMapper;
import info.tongrenlu.model.ThbwikiAlbum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * OKF 阶段 2 内容生成：为 {@code m_okf_entry} 六类概念生成 markdown 正文。
 * 素材策略：
 * <ul>
 *   <li>artist：THBWiki 社团页（entry.source_url，缺失时 opensearch 回退）→ LLM；</li>
 *   <li>event：本站收录专辑数据（ref_id → m_tag 关联的专辑）→ LLM，不抓 THBWiki；</li>
 *   <li>original_game：THBWiki「官方游戏」列表页按名取窗口 → LLM；</li>
 *   <li>game_track / cd_track：曲目词条页（entry.source_url，导入时已由索引页挖掘）
 *       → LLM 原曲提示词；缺失时按曲名回索引页挖掘；</li>
 *   <li>album：本站重组（简介 + 曲目列表），不调 LLM。</li>
 * </ul>
 * 生成取代了 m_tag 侧的 WikiGenerateJob 调度（生成管线已切换到 OKF 表）。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OkfGenerateService {

    private static final int SOURCE_WINDOW_CHARS = 20000;

    private final OkfEntryMapper okfEntryMapper;
    private final TagMapper tagMapper;
    private final TrackMapper trackMapper;
    private final ThbwikiService thbwikiService;
    private final LlmService llmService;

    /**
     * 为单个 OKF 条目生成内容。已有内容且非 force 时跳过。
     *
     * @return 是否产生了更新
     */
    public boolean generateForEntry(OkfEntryBean entry, boolean force) {
        if (entry == null || entry.getId() == null) {
            return false;
        }
        String type = entry.getType();
        if (type == null) {
            return false;
        }
        if (!force && StringUtils.hasText(entry.getContent())) {
            log.info("OKF 条目 {} (id={}, type={}) 已有内容，跳过", entry.getName(), entry.getId(), type);
            return false;
        }
        return switch (type) {
            case "artist" -> generateArtist(entry);
            case "event" -> generateEvent(entry);
            case "original_game" -> generateOriginalGame(entry);
            case "game_track", "cd_track" -> generateTrack(entry);
            case "album" -> generateAlbum(entry);
            default -> {
                log.warn("OKF 条目 {} (id={}) 类型 {} 不支持生成", entry.getName(), entry.getId(), type);
                yield false;
            }
        };
    }

    /** artist：THBWiki 社团页（素材页缺失时 opensearch 回退）→ LLM。 */
    private boolean generateArtist(OkfEntryBean entry) {
        String name = entry.getName();
        String pageUrl = entry.getSourceUrl();
        if (!StringUtils.hasText(pageUrl)) {
            List<ThbwikiAlbum> candidates = thbwikiService.searchAlbum(name);
            if (candidates == null || candidates.isEmpty()) {
                saveGenerated(entry, null, null, null, "NO_SOURCE");
                log.info("artist 条目 {} (id={}) 在 THBWiki 无匹配页面", name, entry.getId());
                return true;
            }
            pageUrl = candidates.get(0).getUrl();
        }
        Optional<String> pageText = thbwikiService.fetchArtistPageText(pageUrl);
        if (pageText.isEmpty()) {
            saveGenerated(entry, null, null, pageUrl, "FAILED");
            log.warn("artist 条目 {} (id={}) 页面抓取失败: {}", name, entry.getId(), pageUrl);
            return true;
        }
        var generated = llmService.generateWiki(name, "artist",
                focusedSource(pageText.get(), name, SOURCE_WINDOW_CHARS));
        if (generated.isEmpty() || !StringUtils.hasText(generated.get().content())) {
            saveGenerated(entry, null, null, pageUrl, "FAILED");
            log.warn("artist 条目 {} (id={}) LLM 生成失败", name, entry.getId());
            return true;
        }
        saveGenerated(entry, generated.get().description(), generated.get().content(), pageUrl, "GENERATED");
        log.info("artist 条目 {} (id={}) 生成完成：content {} 字符", name, entry.getId(),
                generated.get().content().length());
        return true;
    }

    /** event：本站收录专辑数据（ref_id → m_tag 关联的专辑）→ LLM，不抓 THBWiki。 */
    private boolean generateEvent(OkfEntryBean entry) {
        String name = entry.getName();
        Long tagId = entry.getRefId();
        if (!"m_tag".equals(entry.getRefType()) || tagId == null) {
            saveGenerated(entry, null, null, null, "FAILED");
            log.warn("event 条目 {} (id={}) 缺少 m_tag 关联", name, entry.getId());
            return true;
        }
        long albumCount = tagMapper.countAlbumsByEventTagId(tagId);
        List<Map<String, Object>> albums = tagMapper.findAlbumsByEventTagId(tagId, 0, 30);

        StringBuilder sourceText = new StringBuilder();
        sourceText.append("展会名称：").append(name).append('\n');
        sourceText.append("本站收录专辑数：").append(albumCount).append('\n');
        sourceText.append("部分收录专辑：\n");
        if (albums.isEmpty()) {
            sourceText.append("（暂无）\n");
        } else {
            for (Map<String, Object> album : albums) {
                Object title = album.get("title");
                if (title != null && StringUtils.hasText(title.toString())) {
                    sourceText.append("- ").append(title).append('\n');
                }
            }
        }

        var generated = llmService.generateWiki(name, "event", sourceText.toString());
        if (generated.isEmpty() || !StringUtils.hasText(generated.get().content())) {
            saveGenerated(entry, null, null, null, "FAILED");
            log.warn("event 条目 {} (id={}) LLM 生成失败", name, entry.getId());
            return true;
        }
        saveGenerated(entry, generated.get().description(), generated.get().content(), null, "GENERATED");
        log.info("event 条目 {} (id={}) 生成完成：content {} 字符", name, entry.getId(),
                generated.get().content().length());
        return true;
    }

    /** original_game：THBWiki「官方游戏」列表页按名取窗口 → LLM。 */
    private boolean generateOriginalGame(OkfEntryBean entry) {
        String name = entry.getName();
        Optional<String> pageText = thbwikiService.fetchIndexPageText(WikiService.THBWIKI_GAME_LIST_URL);
        if (pageText.isEmpty()) {
            saveGenerated(entry, null, null, WikiService.THBWIKI_GAME_LIST_URL, "FAILED");
            log.warn("original_game 条目 {} (id={}) 官方游戏页抓取失败", name, entry.getId());
            return true;
        }
        String sourceText = focusedSource(pageText.get(), name, SOURCE_WINDOW_CHARS);
        var generated = llmService.generateWiki(name, "original_game", sourceText);
        if (generated.isEmpty() || !StringUtils.hasText(generated.get().content())) {
            saveGenerated(entry, null, null, WikiService.THBWIKI_GAME_LIST_URL, "FAILED");
            log.warn("original_game 条目 {} (id={}) LLM 生成失败", name, entry.getId());
            return true;
        }
        saveGenerated(entry, generated.get().description(), generated.get().content(),
                WikiService.THBWIKI_GAME_LIST_URL, "GENERATED");
        log.info("original_game 条目 {} (id={}) 生成完成：content {} 字符", name, entry.getId(),
                generated.get().content().length());
        return true;
    }

    /**
     * game_track / cd_track：曲目词条页正文（entry.source_url 为导入时索引页挖掘到的
     * 词条页链接）→ LLM 原曲提示词；素材页缺失时按曲名回索引页重新挖掘。
     */
    private boolean generateTrack(OkfEntryBean entry) {
        String name = entry.getName();
        String type = entry.getType();
        String pageUrl = entry.getSourceUrl();
        if (!StringUtils.hasText(pageUrl)) {
            String indexUrl = "cd_track".equals(type)
                    ? WikiService.THBWIKI_CD_TRACK_LIST_URL
                    : WikiService.THBWIKI_TRACK_LIST_URL;
            pageUrl = thbwikiService.findEntryLink(indexUrl, name).orElse(null);
        }
        if (!StringUtils.hasText(pageUrl)) {
            saveGenerated(entry, null, null, null, "NO_SOURCE");
            log.info("{} 条目 {} (id={}) 未挖掘到曲目词条页", type, name, entry.getId());
            return true;
        }
        Optional<String> pageText = thbwikiService.fetchArtistPageText(pageUrl);
        if (pageText.isEmpty()) {
            saveGenerated(entry, null, null, pageUrl, "FAILED");
            log.warn("{} 条目 {} (id={}) 曲目词条页抓取失败: {}", type, name, entry.getId(), pageUrl);
            return true;
        }
        String sourceText = "【THBWiki 曲目词条页】\n"
                + focusedSource(pageText.get(), name, SOURCE_WINDOW_CHARS);
        var generated = llmService.generateWiki(name, type, sourceText);
        if (generated.isEmpty() || !StringUtils.hasText(generated.get().content())) {
            saveGenerated(entry, null, null, pageUrl, "FAILED");
            log.warn("{} 条目 {} (id={}) LLM 生成失败", type, name, entry.getId());
            return true;
        }
        saveGenerated(entry, generated.get().description(), generated.get().content(), pageUrl, "GENERATED");
        log.info("{} 条目 {} (id={}) 生成完成：content {} 字符", type, name, entry.getId(),
                generated.get().content().length());
        return true;
    }

    /** album：本站数据重组（简介 + 收录曲目列表），不调 LLM。 */
    private boolean generateAlbum(OkfEntryBean entry) {
        StringBuilder content = new StringBuilder("# ").append(entry.getName()).append("\n\n");
        if (StringUtils.hasText(entry.getDescription())) {
            content.append(entry.getDescription()).append("\n\n");
        }
        if ("m_article".equals(entry.getRefType()) && entry.getRefId() != null) {
            List<TrackBean> tracks = trackMapper.selectList(new LambdaQueryWrapper<TrackBean>()
                    .eq(TrackBean::getArticleId, entry.getRefId())
                    .orderByAsc(TrackBean::getTrackNumber));
            if (!tracks.isEmpty()) {
                content.append("## 收录曲目\n\n");
                for (TrackBean track : tracks) {
                    content.append("- ").append(track.getName() == null ? "未知曲目" : track.getName());
                    if (StringUtils.hasText(track.getArtist())) {
                        content.append(" — ").append(track.getArtist());
                    }
                    content.append('\n');
                }
                content.append('\n');
            }
            content.append("共 ").append(tracks.size()).append(" 首曲目。\n");
        }
        saveGenerated(entry, null, content.toString(), entry.getSourceUrl(), "GENERATED");
        log.info("album 条目 {} (id={}) 重组完成：content {} 字符", entry.getName(), entry.getId(),
                content.length());
        return true;
    }

    /** 生成结果落库：FAILED 时 content/description 传 null（updateById 不清已有列）。 */
    private void saveGenerated(OkfEntryBean entry, String description, String content,
                               String sourceUrl, String status) {
        entry.setContent(content);
        if (StringUtils.hasText(description)) {
            entry.setDescription(description);
        }
        if (StringUtils.hasText(sourceUrl)) {
            entry.setSourceUrl(sourceUrl);
        }
        entry.setStatus(status);
        entry.setUpdDate(new Date());
        okfEntryMapper.updateById(entry);
    }

    /** 超长素材截取：名称首次出现处放在窗口前 1/3；找不到时保留开头。 */
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
}
