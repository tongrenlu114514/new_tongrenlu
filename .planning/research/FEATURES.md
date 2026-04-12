# Feature Research: Original Track Info Display

**Domain:** Doujin music library (同人音乐库)
**Project:** tongrenlu - 同人音乐库管理平台
**Researched:** 2026-04-13
**Confidence:** MEDIUM

## Feature Landscape

### Table Stakes (Users Expect These)

Features users assume exist in a doujin music library. Missing these = product feels incomplete.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Original track display in album detail | Core value prop: "了解歌曲原曲出处" | LOW | Show "原曲出处/原曲名称" format inline with track name |
| Original info linkability | THBWiki is authoritative source, users want to verify | LOW | Link to THBWiki page for the original track |
| Search by original source | Users know music by game, not artist | MEDIUM | Allow filtering albums/tracks by Touhou game (e.g., "红魔乡") |
| Batch status indicator | Admin needs to know match coverage | LOW | Show "X/Y tracks have original info" on album list |
| Match confidence display | Users want to know if match is reliable | LOW | Show match score or flag low-confidence matches |

### Differentiators (Competitive Advantage)

Features that set the product apart. Not required, but valuable for the specific doujin music niche.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| THBWiki integration | Authoritative source, single source of truth | MEDIUM | Use THBWiki API for fetching original track data |
| Original info tooltips | Space-efficient display, hover for details | LOW | Show full original info on hover instead of cluttering UI |
| Cross-reference links | Connect related tracks across albums | MEDIUM | Link to other arrangements of same original |
| Original game cover display | Visual identification of game | MEDIUM | Show game cover thumbnail next to original info |
| Batch import workflow | Efficient admin operation for large catalogs | MEDIUM | SSE progress updates for batch operations |

### Anti-Features (Commonly Requested, Often Problematic)

Features that seem good but create problems. Based on PROJECT.md constraints.

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|----------------|-------------|
| User-facing real-time search | "Let me search on the spot" | Creates API load, inconsistent results, complex UX | Admin batch operations ensure quality |
| Manual original info editing | "Match is wrong, let me fix it" | No source of truth, data drift over time | Trust THBWiki, re-fetch if wrong |
| Periodic re-matching | "Keep data fresh" | Unnecessary maintenance, may change valid matches | One-time authoritative fetch |
| Multiple data source support | "More coverage = better" | Inconsistent formats, maintenance burden | THBWiki is sufficient for Touhou |
| Per-track match review UI | "Review before saving" | Workflow complexity, admin overhead | Trust auto-match with confidence scores |

## Feature Dependencies

```
[Original Info Storage]
    └──requires──> [THBWiki API Integration]
                          └──requires──> [Data Normalization]

[Album Detail Display] ──enhances──> [Original Info Display]

[Admin Batch Operations] ──enhances──> [Original Info Display]
                                           └──requires──> [Original Info Storage]

[Search/Filter by Game] ──depends-on──> [Original Info Storage]
                                            └──requires──> [Normalized Source Field]
```

### Dependency Notes

- **Original Info Storage requires THBWiki API Integration:** Need API access before storage can be populated
- **Album Detail Display enhances Original Info Display:** Display features depend on data being present
- **Admin Batch Operations enhances Original Info Display:** Admin tooling populates data that users see
- **Search/Filter by Game depends on Normalized Source Field:** Must store game name in queryable format

## MVP Definition

### Launch With (v1)

Minimum viable product - what's needed to validate the concept.

- [x] **Original info storage** - Standardize `TrackBean.original` to "原曲出处/原曲名称" format
- [x] **THBWiki API integration** - Fetch original track data from thwiki.cc API
- [x] **Album detail display** - Show original info inline with track name in album page
- [x] **Admin batch fetch** - Button to trigger batch fetch for albums without original info

### Add After Validation (v1.x)

Features to add once core is working.

- [ ] **Match confidence display** - Show confidence score for auto-matched tracks
- [ ] **THBWiki link** - Add clickable link to THBWiki page for verified original info
- [ ] **Batch status dashboard** - Show coverage statistics across all albums

### Future Consideration (v2+)

Features to defer until product-market fit is established.

- [ ] **Search by original game** - Filter tracks/albums by Touhou game
- [ ] **Original game cover thumbnails** - Visual identification
- [ ] **Cross-reference to other arrangements** - "Other albums with this original"
- [ ] **Unmatched tracks report** - Identify high-value tracks that need manual review

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority |
|---------|------------|---------------------|----------|
| Original info storage format | HIGH | LOW | P1 |
| THBWiki API integration | HIGH | MEDIUM | P1 |
| Album detail display | HIGH | LOW | P1 |
| Admin batch fetch | HIGH | MEDIUM | P1 |
| Match confidence display | MEDIUM | LOW | P2 |
| THBWiki link | MEDIUM | LOW | P2 |
| Batch status dashboard | MEDIUM | LOW | P2 |
| Search by original game | LOW | MEDIUM | P3 |
| Game cover thumbnails | LOW | MEDIUM | P3 |
| Cross-references | LOW | HIGH | P3 |

**Priority key:**
- P1: Must have for launch
- P2: Should have, add when possible
- P3: Nice to have, future consideration

## THBWiki Data Format Reference

Based on analysis of thwiki.cc API responses:

### Original Track Template Format

THBWiki uses template-based format for original tracks:
```
{{原曲|游戏编号|曲号}}
```

Example from actual THBWiki data:
```
{{永夜抄音声名|3|10}} = 永夜抄 (Imperishable Night) + Track 10
{{花映塚音声名|3|4}} = 花映塚 (Phantasmagoria of Flower View) + Track 4
```

### Display Format

THBWiki displays this as:
```
原曲: 永夜抄 - 永夜抄 〜 Imperishable Night
原曲: 花映塚 - 少女が見た眼中的原罪と変化
```

### Recommended Storage Format

For our database (`TrackBean.original`):
```
格式: "原曲出处/原曲名称"
示例: "东方Project/永夜抄 〜 Imperishable Night"
```

This format:
- Is human-readable
- Separates source (game) from track name
- Can be split for filtering/search
- Matches THBWiki's canonical format

## Competitor Feature Analysis

| Feature | THBWiki | VocaDB | Our Approach |
|---------|---------|--------|--------------|
| Original track info | Wiki links | Database entries | "出处/曲名" stored field |
| Source linking | Internal wiki links | External links | Link to THBWiki |
| Batch operations | Manual wiki edits | Admin tools | Admin batch fetch |
| Match confidence | N/A (manual curation) | Vote-based | Auto-match with flag |
| Display format | Template expansion | Full page with metadata | Inline with track |

## Handling Missing/Unmatched Tracks

### Scenarios

| Scenario | Expected Behavior | Implementation |
|----------|-------------------|----------------|
| No match found | Show empty, log for review | Return null, increment "unmatched" counter |
| Low confidence match | Show with warning indicator | Store confidence score, display flag |
| Multiple matches | Auto-select best, log alternatives | Use similarity scoring |
| API unavailable | Fail gracefully, show last-known | Cache results, show "unavailable" state |
| Rate limited | Queue requests, show progress | Implement retry with backoff |

### Admin Visibility

- **Album list:** Show "X/Y tracks matched" badge
- **Album detail:** Highlight unmatched tracks in yellow/amber
- **Batch report:** Summary of matched/unmatched/skipped

## Sources

- THBWiki (thwiki.cc) API analysis - actual data structure verified via API calls
- THBWiki page format analysis - wikitext templates for original track display
- PROJECT.md - project constraints and requirements
- TrackBean.java - existing data model with `original` field

---

*Feature research for: Original track info display in doujin music library*
*Researched: 2026-04-13*
