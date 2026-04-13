# Phase 2: HTML 解析层 - Research

**Researched:** 2026-04-14
**Domain:** THBWiki album page HTML parsing
**Confidence:** MEDIUM

## Summary

Phase 2 implements the HTML parsing layer for THBWiki album pages. The goal is to extract track lists with original source information from album pages. Based on Phase 1 verification, THBWiki uses MediaWiki 1.39.10 with specific CSS classes for track tables.

Key findings:
- **CSS selectors verified** via Phase 1 testing: `.wikitable.musicTable`, `.ogmusic`, `.source`, `.mw-page-title-main`
- **Parsing approach**: Direct HTML parsing in ThbwikiService using Jsoup 1.18.1
- **Error handling**: Graceful degradation with empty tracks + warning log
- **Testing strategy**: Integration tests with sample HTML files

Primary recommendation: Implement `fetchAlbumDetail()` method in ThbwikiService that fetches the album page HTML and parses track information using the verified CSS selectors.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01: 解析器位置** - 直接在 ThbwikiService 内解析，不单独拆分 Parser 类
- **D-02: 容错策略** - 返回空列表 + 日志警告，不抛出异常
- **D-03: 验证方式** - 集成测试 + 真实 HTML 样本

### Claude's Discretion
- 具体 CSS 选择器实现细节
- 解析方法的内部结构
- 边界情况的处理顺序

### Deferred Ideas (OUT OF SCOPE)
- 匹配算法优化 (Phase 6)
- 批量解析进度 (Phase 7/8)
- 缓存刷新机制 (Phase 4)
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| ORIGINAL-01.2 | 曲目列表解析 - 解析 THBWiki 专辑页面的曲目列表，提取每首歌曲的原曲信息 | CSS selectors `.wikitable.musicTable`, `#musicTable tr` confirmed |
| ORIGINAL-01.3 | HTML 解析 - 解析 THBWiki 页面提取原曲出处和原曲名称 | CSS selectors `.ogmusic`, `.source` confirmed |
</phase_requirements>

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Jsoup | 1.18.1 | HTML parsing | Verified in Phase 1, consistent with project dependencies |
| Hutool HttpRequest | 5.8.40 | HTTP client | Already used in project, consistent with CloudMusic integration |
| Caffeine | (existing) | Caching | Already in ThbwikiCacheService |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| JUnit 5 | (existing) | Unit testing | Writing tests for parsing logic |
| AssertJ | (existing) | Assertions | Fluent test assertions |

**Installation:** Jsoup already added in Phase 1 (tongrenlu-dao pom.xml)

**Version verification:**
- Jsoup 1.18.1 - verified via Maven Central [CITED: mavenrepository.com/jsoup]
- Confirmed in project's pom.xml

## Architecture Patterns

### Recommended Project Structure
```
tongrenlu-dao/src/
├── service/
│   └── ThbwikiService.java       # Existing, add fetchAlbumDetail()
├── model/
│   ├── ThbwikiAlbum.java         # Existing
│   └── ThbwikiTrack.java         # Existing
└── resources/
    └── test/
        └── thbwiki/
            └── sample-album.html  # Sample HTML for testing
```

### Pattern 1: Service-Internal HTML Parsing

**What:** Parse HTML directly within ThbwikiService methods using private helper methods

**When to use:** When parsing logic is tightly coupled to HTTP fetching, no reuse needed

**Example:**
```java
// Source: Based on Phase 2 decisions (D-01)
// ThbwikiService.java

public Optional<ThbwikiAlbum> fetchAlbumDetail(String url) {
    String html = fetchPage(url);
    Document doc = Jsoup.parse(html);
    return parseAlbumDetail(doc, url);
}

private Optional<ThbwikiAlbum> parseAlbumDetail(Document doc, String url) {
    // Parse album name
    String albumName = doc.selectFirst(".mw-page-title-main")
                          .text();

    // Parse tracks
    List<ThbwikiTrack> tracks = parseTracks(doc);

    ThbwikiAlbum album = new ThbwikiAlbum();
    album.setName(albumName);
    album.setUrl(url);
    tracks.forEach(album::addTrack);

    if (tracks.isEmpty()) {
        log.warn("No tracks parsed from album page: {}", url);
    }
    return Optional.of(album);
}
```

### Pattern 2: Graceful Degradation

**What:** Return empty result with warning log instead of throwing exception

**When to use:** When operation is non-critical, caller can handle empty result

**Example:**
```java
// Source: Phase 2 decision (D-02)
// ThbwikiService.java

private List<ThbwikiTrack> parseTracks(Document doc) {
    List<ThbwikiTrack> tracks = new ArrayList<>();

    // Try musicTable first
    Elements rows = doc.select("#musicTable tr");
    if (rows.isEmpty()) {
        // Fallback to wikitable.musicTable
        rows = doc.select(".wikitable.musicTable tr");
    }

    for (Element row : rows) {
        ThbwikiTrack track = parseTrackRow(row);
        if (track != null) {
            tracks.add(track);
        }
    }
    return tracks;
}
```

### Pattern 3: Defensive Copy

**What:** Return immutable copies of collections from domain objects

**When to use:** Always, to prevent external mutation

**Example:**
```java
// Source: Phase 1 HIGH fix
// ThbwikiAlbum.java

@Override
public List<ThbwikiTrack> getTracks() {
    return List.copyOf(this.tracks);
}
```

### Anti-Patterns to Avoid
- **Throwing exceptions for parsing failures**: Use graceful degradation with logging instead
- **Mutating returned objects**: Always return copies or use immutable types
- **Hardcoding selectors without fallback**: Provide multiple selector strategies

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| HTML parsing | Custom regex or string manipulation | Jsoup | Handles malformed HTML, provides CSS selectors, well-tested |
| HTTP fetching | Raw HttpURLConnection | Hutool HttpRequest | Already in project, consistent API |
| Caching | ConcurrentHashMap manually | Caffeine | Thread-safe, TTL support, already in project |

**Key insight:** Jsoup is the standard Java HTML parsing library. It handles malformed HTML gracefully and provides a CSS selector API similar to browser DevTools.

## Common Pitfalls

### Pitfall 1: Empty Track List on Valid Page
**What goes wrong:** Parser returns empty tracks even when page has content
**Why it happens:** CSS selector doesn't match actual page structure
**How to avoid:**
- Use multiple fallback selectors: `#musicTable`, `.wikitable.musicTable`, `.wikitable`
- Validate expected elements exist before parsing
- Log selector failures with context

**Warning signs:** `log.warn("No tracks parsed...")` appears for valid albums

### Pitfall 2: Missing Original Source Info
**What goes wrong:** Track parsed but `originalSource` or `originalName` is null
**Why it happens:** Some tracks don't have `.ogmusic` or `.source` elements
**How to avoid:**
- Check for null before setting fields
- Some tracks legitimately have no original source info
- Log when original info is missing (INFO level, not warning)

**Warning signs:** Track objects with null `originalSource`

### Pitfall 3: Table Row Iteration Includes Header
**What goes wrong:** First row is table header, not a track
**Why it happens:** Simple `tr` selector includes header row
**How to avoid:**
- Skip rows where first cell has `th` element
- Use `tbody > tr` selector to exclude orphaned headers

**Warning signs:** First track has weird name like "曲名" or "Track"

## Code Examples

### Verified Parsing Logic

```java
// Source: Based on Phase 1 verified CSS selectors
// Parsing album name
Element titleElement = doc.selectFirst(".mw-page-title-main");
String albumName = titleElement != null ? titleElement.text() : "";

// Parsing tracks from music table
Elements trackRows = doc.select("#musicTable tr");
// or fallback
trackRows = doc.select(".wikitable.musicTable tr");

for (Element row : trackRows) {
    // Skip header rows (th element in first cell)
    if (row.selectFirst("td") == null) {
        continue;
    }

    // Extract track name (first td)
    String trackName = row.selectFirst("td").text();

    // Extract original source info
    Element ogmusic = row.selectFirst(".ogmusic");
    if (ogmusic != null) {
        Element source = ogmusic.selectFirst(".source");
        String sourceName = source != null ? source.text() : "";

        // Other content in ogmusic is original name
        String originalName = ogmusic.text().replace(sourceName, "").trim();
    }
}
```

### Expected HTML Structure

Based on Phase 1 verification, the expected structure:

```html
<!-- Album Title -->
<h1 id="firstHeading">
  <span class="mw-page-title-main">Satori Maiden</span>
</h1>

<!-- Track Table -->
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

### Edge Case Handling

```java
// Source: Pattern from Phase 2 decision (D-02)
private ThbwikiTrack parseTrackRow(Element row) {
    Elements cells = row.select("td");
    if (cells.isEmpty()) {
        return null;
    }

    ThbwikiTrack track = new ThbwikiTrack();

    // Track name: first cell
    track.setName(cells.get(0).text().trim());

    // Original source: second cell with .ogmusic
    if (cells.size() > 1) {
        Element ogmusic = cells.get(1).selectFirst(".ogmusic");
        if (ogmusic != null) {
            // Get first source as primary
            Element source = ogmusic.selectFirst(".source");
            if (source != null) {
                track.setOriginalSource(source.text().trim());
                track.setOriginalUrl(THBWIKI_BASE_URL + source.attr("href"));
            }
        }
    }

    return track;
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| String regex parsing | Jsoup CSS selectors | Phase 1 | More reliable, handles malformed HTML |
| Null returns on errors | Optional + empty list | Phase 2 | Clearer API, no NPE |

**Deprecated/outdated:**
- Manual HTML string manipulation - Use Jsoup selectors instead
- Throwing exceptions for non-critical failures - Use graceful degradation

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | CSS selectors verified in Phase 1 are still valid | Standard Stack | Parsing may fail silently |
| A2 | Track table uses `id="musicTable"` or `class="wikitable musicTable"` | Code Examples | Track parsing returns empty |
| A3 | Original source info uses `.ogmusic` and `.source` classes | Code Examples | Original source fields remain null |

**If this table is empty:** All claims in this research were verified or cited - no user confirmation needed.

## Open Questions

1. **THBWiki page access restriction**
   - What we know: Phase 1 verified OpenSearch API works; direct page fetches may be restricted
   - What's unclear: Whether album page HTML is accessible via HTTP
   - Recommendation: During implementation, verify album page fetches succeed; may need different User-Agent or approach

2. **Track table structure variations**
   - What we know: CSS selectors `.wikitable.musicTable` and `#musicTable` documented
   - What's unclear: Are there other table structures on different album pages?
   - Recommendation: Implement fallback selectors; create test HTML with multiple variants

3. **Original source extraction**
   - What we know: `.ogmusic` contains source links, `.source` class marks source anchor
   - What's unclear: How to extract "original name" vs "original source"
   - Recommendation: Based on example, original name is the page title, source is the game name

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Java 21 | Build & runtime | Yes | 21 | - |
| Maven 3.x | Build | Yes | (check mvn -v) | - |
| Jsoup 1.18.1 | HTML parsing | Yes | 1.18.1 | - |
| THBWiki site | Data source | Partial | - | Use sample HTML for testing |

**Missing dependencies with no fallback:**
- None identified

**Missing dependencies with fallback:**
- THBWiki site access (restricted) - Use sample HTML files for testing

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 (existing in project) |
| Config file | `pom.xml` (existing) |
| Quick run command | `mvn test -Dtest=ThbwikiServiceTest -x` |
| Full suite command | `mvn test -x` |

### Phase Requirements -> Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|--------------|
| ORIGINAL-01.2 | Parse track list from HTML | Unit | `mvn test -Dtest=ThbwikiServiceTest#parseTracks_*` | No |
| ORIGINAL-01.3 | Extract original source info | Unit | `mvn test -Dtest=ThbwikiServiceTest#parseOriginalSource_*` | No |

### Sampling Rate
- **Per task commit:** `mvn test -Dtest=ThbwikiServiceTest -x`
- **Per wave merge:** `mvn test -x`
- **Phase gate:** Full suite green before `/gsd-verify-work`

### Wave 0 Gaps
- [ ] `tongrenlu-dao/src/test/java/info/tongrenlu/service/ThbwikiServiceTest.java` - covers ORIGINAL-01.2, ORIGINAL-01.3
- [ ] `tongrenlu-dao/src/test/resources/thbwiki/sample-album.html` - sample HTML for testing
- [ ] `tongrenlu-dao/src/test/resources/thbwiki/sample-track-no-source.html` - edge case: no original source

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | No | N/A - public THBWiki pages |
| V3 Session Management | No | N/A - no sessions |
| V4 Access Control | No | N/A - public data |
| V5 Input Validation | Yes | Validate URL format before HTTP request |
| V6 Cryptography | No | N/A - no crypto needed |

### Known Threat Patterns for HTML Parsing

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| URL injection via album URL | Tampering | Validate URL matches THBIKI_BASE_URL pattern |
| Malformed HTML causing NPE | Denial | Null-check all parse results |
| Resource exhaustion (large HTML) | Denial | Limit document size or parse timeout |

### Input Validation Requirements

```java
// Validate URL before fetching
private boolean isValidThbwikiUrl(String url) {
    if (!StringUtils.hasText(url)) {
        return false;
    }
    return url.startsWith(THBWIKI_BASE_URL)
        && !url.contains("\n")
        && url.length() < 500;
}
```

## Sources

### Primary (HIGH confidence)
- Phase 1 context (01-CONTEXT.md) - Verified CSS selectors from actual THBWiki testing
- Project data models (ThbwikiTrack.java, ThbwikiAlbum.java) - Confirmed fields needed
- Phase 2 decisions (02-CONTEXT.md) - Implementation approach locked

### Secondary (MEDIUM confidence)
- Jsoup documentation - CSS selector API
- MediaWiki 1.39.10 behavior (from THBWiki main page meta tag)
- THBWiki robots.txt - Crawl rules and delay

### Tertiary (LOW confidence)
- Expected HTML structure - Based on Phase 1 description, not live verification
- Track table variations - Unknown if other structures exist

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - Jsoup and Hutool confirmed in project
- Architecture: HIGH - Direct parsing pattern follows Phase 2 decisions
- Pitfalls: MEDIUM - Based on general HTML parsing experience, not live THBWiki verification

**Research date:** 2026-04-14
**Valid until:** 2026-05-14 (30 days for stable topic)

## Research Blockers

**Unable to verify:**
- Actual THBWiki album page HTML structure - Site returns 404 for direct page fetches (may be network/region restriction)
- Real track table variations - No access to sample pages

**Mitigation:**
- Create sample HTML based on Phase 1 documented selectors
- Implement fallback selectors for robustness
- Test with Jsoup parsing of sample HTML

**Recommendation:** During implementation, verify with actual THBWiki pages if accessible. May need VPN or different network access to test against live site.

---

*Research completed: 2026-04-14*
*Ready for planning: Yes (noted limitations)*
