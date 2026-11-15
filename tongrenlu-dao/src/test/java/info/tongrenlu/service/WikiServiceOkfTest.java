package info.tongrenlu.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** {@link WikiService#toOkfDocument} 等 OKF（Open Knowledge Format）渲染规则的单测。 */
class WikiServiceOkfTest {

    private final WikiService service =
            new WikiService(null, null, null, null, null, null, null);

    @Test
    @DisplayName("v0.2 frontmatter：generated/status/sources 齐备，不再输出 v0.1 timestamp")
    void rendersFullFrontmatter() {
        Map<String, Object> detail = new HashMap<>();
        detail.put("type", "artist");
        detail.put("name", "IOSYS");
        detail.put("okfDescription", "东方同人老铺。");
        detail.put("wikiSourceUrl", "https://thbwiki.cc/IOSYS");
        detail.put("wikiTags", List.of(Map.of("tag", "电子"), Map.of("tag", "东方Project")));
        detail.put("wikiStatus", "GENERATED");
        detail.put("wikiUpdDate", new Date(0));
        detail.put("wikiContent", "## 简介\n\n正文。");

        String doc = service.toOkfDocument(detail);

        assertThat(doc).startsWith("---\n");
        assertThat(doc).contains("type: \"artist\"");
        assertThat(doc).contains("title: \"IOSYS\"");
        assertThat(doc).contains("description: \"东方同人老铺。\"");
        assertThat(doc).contains("resource: \"https://thbwiki.cc/IOSYS\"");
        assertThat(doc).contains("tags:\n  - \"电子\"\n  - \"东方Project\"");
        // v0.2：generated 取代 timestamp；lifecycle + provenance
        assertThat(doc).contains("status: \"stable\"");
        assertThat(doc).contains("generated: { by: \"agent/tongrenlu-wiki/1.0\", at: \"1970-01-01T00:00:00Z\" }");
        assertThat(doc).contains("sources:\n  - id: \"thbwiki\"");
        assertThat(doc).doesNotContain("timestamp:");
        assertThat(doc).contains("---\n\n## 简介");
    }

    @Test
    @DisplayName("未生成（PENDING）词条 lifecycle 映射为 draft")
    void mapsPendingStatusToDraft() {
        Map<String, Object> detail = new HashMap<>();
        detail.put("type", "original_track");
        detail.put("name", "曲名");
        detail.put("wikiStatus", "PENDING");

        String doc = service.toOkfDocument(detail);

        assertThat(doc).contains("status: \"draft\"");
        assertThat(doc).doesNotContain("sources:");  // 无素材页 → 省略 provenance 族
        assertThat(doc).contains("暂无词条内容");
    }

    @Test
    @DisplayName("artist 条目 sources 含 THBWiki 与网易云音乐描述页双来源")
    void artistSourcesIncludeNeteaseDesc() {
        Map<String, Object> detail = new HashMap<>();
        detail.put("type", "artist");
        detail.put("name", "IOSYS");
        detail.put("wikiStatus", "GENERATED");
        detail.put("wikiSourceUrl", "https://thbwiki.cc/IOSYS");
        detail.put("cloudMusicId", 6887L);

        String doc = service.toOkfDocument(detail);

        assertThat(doc).contains("sources:");
        assertThat(doc).contains("id: \"thbwiki\"");
        assertThat(doc).contains("resource: \"https://thbwiki.cc/IOSYS\"");
        assertThat(doc).contains("id: \"netease-desc\"");
        assertThat(doc).contains("resource: \"https://music.163.com/#/artist/desc?id=6887\"");
        assertThat(doc).contains("title: \"网易云音乐艺人描述\"");
    }

    @Test
    @DisplayName("非 artist 类型不输出网易云音乐来源")
    void nonArtistOmitsNeteaseSource() {
        Map<String, Object> detail = new HashMap<>();
        detail.put("type", "event");
        detail.put("name", "例大祭");
        detail.put("wikiStatus", "GENERATED");
        detail.put("wikiSourceUrl", "https://thbwiki.cc/X");
        detail.put("cloudMusicId", 6887L);  // event 不应使用该字段

        String doc = service.toOkfDocument(detail);

        assertThat(doc).contains("id: \"thbwiki\"");
        assertThat(doc).doesNotContain("netease-desc");
    }

    @Test
    @DisplayName("description / resource 缺失时省略该字段；无正文时输出占位")
    void omitsMissingOptionalFields() {
        Map<String, Object> detail = new HashMap<>();
        detail.put("type", "original_track");
        detail.put("name", "曲名");

        String doc = service.toOkfDocument(detail);

        assertThat(doc).contains("type: \"original_track\"");
        assertThat(doc).doesNotContain("description:");
        assertThat(doc).doesNotContain("resource:");
        assertThat(doc).doesNotContain("tags:");
        assertThat(doc).contains("暂无词条内容");
    }

    @Test
    @DisplayName("YAML 双引号标量转义反斜杠 / 双引号 / 换行")
    void escapesYamlScalars() {
        Map<String, Object> detail = new HashMap<>();
        detail.put("type", "artist");
        detail.put("name", "\"Io\"\\SYS");

        String doc = service.toOkfDocument(detail);

        assertThat(doc).contains("title: \"\\\"Io\\\"\\\\SYS\"");
    }
}
