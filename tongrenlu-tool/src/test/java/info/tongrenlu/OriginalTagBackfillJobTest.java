package info.tongrenlu;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import info.tongrenlu.domain.TrackBean;
import info.tongrenlu.mapper.ArticleTagMapper;
import info.tongrenlu.mapper.TrackMapper;
import info.tongrenlu.service.OriginalTagService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/** {@code ?all=true} 一次性全量开关的扫描范围回归测试。 */
class OriginalTagBackfillJobTest {

    private final AtomicReference<String> scopeSql = new AtomicReference<>("");
    private OriginalTagBackfillJob job;

    @BeforeEach
    void setUp() {
        // LambdaQueryWrapper 需要 MP 的 TableInfo 缓存；纯单测没有 Spring 启动过程
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new Configuration(), "");
        TableInfoHelper.initTableInfo(assistant, TrackBean.class);

        TrackMapper trackMapper = mock(TrackMapper.class);
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

        job = new OriginalTagBackfillJob(
                trackMapper, mock(ArticleTagMapper.class), mock(OriginalTagService.class));
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
}
