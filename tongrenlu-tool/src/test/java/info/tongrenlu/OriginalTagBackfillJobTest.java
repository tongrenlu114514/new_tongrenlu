package info.tongrenlu;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import info.tongrenlu.domain.TrackBean;
import info.tongrenlu.mapper.ArticleTagMapper;
import info.tongrenlu.mapper.TrackMapper;
import info.tongrenlu.model.ThbwikiAlbum;
import info.tongrenlu.service.OriginalTagService;
import info.tongrenlu.service.ThbwikiService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** {@code ?all=true} 一次性全量开关的扫描范围回归 + 搜索去重行为。 */
class OriginalTagBackfillJobTest {

    private final AtomicReference<String> scopeSql = new AtomicReference<>("");
    private TrackMapper trackMapper;
    private OriginalTagService originalTagService;
    private ThbwikiService thbwikiService;
    private OriginalTagBackfillJob job;

    @BeforeEach
    void setUp() {
        // LambdaQueryWrapper 需要 MP 的 TableInfo 缓存；纯单测没有 Spring 启动过程
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new Configuration(), "");
        TableInfoHelper.initTableInfo(assistant, TrackBean.class);

        trackMapper = mock(TrackMapper.class);
        // 记录每轮实际使用的查询条件，并返回空页让 runCycle 一轮即结束
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            LambdaQueryWrapper<TrackBean> wrapper = invocation.getArgument(1);
            scopeSql.set(wrapper.getSqlSegment());
            Page<TrackBean> page = invocation.getArgument(0);
            page.setRecords(List.of());
            page.setTotal(0);
            return page;
        }).when(trackMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));

        originalTagService = mock(OriginalTagService.class);
        thbwikiService = mock(ThbwikiService.class);
        job = new OriginalTagBackfillJob(
                trackMapper, mock(ArticleTagMapper.class), originalTagService, thbwikiService);
    }

    @Test
    @DisplayName("默认 preview 排除含全角 ／ 的 THBWiki 行")
    void defaultPreviewExcludesThbwikiRows() {
        job.preview(20, false);
        assertThat(scopeSql.get().toLowerCase()).contains("not like");
    }

    @Test
    @DisplayName("all=true 的 preview 不再排除 ／ 行")
    void allPreviewIncludesThbwikiRows() {
        job.preview(20, true);
        assertThat(scopeSql.get().toLowerCase()).doesNotContain("not like");
    }

    @Test
    @DisplayName("all=true 的轮次确实按全量范围查询")
    void allRunCycleUsesFullScope() {
        job.runCycle(true, false);
        assertThat(scopeSql.get().toLowerCase()).doesNotContain("not like");
    }

    @Test
    @DisplayName("一次性：全量跑完后下一次默认触发自动回到收窄范围")
    void allIsOneShot() {
        job.runCycle(true, false);
        assertThat(scopeSql.get().toLowerCase()).doesNotContain("not like");

        job.runCycle(false, false);
        assertThat(scopeSql.get().toLowerCase()).contains("not like");
    }

    @Test
    @DisplayName("搜索去重：本地变体归一到 THBWiki 规范页面名后再写 tag")
    void canonicalizesVariantsBeforeSaving() {
        TrackBean track = new TrackBean();
        track.setId(1L);
        track.setArticleId(10L);
        track.setOriginal("Sweet Orchid ／ 蓬莱パフォーマンスドール");
        stubPageWith(List.of(track));

        ThbwikiAlbum songHit = new ThbwikiAlbum();
        songHit.setName("Sweet Orchid");
        when(thbwikiService.searchAlbum("Sweet Orchid")).thenReturn(List.of(songHit));

        ThbwikiAlbum gameHit = new ThbwikiAlbum();
        gameHit.setName("蓬莱人形 ～ Performance of 幻想郷");
        when(thbwikiService.searchAlbum("蓬莱パフォーマンスドール")).thenReturn(List.of(gameHit));

        job.runCycle(true, false);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LinkedHashSet<String>> songs = ArgumentCaptor.forClass(LinkedHashSet.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<LinkedHashSet<String>> games = ArgumentCaptor.forClass(LinkedHashSet.class);
        verify(originalTagService).saveOriginalTags(eq(track), songs.capture(), games.capture());
        assertThat(songs.getValue()).containsExactly("Sweet Orchid");
        assertThat(games.getValue()).containsExactly("蓬莱人形 ～ Performance of 幻想郷");
        assertThat(job.status().canonicalizedCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("搜索失败或无结果时回落原名，仍写入 tag")
    void fallsBackToOriginalNameWhenSearchFails() {
        TrackBean track = new TrackBean();
        track.setId(2L);
        track.setArticleId(20L);
        track.setOriginal("Sweet Orchid ／ 蓬莱パフォーマンスドール");
        stubPageWith(List.of(track));

        when(thbwikiService.searchAlbum(any())).thenThrow(new RuntimeException("WAF 503"));

        job.runCycle(true, false);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LinkedHashSet<String>> songs = ArgumentCaptor.forClass(LinkedHashSet.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<LinkedHashSet<String>> games = ArgumentCaptor.forClass(LinkedHashSet.class);
        verify(originalTagService).saveOriginalTags(eq(track), songs.capture(), games.capture());
        assertThat(songs.getValue()).containsExactly("Sweet Orchid");
        assertThat(games.getValue()).containsExactly("蓬莱パフォーマンスドール");
        assertThat(job.status().fallbackCount()).isEqualTo(2);
    }

    private void stubPageWith(List<TrackBean> records) {
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Page<TrackBean> page = invocation.getArgument(0);
            page.setRecords(records);
            page.setTotal(records.size());
            return page;
        }).when(trackMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }
}
