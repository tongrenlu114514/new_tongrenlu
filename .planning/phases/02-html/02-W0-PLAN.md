---
phase: "02-html"
plan: "W0"
type: execute
wave: 0
depends_on: []
files_modified:
  - tongrenlu-dao/src/test/java/info/tongrenlu/service/ThbwikiServiceTest.java
  - tongrenlu-dao/src/test/resources/thbwiki/sample-album.html
  - tongrenlu-dao/src/test/resources/thbwiki/sample-track-no-source.html
autonomous: true
requirements: []
must_haves:
  truths:
    - "Tests can parse sample HTML and verify track extraction"
    - "Edge case (no original source) is testable"
  artifacts:
    - path: "tongrenlu-dao/src/test/java/info/tongrenlu/service/ThbwikiServiceTest.java"
      provides: "Unit tests for HTML parsing methods"
    - path: "tongrenlu-dao/src/test/resources/thbwiki/sample-album.html"
      provides: "Sample album HTML for testing"
    - path: "tongrenlu-dao/src/test/resources/thbwiki/sample-track-no-source.html"
      provides: "Edge case: track without original source"
---

<objective>
Create test infrastructure for Phase 2 HTML parsing validation.

Purpose: Wave 0 must complete before implementation tasks can be verified. Provides automated tests using Jsoup's parse() method with local HTML files.
Output: Test class + sample HTML files that can be run without network access.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
</execution_context>

<context>
@.planning/phases/02-html/02-CONTEXT.md
@.planning/phases/02-html/02-RESEARCH.md
@.planning/phases/02-html/02-VALIDATION.md
@tongrenlu-dao/src/main/java/info/tongrenlu/service/ThbwikiService.java
</context>

<interfaces>
<!-- Test patterns from project conventions -->

From RESEARCH.md - Expected HTML Structure:
```html
<h1 id="firstHeading">
  <span class="mw-page-title-main">Satori Maiden</span>
</h1>

<table id="musicTable" class="wikitable musicTable">
  <tr>
    <th>曲名</th>
    <th>原曲</th>
    <th>备注</th>
  </tr>
  <tr>
    <td>Satori Maiden</td>
    <td class="ogmusic">
      <a class="source" href="/少女さとり　～_3rd_eye">少女さとり　～ 3rd eye</a>
      <a class="source" href="/ハルトマンの妖怪少女">ハルトマンの妖怪少女</a>
    </td>
    <td></td>
  </tr>
</table>
```

From Java testing rules:
```java
@ExtendWith(MockitoExtension.class)
class ThbwikiServiceTest {
    // Use JUnit 5 patterns
}
```
</interfaces>

<tasks>

<task type="auto">
  <name>Task 1: Create sample-album.html test fixture</name>
  <files>tongrenlu-dao/src/test/resources/thbwiki/sample-album.html</files>
  <action>
Create a sample HTML file based on the verified THBWiki structure from RESEARCH.md:

```html
<!DOCTYPE html>
<html>
<head>
    <title>Satori Maiden - THBWiki</title>
</head>
<body>
    <h1 id="firstHeading">
        <span class="mw-page-title-main">Satori Maiden</span>
    </h1>

    <table id="musicTable" class="wikitable musicTable">
        <thead>
            <tr>
                <th>曲名</th>
                <th>原曲</th>
                <th>备注</th>
            </tr>
        </thead>
        <tbody>
            <tr>
                <td>Satori Maiden</td>
                <td class="ogmusic">
                    <a class="source" href="/少女さとり　～_3rd_eye">少女さとり　～ 3rd eye</a>
                    <a class="source" href="/ハルトマンの妖怪少女">ハルトマンの妖怪少女</a>
                </td>
                <td>Studio drums</td>
            </tr>
            <tr>
                <td>Lost Place</td>
                <td class="ogmusic">
                    <a class="source" href="/elley_-_far">elly - far</a>
                </td>
                <td></td>
            </tr>
            <tr>
                <td>Terrace</td>
                <td class="ogmusic">
                    <a class="source" href="/旧約 -Documentation-">旧約 -Documentation-</a>
                </td>
                <td></td>
            </tr>
        </tbody>
    </table>
</body>
</html>
```

This file represents a realistic THBWiki album page with:
- Album title in .mw-page-title-main
- Track table with id="musicTable"
- 3 tracks with various original source configurations
- Second track has only one source link
- Third track has a source with hyphen in name
  </action>
  <verify>
    <automated>test -f tongrenlu-dao/src/test/resources/thbwiki/sample-album.html && echo "File exists"</automated>
  </verify>
  <done>
    sample-album.html exists with album title "Satori Maiden"
    Contains table with id="musicTable"
    Contains 3 tracks with .ogmusic and .source elements
  </done>
</task>

<task type="auto">
  <name>Task 2: Create sample-track-no-source.html edge case fixture</name>
  <files>tongrenlu-dao/src/test/resources/thbwiki/sample-track-no-source.html</files>
  <action>
Create a sample HTML file for the edge case where a track has no original source:

```html
<!DOCTYPE html>
<html>
<head>
    <title>Album Without Sources - THBWiki</title>
</head>
<body>
    <h1 id="firstHeading">
        <span class="mw-page-title-main">Album Without Sources</span>
    </h1>

    <table id="musicTable" class="wikitable musicTable">
        <thead>
            <tr>
                <th>曲名</th>
                <th>原曲</th>
            </tr>
        </thead>
        <tbody>
            <tr>
                <td>Track With Source</td>
                <td class="ogmusic">
                    <a class="source" href="/sample">Sample Source</a>
                </td>
            </tr>
            <tr>
                <td>Track Without Source</td>
                <td></td>
            </tr>
            <tr>
                <td>Track Without Ogmusic Class</td>
                <td>Some other content</td>
            </tr>
        </tbody>
    </table>
</body>
</html>
```

This tests graceful degradation:
- Track 1: has .ogmusic with .source (normal case)
- Track 2: empty td cell (no ogmusic)
- Track 3: td cell with text but no .ogmusic class
  </action>
  <verify>
    <automated>test -f tongrenlu-dao/src/test/resources/thbwiki/sample-track-no-source.html && echo "File exists"</automated>
  </verify>
  <done>
    sample-track-no-source.html exists
    Contains edge case: empty td, no .ogmusic class
  </done>
</task>

<task type="auto">
  <name>Task 3: Create ThbwikiServiceTest class</name>
  <files>tongrenlu-dao/src/test/java/info/tongrenlu/service/ThbwikiServiceTest.java</files>
  <action>
Create unit test class for ThbwikiService HTML parsing methods.

Pattern: Follow JUnit 5 + Mockito patterns from Java testing rules.

```java
package info.tongrenlu.service;

import info.tongrenlu.cache.ThbwikiCacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

class ThbwikiServiceTest {

    private ThbwikiService service;

    @BeforeEach
    void setUp() {
        service = new ThbwikiService(
            new ThbwikiCacheService(),
            new ObjectMapper()
        );
    }

    private String loadHtml(String resourcePath) throws IOException, URISyntaxException {
        return Files.readString(
            Paths.get(getClass().getResource(resourcePath).toURI())
        );
    }

    @Nested
    @DisplayName("fetchAlbumDetail tests")
    class FetchAlbumDetailTests {

        @Test
        @DisplayName("returns empty for null URL")
        void fetchAlbumDetail_nullUrl_returnsEmpty() {
            var result = service.fetchAlbumDetail(null);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty for invalid URL")
        void fetchAlbumDetail_invalidUrl_returnsEmpty() {
            var result = service.fetchAlbumDetail("https://evil.com/page");
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("parseTracks tests")
    class ParseTracksTests {

        @Test
        @DisplayName("parses tracks from sample album HTML")
        void parseTracks_validHtml_parsesAllTracks() throws Exception {
            String html = loadHtml("/thbwiki/sample-album.html");
            Document doc = Jsoup.parse(html);

            // Access private method via reflection or make it package-private
            // For now, use reflection or test through fetchAlbumDetail with local file
            // Better: make parseTracks package-private for testing
        }
    }

    @Nested
    @DisplayName("parseAlbumDetail tests")
    class ParseAlbumDetailTests {

        @Test
        @DisplayName("extracts album name from title")
        void parseAlbumDetail_validHtml_extractsName() throws Exception {
            String html = loadHtml("/thbwiki/sample-album.html");
            Document doc = Jsoup.parse(html);

            // Test through fetchAlbumDetail
            // Since we can't test private methods directly,
            // test via public API when possible
        }
    }

    @Nested
    @DisplayName("Edge case: tracks without source")
    class NoSourceTests {

        @Test
        @DisplayName("handles track with empty source cell")
        void parseTrackRow_emptyCell_handledGracefully() throws Exception {
            String html = loadHtml("/thbwiki/sample-track-no-source.html");
            Document doc = Jsoup.parse(html);

            // Verify parse doesn't throw
        }
    }
}
```

Note: Since parseAlbumDetail, parseTracks, parseTrackRow are private methods,
either:
1. Make them package-private (remove private) for testing, OR
2. Test only through public methods

For Phase 2, the recommendation is to make the parse methods package-private
(`parseAlbumDetail` without modifier) so tests can access them directly within
the same package. This is a common Java pattern for testing private logic.

Update ThbwikiService.java to use package-private (default access) for:
- parseAlbumDetail(Document, String)
- parseTracks(Document)
- parseTrackRow(Element)

Public API remains:
- fetchAlbumDetail(String url)
  </action>
  <verify>
    <automated>cd tongrenlu-dao && mvn test -Dtest=ThbwikiServiceTest -x 2>&1 | tail -30</automated>
  </verify>
  <done>
    ThbwikiServiceTest.java exists with JUnit 5 tests
    Tests for null/invalid URL handling
    Tests for sample album parsing
    Tests for edge case (no source)
    Tests compile and can run (may fail until implementation is done)
  </done>
</task>

</tasks>

<verification>
```bash
# Verify test files exist
ls -la tongrenlu-dao/src/test/resources/thbwiki/
ls -la tongrenlu-dao/src/test/java/info/tongrenlu/service/ThbwikiServiceTest.java

# Compile tests (may fail until implementation done)
cd tongrenlu-dao && mvn test-compile -x
```
</verification>

<success_criteria>
1. ThbwikiServiceTest.java exists in correct package
2. Sample HTML files exist in src/test/resources/thbwiki/
3. Tests can compile (may be stubbed/failing until implementation)
4. Tests use JUnit 5 + AssertJ patterns
5. parse methods are package-private for testability
</success_criteria>

<output>
After completion, create `.planning/phases/02-html/02-W0-SUMMARY.md`
</output>
