package info.tongrenlu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import info.tongrenlu.domain.ArticleTagBean;
import info.tongrenlu.domain.TagBean;
import info.tongrenlu.domain.TrackBean;
import info.tongrenlu.mapper.ArticleTagMapper;
import info.tongrenlu.mapper.TagMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Date;

/**
 * 把单曲 {@code m_track.original} 里的「原曲」「原作」保存成 tag：
 * <ul>
 *   <li>{@code m_tag} 新增 {@code type=original_track} / {@code original_game} 词条；</li>
 *   <li>{@code r_article_tag} 把词条关联到曲目所属专辑（{@code type=m_article}）。</li>
 * </ul>
 * 全程「查重 + 插入」，可重复执行（幂等），不覆盖已有词条、不删除其它类型标签。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OriginalTagService {

    private static final int MAX_TAG_LENGTH = 255;

    private final TagMapper tagMapper;
    private final ArticleTagMapper articleTagMapper;
    private final OriginalTagParser parser = new OriginalTagParser();

    /**
     * 解析并保存一首曲目的原曲 / 原作标签（遗留回填路径，数据源是 m_track.original 文本）。
     * 关联目标封装在 {@link #linkTag} 里：目前关联到专辑 (articleId)。
     */
    @Transactional(rollbackFor = Exception.class)
    public void syncTrack(TrackBean track) {
        if (track == null || track.getArticleId() == null) {
            return;
        }
        OriginalTagParser.ParsedOriginal parsed = parser.parse(track.getOriginal());
        saveTags(track.getArticleId(), parsed.songs(), HomeMusicService.ORIGINAL_TRACK);
        saveTags(track.getArticleId(), parsed.games(), HomeMusicService.ORIGINAL_GAME);
    }

    /**
     * 直接用结构化的原曲 / 原作名写 tag（THBWiki 匹配路径）。
     * 不经过 m_track.original 文本中转与反解析，避免曲名里的「、」「／」等被误切。
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveOriginalTags(TrackBean track, Collection<String> songs, Collection<String> games) {
        if (track == null || track.getArticleId() == null) {
            return;
        }
        saveTags(track.getArticleId(), songs, HomeMusicService.ORIGINAL_TRACK);
        saveTags(track.getArticleId(), games, HomeMusicService.ORIGINAL_GAME);
    }

    private void saveTags(Long articleId, Collection<String> names, String type) {
        if (names == null || names.isEmpty()) {
            return;
        }
        for (String name : names) {
            String tag = clean(name);
            if (tag != null) {
                linkTag(articleId, findOrCreateTag(tag, type));
            }
        }
    }

    /** 与 OriginalTagParser.clean 一致的清洗：trim、去空、限长。 */
    private String clean(String value) {
        if (value == null) {
            return null;
        }
        String v = value.strip();
        if (v.isEmpty() || v.length() > MAX_TAG_LENGTH) {
            return null;
        }
        return v;
    }

    /** 按 (tag, type) 查 m_tag.id，没有则插入一条新词条。 */
    private Long findOrCreateTag(String tag, String type) {
        TagBean existing = tagMapper.selectOne(new LambdaQueryWrapper<TagBean>()
                .select(TagBean::getId)
                .eq(TagBean::getTag, tag)
                .eq(TagBean::getType, type)
                .last("LIMIT 1"));
        if (existing != null && existing.getId() != null) {
            return existing.getId();
        }
        TagBean bean = new TagBean();
        bean.setTag(tag);
        bean.setType(type);
        bean.setDelFlg("0");
        bean.setUpdDate(new Date());
        tagMapper.insert(bean);
        return bean.getId();
    }

    /** 按 (article_id, tag_id, type) 查重后插入关联；改关联目标只需改这个方法。 */
    private void linkTag(Long articleId, Long tagId) {
        if (tagId == null) {
            return;
        }
        ArticleTagBean existing = articleTagMapper.selectOne(new LambdaQueryWrapper<ArticleTagBean>()
                .select(ArticleTagBean::getId)
                .eq(ArticleTagBean::getArticleId, articleId)
                .eq(ArticleTagBean::getTagId, tagId)
                .eq(ArticleTagBean::getType, HomeMusicService.M_ARTICLE)
                .last("LIMIT 1"));
        if (existing != null) {
            return;
        }
        ArticleTagBean bean = new ArticleTagBean();
        bean.setArticleId(articleId);
        bean.setTagId(tagId);
        bean.setType(HomeMusicService.M_ARTICLE);
        articleTagMapper.insert(bean);
    }
}
