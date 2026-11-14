package info.tongrenlu.service;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import info.tongrenlu.domain.ArticleTagBean;
import info.tongrenlu.domain.TagBean;
import info.tongrenlu.domain.TrackBean;
import info.tongrenlu.mapper.ArticleTagMapper;
import info.tongrenlu.mapper.TagMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** {@link OriginalTagService#saveOriginalTags} 与遗留 {@link OriginalTagService#syncTrack} 的单测。 */
class OriginalTagServiceTest {

    private TagMapper tagMapper;
    private ArticleTagMapper articleTagMapper;
    private OriginalTagService service;
    private final AtomicLong idSeq = new AtomicLong(100);

    @BeforeEach
    void setUp() {
        // LambdaQueryWrapper 需要 MP 的 TableInfo 缓存；纯单测没有 Spring 启动过程，手动初始化
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new Configuration(), "");
        TableInfoHelper.initTableInfo(assistant, TagBean.class);
        TableInfoHelper.initTableInfo(assistant, ArticleTagBean.class);

        tagMapper = mock(TagMapper.class);
        articleTagMapper = mock(ArticleTagMapper.class);
        service = new OriginalTagService(tagMapper, articleTagMapper);

        when(tagMapper.selectOne(any())).thenReturn(null);
        when(articleTagMapper.selectOne(any())).thenReturn(null);
        // 模拟自增主键回填
        doAnswer(invocation -> {
            TagBean bean = invocation.getArgument(0);
            bean.setId(idSeq.incrementAndGet());
            return 1;
        }).when(tagMapper).insert(any(TagBean.class));
    }

    private TrackBean track() {
        TrackBean t = new TrackBean();
        t.setId(10L);
        t.setArticleId(7L);
        return t;
    }

    @Test
    @DisplayName("结构化原曲/原作写两类 m_tag 并关联到专辑")
    void saveOriginalTagsWritesBothTypesAndLinks() {
        service.saveOriginalTags(track(), List.of("Song A", "Song B"), List.of("Game G"));

        ArgumentCaptor<TagBean> tags = ArgumentCaptor.forClass(TagBean.class);
        verify(tagMapper, times(3)).insert(tags.capture());
        assertThat(tags.getAllValues())
                .extracting(TagBean::getTag, TagBean::getType)
                .containsExactlyInAnyOrder(
                        tuple("Song A", HomeMusicService.ORIGINAL_TRACK),
                        tuple("Song B", HomeMusicService.ORIGINAL_TRACK),
                        tuple("Game G", HomeMusicService.ORIGINAL_GAME));

        ArgumentCaptor<ArticleTagBean> links = ArgumentCaptor.forClass(ArticleTagBean.class);
        verify(articleTagMapper, times(3)).insert(links.capture());
        assertThat(links.getAllValues()).allSatisfy(link -> {
            assertThat(link.getArticleId()).isEqualTo(7L);
            assertThat(link.getType()).isEqualTo(HomeMusicService.M_ARTICLE);
            assertThat(link.getTagId()).isNotNull();
        });
    }

    @Test
    @DisplayName("已存在的词条与关联被复用，不重复插入（幂等）")
    void existingTagAndLinkAreReused() {
        TagBean existingTag = new TagBean();
        existingTag.setId(99L);
        when(tagMapper.selectOne(any())).thenReturn(existingTag);
        when(articleTagMapper.selectOne(any())).thenReturn(new ArticleTagBean());

        service.saveOriginalTags(track(), List.of("Song A"), List.of("Game G"));

        verify(tagMapper, never()).insert(any(TagBean.class));
        verify(articleTagMapper, never()).insert(any(ArticleTagBean.class));
    }

    @Test
    @DisplayName("null / 空白 / 超长片段被过滤")
    void blankNullOverlongAreSkipped() {
        service.saveOriginalTags(track(),
                Arrays.asList(null, "", "   ", "あ".repeat(300), "Valid"), List.of());

        ArgumentCaptor<TagBean> tags = ArgumentCaptor.forClass(TagBean.class);
        verify(tagMapper, times(1)).insert(tags.capture());
        assertThat(tags.getValue().getTag()).isEqualTo("Valid");
        assertThat(tags.getValue().getType()).isEqualTo(HomeMusicService.ORIGINAL_TRACK);
    }

    @Test
    @DisplayName("games 为 null 不抛异常")
    void nullGamesAreSafe() {
        service.saveOriginalTags(track(), List.of("Song A"), null);
        verify(tagMapper, times(1)).insert(any(TagBean.class));
    }

    @Test
    @DisplayName("articleId 为 null 时直接跳过")
    void nullArticleIdIsNoop() {
        service.saveOriginalTags(new TrackBean(), List.of("X"), List.of("Y"));
        verify(tagMapper, never()).insert(any(TagBean.class));
        verify(articleTagMapper, never()).insert(any(ArticleTagBean.class));
    }

    @Test
    @DisplayName("遗留 syncTrack 仍按文本解析出原曲 + 原作两类 tag")
    void syncTrackStillParsesOriginalText() {
        TrackBean t = track();
        t.setOriginal("少女さとり ～ 3rd eye ／ 东方地灵殿");

        service.syncTrack(t);

        ArgumentCaptor<TagBean> tags = ArgumentCaptor.forClass(TagBean.class);
        verify(tagMapper, times(2)).insert(tags.capture());
        assertThat(tags.getAllValues())
                .extracting(TagBean::getType)
                .containsExactlyInAnyOrder(
                        HomeMusicService.ORIGINAL_TRACK, HomeMusicService.ORIGINAL_GAME);
    }
}
