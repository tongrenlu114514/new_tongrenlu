# Pitfalls Research: THBWiki Web Scraping for Music Track Information

**Domain:** Wiki scraping for Touhou/doujin music metadata
**Researched:** 2026-04-13
**Confidence:** MEDIUM (based on domain knowledge of wiki scraping, CJK text processing, and music metadata matching; web search was blocked)

## Executive Summary

Scraping THBWiki for music track "original" information (the source game and track name) involves several domain-specific pitfalls that can cause data corruption, matching failures, or service disruption. The core challenges stem from: (1) HTML structure fragility, (2) CJK character normalization complexity, (3) fuzzy matching limitations for Japanese/Chinese music titles, and (4) rate limiting/IP blocking risks.

## Critical Pitfalls

### Pitfall 1: HTML Structure Fragility

**What goes wrong:**
Scraper breaks silently when THBWiki updates page structure, causing partial data extraction or wrong field mapping.

**Why it happens:**
- THBWiki uses MediaWiki with custom templates and CSS classes
- Page structure changes without notice (CSS class renaming, HTML restructuring)
- XPath/CSS selectors become stale and match wrong elements
- Empty fields are silently skipped instead of being flagged as errors

**How to avoid:**
1. Use semantic selectors (data attributes, specific CSS classes) instead of positional selectors
2. Implement validation: verify extracted fields match expected patterns before saving
3. Log raw HTML snippets for debugging failed extractions
4. Build a health check endpoint that tests scraping against known pages

**Warning signs:**
- Extract count drops significantly (from 100 tracks to 50)
- Fields suddenly empty for no apparent reason
- HTML parser throwing exceptions in logs

**Phase to address:**
Phase 1 (Scraping Service) - Build resilient extraction layer

---

### Pitfall 2: CJK Character Normalization Failures

**What goes wrong:**
Song names fail to match because of subtle character differences between local data and THBWiki.

**Why it happens:**
- Full-width vs half-width character differences: `．` vs `.`
- Japanese vs Chinese simplified vs traditional characters: `音楽` vs `音乐`
- Space normalization: `BAD APPLE` vs `BADAPPLE` vs `BAD　APPLE`
- Special characters: `!` vs `！`, `?` vs `？`
- Line break codes in titles: `\n`, `\r`, zero-width spaces
- Unicode normalization (NFC vs NFD): combining characters vs precomposed

**How to avoid:**
1. Create a normalization pipeline that runs before any comparison:
   ```
   lowercase → fullwidth to halfwidth → trim whitespace →
   normalize unicode (NFC) → remove special chars → standardize spaces
   ```
2. Use Apache Commons Lang3 `StringUtils.normalizeSpace()` and `strip()`
3. Test normalization against known pairs of equivalent strings from both sources

**Warning signs:**
- 30-40% match rate when it should be 70-80%
- Specific songs always failing (suggesting character encoding issues)
- Matching works in dev but fails in production (encoding configuration difference)

**Phase to address:**
Phase 2 (Matching Algorithm) - Implement robust text normalization

---

### Pitfall 3: Over-Matching / False Positive Matches

**What goes wrong:**
Wrong original information assigned to tracks due to overly aggressive fuzzy matching.

**Why it happens:**
- Short song names (2-3 characters) have many possible matches
- Common words like "Intro", "Track 1", "Bonus" match across albums
- Fuzzy matching threshold too permissive
- No disambiguation based on album context or artist

**How to avoid:**
1. Set minimum confidence threshold (e.g., 85%) and flag matches below for review
2. Consider album context in matching:
   - Same artist's previous works
   - Album release year (THBWiki has dates)
   - Track sequence position
3. Implement a "do not match" list for generic terms
4. Log all matches with confidence scores for quality analysis

**Warning signs:**
- Same original track assigned to many different local songs
- Original field contains obviously wrong values
- User complaints about incorrect source information

**Phase to address:**
Phase 2 (Matching Algorithm) - Tune confidence thresholds and disambiguation

---

### Pitfall 4: Rate Limiting and IP Blocking

**What goes wrong:**
THBWiki blocks requests or returns 429/403 errors, halting batch operations.

**Why it happens:**
- Too many requests per second (bulk scraping without delays)
- No respect for robots.txt directives
- Request patterns triggering anti-bot detection
- Server-side rate limiting kicks in

**How to avoid:**
1. Implement polite scraping with delays (2-5 seconds between requests)
2. Check and respect robots.txt before scraping
3. Add jitter to request intervals to avoid predictable patterns
4. Implement exponential backoff on 429/503 responses
5. Use a proxy rotation strategy if operating at scale
6. Consider caching search results to avoid redundant requests

**Warning signs:**
- HTTP 429 (Too Many Requests) responses appearing in logs
- 403 Forbidden on previously working endpoints
- Response times suddenly increasing
- Search results becoming empty despite valid queries

**Phase to address:**
Phase 1 (Scraping Service) - Build rate limiting and retry logic

---

### Pitfall 5: Incomplete THBWiki Data

**What goes wrong:**
THBWiki lacks the specific track, resulting in no match even for valid songs.

**Why it happens:**
- New releases not yet added to THBWiki
- Arrange tracks not in the database
- Rare or limited edition albums missing
- THBWiki has gaps in coverage for some circle/artist discographies

**How to avoid:**
1. Design graceful degradation: store as "no match" with reason
2. Do not block batch processing for missing entries
3. Provide manual review workflow for unmatched tracks
4. Periodically retry unmatched tracks (albums may be added later)

**Warning signs:**
- Specific artist/circle albums consistently unmatched
- Higher-than-expected rate of "no match" for recent releases
- User feedback about missing track information

**Phase to address:**
Phase 2 (Storage) - Design "no match" state handling

---

### Pitfall 6: THBWiki Search API Limitations

**What goes wrong:**
Using wrong search endpoint leads to poor results or data loss.

**Why it happens:**
- THBWiki may not have a public API; scraping search results pages
- Search syntax differences (MediaWiki vs standard search)
- Pagination limits causing incomplete results
- Search result ordering varies (relevance, alphabetical, date)

**How to avoid:**
1. Study THBWiki's search page structure before implementing
2. Test search with edge cases: special characters, partial names, alternate spellings
3. Handle pagination correctly (don't assume first page has all results)
4. Consider direct page scraping for known albums instead of search

**Warning signs:**
- Search returns fewer results than expected
- Some known albums never appearing in search results
- Inconsistent results between identical queries

**Phase to address:**
Phase 1 (Scraping Service) - Test search coverage thoroughly

---

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| Use simple string equality for matching | Fast implementation | Misses similar titles | Never (CJK requires normalization) |
| Hardcode CSS selectors | Quick to start | Breaks on site updates | Only for throwaway prototypes |
| No rate limiting | Fast testing | IP ban, service disruption | Never |
| Skip validation on extracted data | Saves time now | Silent data corruption | Never for production |
| Ignore robots.txt | More data available | Legal risk, ethical violation | Never |
| No retry logic | Simpler code | Failed batches, lost work | Never for external APIs |

---

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| THBWiki search | Assuming exact match works | Always normalize both query and results |
| THBWiki page | Hardcoding page URL structure | Use relative paths, handle redirects |
| Database | Storing raw HTML in database | Parse and store structured data only |
| Batch job | Processing without checkpoints | Save progress periodically, resume on failure |
| HTTP client | Not setting proper headers | Include User-Agent, Accept-Language |
| Encoding | Assuming UTF-8 everywhere | Explicitly set encoding in HTTP requests |

---

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| N+1 requests (one per track) | Processing 1000 tracks takes hours | Batch search queries | Above 100 tracks |
| In-memory HTML parsing | OutOfMemoryError | Stream parsing, limit DOM size | Large page sets |
| No caching | Repeated requests for same data | Cache search results (24hr TTL) | Any repeated operations |
| Single-threaded scraping | Very slow batch jobs | Parallel workers with rate limiting | Above 500 tracks |

---

## Security Mistakes

| Mistake | Risk | Prevention |
|---------|------|------------|
| Ignoring robots.txt | Legal/ethical violation, IP ban | Parse and respect robots.txt |
| Hardcoding THBWiki URLs | Brittleness to site changes | Use configuration, not hardcoded strings |
| No input validation on search terms | Injection attacks if passing to API | Sanitize all external input |
| Logging raw API responses | Data exposure in logs | Sanitize before logging |
| Not using HTTPS | Man-in-the-middle attacks | Always use HTTPS for external APIs |

---

## UX Pitfalls

| Pitfall | User Impact | Better Approach |
|---------|-------------|-----------------|
| Silent failures in batch jobs | Admin doesn't know what failed | Real-time progress + error report |
| No retry mechanism | "Why didn't my album get matched?" | Automatic retry with exponential backoff |
| Matching without confidence scores | Can't trust results | Show match confidence, flag low scores |
| Blocking admin interface during scraping | Can't do other tasks | Background processing with SSE updates |

---

## "Looks Done But Isn't" Checklist

- [ ] **Scraping:** Extracts data successfully - but is it the correct fields? Verify against known track pages
- [ ] **Matching:** Algorithm returns results - but are they accurate? Spot-check random samples
- [ ] **Storage:** Data saves to database - but is it the right format? Validate "source/title" format
- [ ] **Batch job:** Completes without errors - but did it process all items? Log counts before/after
- [ ] **CJK handling:** Works for test data - but have you tested full-width chars, unicode edge cases?
- [ ] **Rate limiting:** Processes quickly - but can you sustain it without blocking? Run extended test

---

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| Wrong matches applied | HIGH | Backup before update; implement rollback that restores original field |
| IP blocked | MEDIUM | Wait (hours to days), implement rate limiting, use proxy if needed |
| Data corruption from parser bug | HIGH | Re-scrape affected albums; have raw response cache |
| Lost batch progress | MEDIUM | Implement checkpoint system; resume from last successful item |
| Missing fields after update | MEDIUM | Re-run scraping for affected items; have validation layer catch earlier |

---

## Pitfall-to-Phase Mapping

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| HTML Structure Fragility | Phase 1: Scraping Service | Run health check against known pages after any THBWiki update |
| CJK Character Normalization | Phase 2: Matching Algorithm | Unit tests with known equivalent pairs |
| Over-Matching / False Positives | Phase 2: Matching Algorithm | Sample validation, compare against manual matches |
| Rate Limiting and IP Blocking | Phase 1: Scraping Service | Extended load test with monitoring |
| Incomplete THBWiki Data | Phase 2: Storage | Track "no match" rate, investigate patterns |
| THBWiki Search API Limitations | Phase 1: Scraping Service | Test coverage for edge cases, pagination |
| Data Corruption | Phase 2: Validation Layer | Pre-save validation, backup before bulk updates |
| N+1 Performance | Phase 1: Batch Optimization | Performance test with 1000+ tracks |

---

## Phase-Specific Warnings

### Phase 1: Scraping Service (ORIGINAL-01)
**Highest Risk Pitfalls:**
1. Rate limiting - implement immediately, not as afterthought
2. HTML fragility - use resilient selectors, add validation
3. CJK normalization - build normalization layer early

**Deeper Research Needed:**
- THBWiki specific HTML structure (CSS classes, templates)
- robots.txt contents and what is allowed
- Available search endpoints and their behavior

### Phase 2: Matching Algorithm (ORIGINAL-02)
**Highest Risk Pitfalls:**
1. Over-matching - set conservative thresholds
2. CJK edge cases - comprehensive test suite needed
3. Empty/no-match handling - design graceful degradation

**Deeper Research Needed:**
- Touhou track naming conventions and common variations
- Album context features available from THBWiki

### Phase 3: Storage & Admin (ORIGINAL-03, ORIGINAL-04)
**Highest Risk Pitfalls:**
1. Batch interruption - checkpoint system essential
2. Wrong data applied - validation before save
3. User experience - real-time progress, not blocking

**Deeper Research Needed:**
- Existing Original field format for migration
- Admin UI requirements for batch operations

---

## Sources

- Domain knowledge: Web scraping best practices for wiki sites
- MediaWiki scraping patterns from common scraping frameworks
- CJK text normalization research
- Music metadata matching challenges (MusicBrainz, Discogs integration learnings)
- Touhou Project / doujin music metadata conventions

*Note: Web search was blocked during research. Confidence level MEDIUM. Recommend validation against actual THBWiki site structure before implementation.*

---
*Pitfalls research for: THBWiki web scraping*
*Researched: 2026-04-13*
