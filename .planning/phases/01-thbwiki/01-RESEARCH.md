# Project Research Summary

**Project:** tongrenlu - 同人音乐库管理平台
**Domain:** Doujin music library with THBWiki integration
**Researched:** 2026-04-13
**Confidence:** MEDIUM

## Executive Summary

This project involves adding "Original Track Info Display" functionality to a doujin music library (同人音乐库). The feature allows displaying the original Touhou game source and track name for arranged doujin music tracks by scraping THBWiki (thwiki.cc). Experts build this by implementing a dedicated scraping service in the existing Spring Boot multi-module architecture, using SSE streaming for batch operations, and applying robust CJK text normalization to handle character encoding differences between the database and THBWiki.

The recommended approach follows established patterns from existing CloudMusic integration: create `ThbwikiService` in `tongrenlu-dao` with `ThbwikiJob` in `tongrenlu-tool` for SSE streaming batch operations. Critical risks include HTML structure fragility (scraper breaks on site updates), CJK normalization complexity (full-width vs half-width characters, unicode normalization), and rate limiting/IP blocking from THBWiki. Mitigation requires resilient selectors with validation, comprehensive text normalization pipeline, and polite scraping with delays.

## Key Findings

### Recommended Stack

The project uses an existing Java 21 + Spring Boot 3.4.3 multi-module Maven architecture. New dependencies for this feature include Jsoup 1.18.1 for HTML parsing and Caffeine for in-memory caching.

**Core technologies:**
- **Java 21 + Spring Boot 3.4.3** — Backend framework, already configured
- **MyBatis Plus 3.5.11** — ORM for data persistence, already in use
- **Spring WebFlux** — Reactive streams for SSE streaming batch jobs, already available in tongrenlu-tool
- **Hutool 5.8.40** — HTTP client (cn.hutool.http.HttpRequest), consistent with existing CloudMusic integration
- **Jsoup 1.18.1** — HTML parsing for THBWiki scraping, NEW dependency needed
- **Caffeine** — In-memory cache with TTL for scraped results, NEW dependency needed

### Expected Features

**Must have (table stakes):**
- Original track display in album detail — Show "原曲出处/原曲名称" format inline with track name
- THBWiki linkability — Clickable link to THBWiki page for verified original info
- Batch status indicator — Show "X/Y tracks have original info" on album list
- Match confidence display — Show match score or flag low-confidence matches

**Should have (competitive):**
- THBWiki API integration — Fetch original track data from thwiki.cc API
- Original info tooltips — Space-efficient display, hover for details
- Admin batch fetch workflow — SSE progress updates for batch operations
- Match confidence scoring — Flag low-confidence matches for review

**Defer (v2+):**
- Search by original game — Filter tracks/albums by Touhou game
- Original game cover thumbnails — Visual identification
- Cross-reference links — Connect related tracks across albums

### Architecture Approach

The recommended architecture follows the existing CloudMusic integration pattern: a dedicated service class in `tongrenlu-dao` for HTTP fetching and HTML parsing, with controller endpoints in `tongrenlu-tool` for admin batch operations. Async batch operations leverage the existing WebFlux SSE pattern from `PlaylistImportJob`. The scraping flow uses Hutool HttpRequest (consistent with existing code) with Jsoup for HTML parsing, Caffeine cache for search results, and rate limiting via `delayElements()` or `Thread.sleep()`.

**Major components:**
1. **ThbwikiService** (tongrenlu-dao) — Core scraping logic: HTTP fetching, HTML parsing, matching algorithm, cache management
2. **ThbwikiJob** (tongrenlu-tool) — SSE streaming batch job with progress tracking and checkpoint resume capability
3. **OriginalCacheService** (tongrenlu-dao) — In-memory Caffeine cache with 24hr TTL for scraped results

### Critical Pitfalls

1. **HTML Structure Fragility** — Scraper breaks silently when THBWiki updates page structure. Prevention: Use semantic selectors with data attributes instead of positional selectors; implement validation on extracted fields; log raw HTML snippets for debugging.

2. **CJK Character Normalization Failures** — Song names fail to match due to full-width vs half-width differences, Japanese vs Chinese characters, unicode normalization. Prevention: Build normalization pipeline (lowercase, fullwidth-to-halfwidth, NFC unicode, trim spaces) before any comparison.

3. **Over-Matching / False Positive Matches** — Wrong original info assigned due to overly aggressive fuzzy matching on short titles. Prevention: Set minimum 85% confidence threshold; consider album context in matching; implement "do not match" list for generic terms.

4. **Rate Limiting and IP Blocking** — THBWiki blocks requests, halting batch operations. Prevention: Implement 2-5 second delays between requests; exponential backoff on 429 responses; respect robots.txt.

5. **Incomplete THBWiki Data** — No match found for valid songs not yet in THBWiki. Prevention: Design graceful degradation storing "no match" with reason; provide manual review workflow; periodically retry unmatched tracks.

## Implications for Roadmap

Based on research, suggested phase structure:

### Phase 1: Scraping Service Foundation
**Rationale:** This comes first because all downstream features depend on having a working scraping service. It also addresses the highest-risk pitfalls (rate limiting, HTML fragility) before they're embedded in batch logic.

**Delivers:** ThbwikiService with basic search and HTML parsing; OriginalCacheService with Caffeine; rate limiting implementation; basic validation layer

**Addresses:** THBWiki API integration (P1), Original info storage format (P1)

**Avoids:** HTML Structure Fragility, Rate Limiting and IP Blocking

**Research flag:** Needs validation of actual THBWiki site structure, robots.txt contents, and search endpoint behavior before implementation.

### Phase 2: Matching Algorithm & Normalization
**Rationale:** Matching logic depends on having scraping service working. CJK normalization must be built early to avoid retrofitting. Conservative thresholds prevent data corruption.

**Delivers:** CJK normalization pipeline; fuzzy matching algorithm with confidence scoring; "no match" state handling; match logging for quality analysis

**Addresses:** Match confidence display (P2), Search by original game (P3 future enabler)

**Avoids:** CJK Character Normalization Failures, Over-Matching / False Positives, Incomplete THBWiki Data

### Phase 3: Admin Batch Operations (SSE Streaming)
**Rationale:** Follows PlaylistImportJob pattern which is well-documented in codebase. Batch operations require stable scraping and matching first.

**Delivers:** ThbwikiJob with SSE streaming; progress tracking with checkpoint resume; admin REST endpoints; real-time progress feedback

**Addresses:** Admin batch fetch (P1), Batch status dashboard (P2)

**Avoids:** Silent batch failures, blocking admin interface

### Phase 4: Album Detail Display Integration
**Rationale:** Final integration phase connects all components to user-facing features. Can proceed in parallel once service and job are stable.

**Delivers:** Original info display in album detail page; THBWiki link rendering; match confidence indicator; batch status on album list

**Addresses:** Original track display in album detail (P1), THBWiki link (P2), Original info tooltips (P2)

### Phase Ordering Rationale

- **Scraping before Matching before Batch before Display** — Clear dependency chain: cannot match without scraped data; cannot batch without stable matching; cannot display without data to show
- **Normalization early, not late** — CJK edge cases embedded in matching logic; easier to build correctly from start than retrofit
- **Rate limiting first** — IP ban is catastrophic; must establish polite scraping pattern before any batch testing
- **Checkpoint system early** — Batch interruption recovery essential for admin trust; build checkpointing into ThbwikiJob from start

### Research Flags

Phases likely needing deeper research during planning:
- **Phase 1:** THBWiki-specific HTML structure validation required; recommend actual site testing before implementing selectors
- **Phase 2:** Touhou track naming conventions and common variations; CJK normalization edge cases need comprehensive test suite
- **Phase 4:** Frontend display format for original info; tooltip implementation approach for existing vanilla JS codebase

Phases with standard patterns (skip research-phase):
- **Phase 3:** SSE streaming pattern already demonstrated in PlaylistImportJob; straightforward adaptation

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | Existing codebase analysis; confirmed dependencies in pom.xml |
| Features | MEDIUM | Domain knowledge of doujin music + web search blocked during research |
| Architecture | MEDIUM | Follows established patterns from CloudMusic integration; new Jsoup/Caffeine pattern well-documented |
| Pitfalls | MEDIUM | Domain knowledge of web scraping, CJK text, and music metadata; web search blocked |

**Overall confidence:** MEDIUM

### Gaps to Address

- **THBWiki site structure:** Need to verify actual HTML structure, CSS classes, and search endpoint behavior before implementing selectors
- **robots.txt compliance:** Need to verify THBWiki scraping is allowed before production deployment
- **CJK normalization edge cases:** Need comprehensive test cases with actual equivalent string pairs from both sources
- **Matching algorithm precision:** Need to determine optimal confidence threshold through experimentation with real data

## Sources

### Primary (HIGH confidence)
- Existing codebase analysis (ARCHITECTURE.md, STACK.md from codebase) — Verified pom.xml dependencies, existing service patterns
- PlaylistImportJob pattern — Working SSE streaming implementation in codebase

### Secondary (MEDIUM confidence)
- THBWiki API analysis (FEATURES.md) — Actual API response structure from analysis
- THBWiki page format analysis — Wikitext templates documented
- Domain knowledge: Web scraping best practices for wiki sites
- Domain knowledge: CJK text normalization research
- Domain knowledge: Music metadata matching challenges (MusicBrainz, Discogs integration learnings)

### Tertiary (LOW confidence)
- THBWiki specific CSS selectors — Needs actual site validation
- Matching algorithm parameters — Needs experimentation with real data

---

*Research completed: 2026-04-13*
*Ready for roadmap: yes*
