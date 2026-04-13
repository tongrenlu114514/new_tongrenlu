---
phase: "02-html"
plan: "01"
type: execute
wave: 1
depends_on: []
files_modified:
  - tongrenlu-dao/src/main/java/info/tongrenlu/service/ThbwikiService.java
autonomous: true
requirements:
  - ORIGINAL-01.2
  - ORIGINAL-01.3
must_haves:
  truths:
    - "Given a THBWiki album URL, the system parses track list"
    - "Each track has originalSource and originalName extracted"
    - "Empty tracks result in warning log, not exception"
  artifacts:
    - path: "tongrenlu-dao/src/main/java/info/tongrenlu/service/ThbwikiService.java"
      provides: "HTML parsing for album tracks"
      exports:
        - "fetchAlbumDetail(String url)"
        - "parseAlbumDetail(Document, String)"
        - "parseTracks(Document)"
        - "parseTrackRow(Element)"
  key_links:
    - from: "ThbwikiService"
      to: "ThbwikiAlbum"
      via: "ThbwikiAlbum.setTracks()"
      pattern: "album.setTracks()"
    - from: "ThbwikiTrack"
      to: "parseTrackRow()"
      via: "track.setOriginalSource()"
      pattern: "track.setOriginal.*()"
---

<objective>
Implement HTML parsing layer to extract track list and original source information from THBWiki album pages.

Purpose: Complete the album detail fetching pipeline started in Phase 1 (search) by adding page HTML parsing.
Output: `ThbwikiAlbum` with populated `tracks` list containing `originalSource` and `originalName`.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
</execution_context>

<context>
@.planning/phases/02-html/02-CONTEXT.md
@.planning/phases/02-html/02-RESEARCH.md
@.planning/phases/02-html/02-VALIDATION.md
@tongrenlu-dao/src/main/java/info/tongrenlu/service/ThbwikiService.java
@tongrenlu-dao/src/main/java/info/tongrenlu/model/ThbwikiTrack.java
@tongrenlu-dao/src/main/java/info/tongrenlu/model/ThbwikiAlbum.java
</context>

<interfaces>
<!-- Key types and contracts the executor needs. Extracted from codebase. -->

From ThbwikiTrack.java:
```java
@Data
public class ThbwikiTrack {
    private String name;           // 曲目名称
    private String originalSource; // 原曲出处，如 "东方Project"
    private String originalName;    // 原曲名称，如 "永夜抄"
    private String originalUrl;    // THBWiki 链接
}
```

From ThbwikiAlbum.java:
```java
@Data
public class ThbwikiAlbum {
    private String name;
    private String url;
    private List<ThbwikiTrack> tracks = new ArrayList<>();

    public void addTrack(ThbwikiTrack track);
    @Override
    public List<ThbwikiTrack> getTracks();  // Returns defensive copy
}
```

From ThbwikiService.java (existing patterns):
```java
@Service
@RequiredArgsConstructor
@Slf4j
public class ThbwikiService {
    private static final String THBWIKI_BASE_URL = "https://thbwiki.cc";
    // ... existing methods
}
```
</interfaces>

<tasks>

<task type="auto">
  <name>Task 1: Add fetchAlbumDetail method with URL validation</name>
  <files>tongrenlu-dao/src/main/java/info/tongrenlu/service/ThbwikiService.java</files>
  <action>
Add a new public method `fetchAlbumDetail(String url)` that:

1. Validates URL format (per D-02, D-03 - return empty Optional on invalid input):
   - Not null/empty
   - Starts with THBWIKI_BASE_URL
   - Length < 500 chars
   - No newlines or control characters

2. Fetches page HTML using Hutool HttpRequest:
   - User-Agent: "tongrenlu/1.0 (同人音乐库管理)"
   - Timeout: 10000ms
   - Follow redirects

3. Parses HTML with Jsoup and delegates to parseAlbumDetail()

4. Returns Optional&lt;ThbwikiAlbum&gt; (empty on any failure, per D-02)

The method signature:
```java
public Optional<ThbwikiAlbum> fetchAlbumDetail(String url) {
    // URL validation
    // HTTP fetch
    // Jsoup.parse(html)
    // return parseAlbumDetail(doc, url)
}
```
  </action>
  <verify>
    <automated>cd tongrenlu-dao && mvn test -Dtest=ThbwikiServiceTest#fetchAlbumDetail_* -x 2>&1 | tail -20</automated>
  </verify>
  <done>
    fetchAlbumDetail() method exists with URL validation and HTTP fetch logic
    Returns Optional.empty() for invalid URLs
    Returns Optional.empty() on HTTP errors
  </done>
</task>

<task type="auto">
  <name>Task 2: Implement parseAlbumDetail method</name>
  <files>tongrenlu-dao/src/main/java/info/tongrenlu/service/ThbwikiService.java</files>
  <action>
Add private method `parseAlbumDetail(Document doc, String url)` that:

1. Extracts album name from page title:
   ```java
   String albumName = doc.selectFirst(".mw-page-title-main")
       .text();
   ```

2. Parses track list by calling parseTracks(doc)

3. Creates and populates ThbwikiAlbum:
   ```java
   ThbwikiAlbum album = new ThbwikiAlbum();
   album.setName(albumName);
   album.setUrl(url);
   album.setTracks(tracks);
   ```

4. Logs warning if tracks.isEmpty() (per D-02)

5. Returns Optional.of(album)

Method signature:
```java
private Optional<ThbwikiAlbum> parseAlbumDetail(Document doc, String url) {
    // Extract album name
    // Parse tracks
    // Create album with tracks
    // Return Optional.of(album)
}
```
  </action>
  <verify>
    <automated>cd tongrenlu-dao && mvn test -Dtest=ThbwikiServiceTest#parseAlbumDetail_* -x 2>&1 | tail -20</automated>
  </verify>
  <done>
    parseAlbumDetail() extracts album name from .mw-page-title-main
    Creates ThbwikiAlbum with name, url, and tracks
    Logs warn if tracks is empty
  </done>
</task>

<task type="auto">
  <name>Task 3: Implement parseTracks and parseTrackRow methods</name>
  <files>tongrenlu-dao/src/main/java/info/tongrenlu/service/ThbwikiService.java</files>
  <action>
Add two private methods:

### parseTracks(Document doc)

1. Select track rows using multiple CSS selector strategies (fallback pattern):
   ```java
   Elements rows = doc.select("#musicTable tr");
   if (rows.isEmpty()) {
       rows = doc.select(".wikitable.musicTable tr");
   }
   if (rows.isEmpty()) {
       rows = doc.select(".wikitable tr");
   }
   ```

2. Skip header rows (rows without td element)

3. Parse each row with parseTrackRow()

4. Return List&lt;ThbwikiTrack&gt;

### parseTrackRow(Element row)

1. Extract all td cells:
   ```java
   Elements cells = row.select("td");
   if (cells.isEmpty()) {
       return null;
   }
   ```

2. Create ThbwikiTrack:
   ```java
   ThbwikiTrack track = new ThbwikiTrack();
   track.setName(cells.get(0).text().trim());
   ```

3. Extract original source from second cell (index 1):
   ```java
   if (cells.size() > 1) {
       Element ogmusic = cells.get(1).selectFirst(".ogmusic");
       if (ogmusic != null) {
           Element source = ogmusic.selectFirst(".source");
           if (source != null) {
               track.setOriginalSource(source.text().trim());
               track.setOriginalUrl(THBWIKI_BASE_URL + source.attr("href"));
               // Original name is the text content of ogmusic minus source name
               String originalName = ogmusic.text().replace(source.text()).trim();
               track.setOriginalName(originalName);
           }
       }
   }
   ```

4. Return track (never null - return empty track with name only if no original info)

5. Import jsoup classes: `org.jsoup.nodes.Document`, `org.jsoup.nodes.Element`, `org.jsoup.select.Elements`
  </action>
  <verify>
    <automated>cd tongrenlu-dao && mvn test -Dtest=ThbwikiServiceTest#parseTracks_* -x 2>&1 | tail -20</automated>
  </verify>
  <done>
    parseTracks() uses fallback selectors: #musicTable, .wikitable.musicTable, .wikitable
    parseTracks() skips header rows (no td element)
    parseTrackRow() extracts name from first td
    parseTrackRow() extracts originalSource from .ogmusic .source
    parseTrackRow() extracts originalUrl from source href
    parseTrackRow() extracts originalName by removing source text
    Returns track even if no original info (graceful degradation per D-02)
  </done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| client→THBWiki | Untrusted network, external HTTP request |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-2-01 | Tampering | fetchAlbumDetail(url) | mitigate | Validate URL starts with THBWIKI_BASE_URL, length < 500, no control chars |
| T-2-02 | Denial | parseTrackRow(row) | mitigate | Null-check all selectFirst() results, return empty track on parse failure |
| T-2-03 | Denial | HTTP fetch | accept | 10s timeout, catch all exceptions, return empty Optional |
</threat_model>

<verification>
```bash
# Quick test
mvn test -Dtest=ThbwikiServiceTest -pl tongrenlu-dao -x

# Full suite
mvn test -pl tongrenlu-dao -x
```
</verification>

<success_criteria>
1. ThbwikiService has fetchAlbumDetail(String url) method
2. fetchAlbumDetail returns Optional&lt;ThbwikiAlbum&gt; with populated tracks list
3. Each ThbwikiTrack has: name, originalSource, originalName, originalUrl (may be null for tracks without original info)
4. Empty tracks list logs warning but does not throw exception
5. Invalid URLs return Optional.empty()
6. HTTP errors return Optional.empty()
7. Tests pass with sample HTML
</success_criteria>

<output>
After completion, create `.planning/phases/02-html/02-01-SUMMARY.md`
</output>
