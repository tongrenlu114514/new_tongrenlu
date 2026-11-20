package info.tongrenlu.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import info.tongrenlu.support.CloudMusicHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** {@link CloudMusicCommentService} 评论接口 URL 构造与解析单测（不触网）。 */
class CloudMusicCommentServiceTest {

    private CloudMusicCommentService service;

    @BeforeEach
    void setUp() {
        service = new CloudMusicCommentService(new CloudMusicHttpClient(), new ObjectMapper());
    }

    private void setBase(String base) {
        try {
            Field f = CloudMusicCommentService.class.getDeclaredField("cloudMusicApiBase");
            f.setAccessible(true);
            f.set(service, base);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("commentUrl：专辑类型 type=3、推荐排序 sortType=1，尾斜杠被去掉")
    void commentUrl() {
        setBase("http://127.0.0.1:3000/");
        assertThat(service.commentUrl("123", 20))
                .isEqualTo("http://127.0.0.1:3000/comment/new?id=123&type=3&sortType=1&pageNo=1&pageSize=20");
    }

    @Test
    @DisplayName("parseComments：取 data.comments（新版接口），按 limit 截断，空内容跳过")
    void parseComments() {
        String json = """
                {"code":200,"data":{"comments":[
                  {"content":"曲子很棒","user":{"nickname":"听众A"}},
                  {"content":"  ","user":{"nickname":"空内容"}},
                  {"content":"循环了一整天","user":{"nickname":"听众B"}},
                  {"content":"第三条","user":{"nickname":"听众C"}}
                ]}}
                """;
        List<CloudMusicCommentService.Comment> comments = service.parseComments(json, 2);
        assertThat(comments).hasSize(2);
        assertThat(comments.get(0).nickname()).isEqualTo("听众A");
        assertThat(comments.get(0).content()).isEqualTo("曲子很棒");
        assertThat(comments.get(1).content()).isEqualTo("循环了一整天");
    }

    @Test
    @DisplayName("parseComments：兼容顶层 comments（旧版接口）")
    void parseCommentsLegacyShape() {
        String json = """
                {"code":200,"comments":[{"content":"旧版评论","user":{"nickname":"听众X"}}]}
                """;
        List<CloudMusicCommentService.Comment> comments = service.parseComments(json, 5);
        assertThat(comments).hasSize(1);
        assertThat(comments.get(0).content()).isEqualTo("旧版评论");
    }

    @Test
    @DisplayName("parseComments：code 非 200 / 空响应 / 损坏 JSON 一律降级为空列表")
    void parseCommentsFallbacks() {
        assertThat(service.parseComments("{\"code\":301,\"comments\":[]}", 5)).isEmpty();
        assertThat(service.parseComments(null, 5)).isEmpty();
        assertThat(service.parseComments("not-json", 5)).isEmpty();
        assertThat(service.parseComments("{\"code\":200}", 5)).isEmpty();
    }

    @Test
    @DisplayName("fetchRecommendedComments：空专辑 id 直接返回空列表")
    void blankAlbumIdReturnsEmpty() {
        assertThat(service.fetchRecommendedComments("", 20)).isEmpty();
        assertThat(service.fetchRecommendedComments("   ", 20)).isEmpty();
        assertThat(service.fetchRecommendedComments("123", 0)).isEmpty();
    }

    @Test
    @DisplayName("Spring 上下文可实例化：多构造器时用 @Autowired 指定的构造器，不再报 No default constructor")
    void springCanInstantiateBean() {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
            ctx.register(ObjectMapper.class, CloudMusicCommentService.class);
            ctx.refresh();
            assertThat(ctx.getBean(CloudMusicCommentService.class)).isNotNull();
        }
    }
}
