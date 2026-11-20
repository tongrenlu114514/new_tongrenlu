package info.tongrenlu.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** {@link LlmService} 超大素材分批生成 + 素材切分单测（不触网）。 */
class LlmServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 可观测的 LlmService：覆盖 postChatCompletions 返回预设响应并记录每次请求体。 */
    private static class StubLlmService extends LlmService {
        private final List<String> bodies = new ArrayList<>();
        private java.util.function.Function<String, String> responder;

        StubLlmService() {
            super("https://example.invalid", "test-key", "test-model", 8192, MAPPER);
        }

        @Override
        String postChatCompletions(String body) {
            bodies.add(body);
            return responder == null ? null : responder.apply(body);
        }
    }

    private StubLlmService service;

    @BeforeEach
    void setUp() {
        service = new StubLlmService();
    }

    /** 把内部 wiki JSON 包装成 chat/completions 响应体。 */
    private static String chatResponse(String description, String content, List<String> tags) {
        try {
            var wiki = MAPPER.createObjectNode();
            wiki.put("description", description);
            wiki.put("content", content);
            var tagArray = wiki.putArray("tags");
            tags.forEach(tagArray::add);
            var message = MAPPER.createObjectNode();
            message.put("content", MAPPER.writeValueAsString(wiki));
            var choice = MAPPER.createObjectNode();
            choice.set("message", message);
            var root = MAPPER.createObjectNode();
            var choices = root.putArray("choices");
            choices.add(choice);
            return MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("splitSource：按上限切块，优先在换行边界断开，块长均不超过上限")
    void splitSourceBreaksOnNewline() {
        String source = "AAAA\nBBBB\nCCCC\nDDDD";
        List<String> chunks = service.splitSource(source, 10);
        assertThat(chunks).hasSizeGreaterThan(1);
        chunks.forEach(c -> assertThat(c.length()).isLessThanOrEqualTo(10));
        assertThat(String.join("", chunks)).isEqualTo(source);
    }

    @Test
    @DisplayName("splitSource：无换行也能按上限切块且不丢字符")
    void splitSourceNoNewline() {
        String source = "x".repeat(25);
        List<String> chunks = service.splitSource(source, 10);
        assertThat(chunks).hasSize(3);
        assertThat(String.join("", chunks)).isEqualTo(source);
    }

    @Test
    @DisplayName("generateWiki：素材未超限时只调用一次 LLM")
    void smallSourceSingleCall() {
        service.responder = body -> chatResponse("摘要", "## 正文", List.of("标签"));
        Optional<LlmService.WikiResult> result =
                service.generateWiki("东方社团", "artist", "短素材");
        assertThat(result).isPresent();
        assertThat(result.get().content()).isEqualTo("## 正文");
        assertThat(service.bodies).hasSize(1);
    }

    @Test
    @DisplayName("generateWiki：素材超限时分块提取草稿并合并为一次总结调用")
    void largeSourceBatchesThenMerges() {
        // 80000 字符上限：构造 90000 字符素材 → 2 块 + 1 合并 = 3 次调用
        String source = "素材".repeat(45000);
        service.responder = body -> {
            if (body.contains("各分块草稿")) {
                return chatResponse("最终摘要", "## 合并正文", List.of("合并标签"));
            }
            return chatResponse("草稿摘要", "## 草稿正文", List.of("草稿标签"));
        };

        Optional<LlmService.WikiResult> result =
                service.generateWiki("东方社团", "artist", source);

        assertThat(result).isPresent();
        assertThat(result.get().description()).isEqualTo("最终摘要");
        assertThat(result.get().content()).isEqualTo("## 合并正文");
        // 2 个草稿块 + 1 次合并
        assertThat(service.bodies).hasSize(3);
        // 前两次是草稿提取（system 含"草稿"），最后一次是合并（system 含"综合成一篇"）
        assertThat(service.bodies.get(0)).contains("草稿");
        assertThat(service.bodies.get(2)).contains("综合成一篇");
    }

    @Test
    @DisplayName("generateWiki：所有分块都失败时回退到首块单次调用")
    void largeSourceAllDraftsFailFallsBack() {
        String source = "素材".repeat(45000);
        service.responder = body -> {
            // 草稿提取请求体的 system 含"草稿集合"以外的"提取"；合并请求体含"各分块草稿"
            if (body.contains("各分块草稿")) {
                return null; // 不会走到
            }
            return null; // 所有草稿失败
        };

        Optional<LlmService.WikiResult> result =
                service.generateWiki("东方社团", "artist", source);

        // 全部草稿失败 → 回退首块单次调用，responder 仍返回 null → empty
        assertThat(result).isEmpty();
        // 2 次草稿失败 + 1 次回退 = 3 次
        assertThat(service.bodies).hasSize(3);
    }
}
