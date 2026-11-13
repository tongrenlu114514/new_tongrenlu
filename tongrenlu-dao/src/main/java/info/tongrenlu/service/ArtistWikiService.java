package info.tongrenlu.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import info.tongrenlu.domain.ArtistBean;
import info.tongrenlu.enums.ThbWikiStatus;
import info.tongrenlu.model.ThbwikiAlbum;
import info.tongrenlu.support.TextNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 艺术家 Wiki 内容生成编排：THBWiki 抓取 → LLM 生成 → 标签校验 → 入库。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ArtistWikiService {

    private final ThbwikiService thbwikiService;
    private final LlmService llmService;
    private final ArtistService artistService;
    private final ObjectMapper objectMapper;

    /** 互联标签：type=artist（可跳转到对应艺术家条目）/ style（风格标签） */
    public record ResolvedTag(String tag, Long artistId, String type) {
    }

    /**
     * 为单个艺术家执行完整生成流程。
     * 已有生成内容且非 force 时跳过。
     *
     * @param artist 艺术家
     * @param force  是否强制重新生成
     * @return 是否产生了更新
     */
    public boolean generateForArtist(ArtistBean artist, boolean force) {
        if (artist == null || artist.getId() == null) {
            return false;
        }
        if (!force && ThbWikiStatus.MATCHED.name().equals(artist.getThbWikiStatus())
                && artist.getWikiContent() != null && !artist.getWikiContent().isBlank()) {
            log.info("艺术家 {} (id={}) 已有 wiki 内容，跳过", artist.getName(), artist.getId());
            return false;
        }

        // 1. THBWiki 搜索
        Optional<ThbwikiAlbum> matched = thbwikiService.searchArtist(artist.getName());
        if (matched.isEmpty()) {
            artist.setThbWikiStatus(ThbWikiStatus.NOT_FOUND.name());
            artist.setWikiUpdDate(new Date());
            artistService.updateById(artist);
            log.info("艺术家 {} (id={}) 在 THBWiki 无匹配页面", artist.getName(), artist.getId());
            return true;
        }

        // 2. 抓取页面正文
        String pageUrl = matched.get().getUrl();
        Optional<String> pageText = thbwikiService.fetchArtistPageText(pageUrl);
        artist.setThbWikiUrl(pageUrl);
        if (pageText.isEmpty()) {
            artist.setThbWikiStatus(ThbWikiStatus.FETCH_FAILED.name());
            artist.setWikiUpdDate(new Date());
            artistService.updateById(artist);
            log.warn("艺术家 {} (id={}) 页面抓取失败: {}", artist.getName(), artist.getId(), pageUrl);
            return true;
        }
        artist.setThbWikiStatus(ThbWikiStatus.MATCHED.name());

        // 3. LLM 生成
        Optional<LlmService.ArtistWiki> generated = llmService.generateArtistWiki(artist.getName(), pageText.get());
        if (generated.isEmpty()) {
            // 状态保持 MATCHED 但 content 为空，表示"抓取成功、生成失败"，可通过 force 重试
            artist.setWikiUpdDate(new Date());
            artistService.updateById(artist);
            log.warn("艺术家 {} (id={}) LLM 生成失败（页面已保存: {}）", artist.getName(), artist.getId(), pageUrl);
            return true;
        }

        // 4. 标签校验 + 入库
        List<ResolvedTag> resolvedTags = resolveTags(generated.get().tags());
        artist.setWikiContent(generated.get().content());
        artist.setWikiTags(serializeTags(resolvedTags));
        artist.setWikiUpdDate(new Date());
        artistService.updateById(artist);

        log.info("艺术家 {} (id={}) wiki 生成完成：content {} 字符，tags {} 个（{} 个关联艺术家）",
                artist.getName(), artist.getId(),
                generated.get().content().length(),
                resolvedTags.size(),
                resolvedTags.stream().filter(t -> t.artistId() != null).count());
        return true;
    }

    /**
     * 校验 LLM 输出的标签：命中现有艺术家名的标记为 artist（携带 artistId），
     * 其余标记为 style 风格标签。
     */
    public List<ResolvedTag> resolveTags(List<String> tags) {
        List<ResolvedTag> resolved = new ArrayList<>();
        if (tags == null || tags.isEmpty()) {
            return resolved;
        }

        Map<String, Long> artistNames = loadArtistNameIndex();
        for (String tag : tags) {
            if (tag == null || tag.isBlank()) {
                continue;
            }
            String key = TextNormalizer.normalizeForComparison(tag.trim());
            Long artistId = artistNames.get(key);
            resolved.add(new ResolvedTag(tag.trim(), artistId, artistId != null ? "artist" : "style"));
        }
        return resolved;
    }

    /**
     * 加载全部艺术家名称的归一化索引（每次调用重建，批量任务每轮只调用一次）。
     */
    private Map<String, Long> loadArtistNameIndex() {
        Map<String, Long> index = new HashMap<>();
        List<ArtistBean> artists = artistService.list();
        for (ArtistBean artist : artists) {
            if (artist.getName() != null && !artist.getName().isBlank()) {
                index.putIfAbsent(TextNormalizer.normalizeForComparison(artist.getName()), artist.getId());
            }
        }
        return index;
    }

    /**
     * 序列化标签为 JSON 数组。
     */
    String serializeTags(List<ResolvedTag> tags) {
        try {
            var array = objectMapper.createArrayNode();
            for (ResolvedTag tag : tags) {
                var node = array.addObject();
                node.put("tag", tag.tag());
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
}
