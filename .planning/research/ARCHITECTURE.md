# Architecture Research: THBWiki Integration

**Domain:** Web Scraping Integration for Multi-Module Spring Boot Application
**Project:** tongrenlu (同人音乐库管理)
**Researched:** 2026-04-13
**Confidence:** MEDIUM

## Executive Summary

THBWiki scraping should be implemented as a dedicated service in `tongrenlu-dao` with controller endpoints in `tongrenlu-tool`, following the existing CloudMusic integration pattern. The scraping service should use Hutool HttpRequest (consistent with existing code) paired with Jsoup for HTML parsing. Async batch operations should leverage the existing WebFlux SSE pattern already demonstrated in PlaylistImportJob.

## Existing Architecture Analysis

### Current Module Structure

```
tongrenlu (parent pom)
├── tongrenlu-dao/           # Data layer
│   ├── domain/              # Entity beans (MyBatis-Plus)
│   ├── mapper/              # MyBatis mapper interfaces
│   ├── model/               # External API response DTOs
│   └── service/             # Business logic services
├── tongrenlu-web/           # Public API (port 8443)
│   └── www/                 # REST controllers
└── tongrenlu-tool/          # Admin/Batch (port 8080)
    ├── Controllers          # Admin REST endpoints
    └── Jobs                 # Batch processing (SSE streaming)
```

### Existing Patterns to Follow

| Pattern | Location | Description |
|---------|----------|-------------|
| HTTP Client | `HomeMusicService` | Uses `cn.hutool.http.HttpRequest` for CloudMusic API |
| Async Batch | `PlaylistImportJob` | Uses WebFlux `Flux<ServerSentEvent>` with `Schedulers.boundedElastic()` |
| Progress Persistence | `PlaylistImportJob` | File-based checkpoint for resume capability |
| Rate Limiting | `HomeMusicService` | `Thread.sleep(500)` between requests |

### Key Dependencies Already Available

**tongrenlu-tool/pom.xml** includes:
- `hutool-all:5.8.40` - HTTP client
- `spring-boot-starter-webflux` - Reactive streams for SSE
- `commons-io:2.18.0` - File operations for progress tracking
- `guava:33.3.1-jre` - Utility library

## Recommended Architecture

### System Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                      tongrenlu-tool (Admin Layer)                    │
├─────────────────────────────────────────────────────────────────────┤
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐  │
│  │ AdminController  │  │ AdminController   │  │ ThbwikiJob       │  │
│  │ (Artist)         │  │ (Album)           │  │ (SSE Streaming)  │  │
│  └────────┬─────────┘  └────────┬─────────┘  └────────┬─────────┘  │
│           │                    │                    │              │
├───────────┴────────────────────┴────────────────────┴──────────────┤
│                        tongrenlu-dao (Service Layer)                 │
├─────────────────────────────────────────────────────────────────────┤
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐  │
│  │ HomeMusicService │  │ ArtistService    │  │ ThbwikiService   │  │
│  │ (CloudMusic)     │  │ (Artist CRUD)    │  │ (THBWiki Scraping)│ │
│  └────────┬─────────┘  └────────┬─────────┘  └────────┬─────────┘  │
│           │                    │                    │              │
├───────────┴────────────────────┴────────────────────┴──────────────┤
│                         Data Access Layer                            │
├─────────────────────────────────────────────────────────────────────┤
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐  │
│  │ ArticleMapper    │  │ TrackMapper      │  │ OriginalMapper   │  │
│  │ (Album)          │  │ (Tracks)         │  │ (Original Info)  │  │
│  └──────────────────┘  └──────────────────┘  └──────────────────┘  │
└─────────────────────────────────────────────────────────────────────┘
                              │
                              ▼
                    ┌──────────────────┐
                    │     MySQL        │
                    │  (tongrenlu)     │
                    └──────────────────┘
```

### Component Boundaries

| Component | Module | Responsibility | Public API |
|-----------|--------|---------------|------------|
| ThbwikiService | tongrenlu-dao | HTTP fetching, HTML parsing, matching logic | `searchOriginal(query)`, `batchSearch(trackIds)` |
| ThbwikiJob | tongrenlu-tool | SSE streaming, progress tracking, batch orchestration | `/api/thbwiki/import` |
| OriginalCacheService | tongrenlu-dao | In-memory cache for scraped results | `getCached(key)`, `put(key, value)` |
| ThbwikiController | tongrenlu-tool | Admin REST endpoints | `GET /api/thbwiki/search`, `POST /api/thbwiki/batch` |

### Data Flow

#### Single Search Flow

```
1. [Admin Request] → /api/thbwiki/search?q={keyword}
2. [Controller] → ThbwikiService.searchOriginal(keyword)
3. [Service] → Check OriginalCacheService for cached result
4. [Cache Miss] → HttpRequest.get(thbwiki.cc/search)
5. [Service] → Jsoup.parse(html) → Extract results
6. [Service] → Store in cache (TTL: 24h)
7. [Service] → Return list of OriginalResult
8. [Controller] → ResponseEntity.ok(results)
```

#### Batch Import Flow (SSE Streaming)

```
1. [Admin Request] POST /api/thbwiki/import with albumIds
2. [Controller] → Return Flux<ServerSentEvent> (SSE stream)
3. [Job] → Flux.defer() {
     - Read progress checkpoint
     - Process remaining tracks with concatMap
     - Each track: search → match → update DB
     - Emit SSE event after each track
     - Save progress checkpoint
   }
4. [Client] → Receives streaming progress via EventSource
```

## Recommended Project Structure

```
tongrenlu-dao/src/main/java/info/tongrenlu/
├── service/
│   └── ThbwikiService.java          # Core scraping logic
├── service/OriginalCacheService.java # Cache management
├── domain/
│   └── OriginalBean.java            # Entity for original info (optional)
├── model/
│   ├── ThbwikiSearchResult.java     # Search result DTO
│   └── ThbwikiTrackInfo.java        # Track info DTO
└── mapper/
    └── OriginalMapper.java          # For storing scraped results

tongrenlu-tool/src/main/java/info/tongrenlu/
├── ThbwikiController.java           # Admin REST endpoints
└── ThbwikiJob.java                  # SSE streaming batch job
```

### New Dependencies Required

Add to `tongrenlu-dao/pom.xml`:

```xml
<dependency>
    <groupId>org.jsoup</groupId>
    <artifactId>jsoup</artifactId>
    <version>1.18.1</version>
</dependency>
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
</dependency>
```

## Architectural Patterns

### Pattern 1: External API Service Layer

**What:** Dedicated service class in `tongrenlu-dao` encapsulating all external API interactions.

**When to use:** When integrating with third-party services (CloudMusic, THBWiki).

**Implementation:**
```java
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class ThbwikiService {

    private static final String THBWIKI_BASE = "https://thbwiki.cc";
    private static final Duration REQUEST_DELAY = Duration.ofMillis(1000);

    private final OriginalCacheService cacheService;

    public List<ThbwikiSearchResult> searchOriginal(String query) {
        // 1. Check cache
        // 2. Fetch via Hutool HttpRequest
        // 3. Parse HTML with Jsoup
        // 4. Cache and return
    }

    private String fetchWithRetry(String url, int maxRetries) {
        // Retry logic with exponential backoff
    }
}
```

**Trade-offs:**
- PRO: Centralizes HTTP logic, easy to add retry/caching
- CON: Adds coupling to external service

### Pattern 2: SSE Streaming Batch Job

**What:** WebFlux-based streaming endpoint for long-running batch operations.

**When to use:** For batch scraping operations that may take minutes.

**Implementation (follow existing PlaylistImportJob pattern):**
```java
@GetMapping(value = "/api/thbwiki/import", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<String>> batchImport(@RequestParam List<Long> albumIds) {
    return Flux.defer(() -> {
        List<TrackBean> tracks = loadTracks(albumIds);
        return Flux.fromIterable(tracks)
            .concatMap(this::processTrack)
            .delayElements(Duration.ofMillis(1000))  // Rate limit
            .subscribeOn(Schedulers.boundedElastic());
    });
}
```

**Trade-offs:**
- PRO: Real-time progress feedback, non-blocking
- CON: More complex than simple async

### Pattern 3: Caffeine Cache with TTL

**What:** In-memory cache for scraped THBWiki results.

**When to use:** When the same search queries are likely repeated.

**Implementation:**
```java
@Service
public class OriginalCacheService {

    private final Cache<String, List<ThbwikiSearchResult>> cache = Caffeine.newBuilder()
        .maximumSize(10_000)
        .expireAfterWrite(Duration.ofHours(24))
        .build();

    public List<ThbwikiSearchResult> getOrFetch(String key, Supplier<List<ThbwikiSearchResult>> loader) {
        return cache.get(key, loader);
    }
}
```

**Trade-offs:**
- PRO: Fast lookup, reduces THBWiki load
- CON: Memory usage, stale data risk

## Error Handling Strategy

### Retry Policy

| Error Type | Retry Strategy | Max Attempts |
|------------|---------------|--------------|
| Network timeout | Exponential backoff (1s, 2s, 4s) | 3 |
| HTTP 429 (Rate limit) | Wait 60s then retry | 2 |
| HTTP 5xx | Linear backoff (5s) | 2 |
| Parsing error | No retry | - |

### Circuit Breaker (Optional Enhancement)

For production, consider resilience4j:
```xml
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot3</artifactId>
</dependency>
```

## Data Flow

### Information Flow

```
Track.original (existing format: "alias1, alias2")
        │
        ▼
ThbwikiService.searchOriginal(track.name)
        │
        ├──▶ THBWiki Search API (GET /search?q=...)
        │
        ├──▶ Jsoup.parse(html) → Extract track list
        │
        ├──▶ Fuzzy match against track.name + artist
        │
        └──▶ Return OriginalInfo("东方Project", ".BAD_APPLE!!")

        │
        ▼
Track.original = "东方Project/.BAD_APPLE!!" (new format)
```

## Scaling Considerations

| Scale | Architecture Adjustments |
|-------|--------------------------|
| 0-100 albums | Single-threaded batch, simple retry |
| 100-1,000 albums | Rate-limited (1 req/s), progress checkpoint |
| 1,000-10,000 albums | Distributed job queue, multiple workers |

### Scaling Priorities

1. **First bottleneck: Rate limiting** - THBWiki may block excessive requests
2. **Second bottleneck: Memory** - Caffeine cache size limit
3. **Third bottleneck: Database** - Batch inserts with transaction batching

## Anti-Patterns

### Anti-Pattern 1: Scraping in REST Response Thread

**What people do:** Execute scraping synchronously in controller, blocking the HTTP response.

**Why it's wrong:** Long scraping times (2-5s per request) will timeout and exhaust threads.

**Do this instead:** Use async processing (WebFlux or @Async) and return immediately with job ID.

### Anti-Pattern 2: No Rate Limiting

**What people do:** Fire multiple concurrent requests to THBWiki.

**Why it's wrong:** THBWiki may IP-ban the server, breaking all functionality.

**Do this instead:** Add `delayElements()` in WebFlux or `Thread.sleep()` between requests.

### Anti-Pattern 3: Storing Raw HTML

**What people do:** Store raw HTML responses in database for later parsing.

**Why it's wrong:** Wastes storage, HTML structure may change breaking old parses.

**Do this instead:** Parse immediately, store only structured `OriginalInfo`.

## Build Order Implications

1. **Phase 1: ThbwikiService (tongrenlu-dao)**
   - Add Jsoup and Caffeine dependencies
   - Implement search logic
   - Unit test parsing with sample HTML

2. **Phase 2: OriginalCacheService (tongrenlu-dao)**
   - Implement cache layer
   - Add cache invalidation logic

3. **Phase 3: ThbwikiJob (tongrenlu-tool)**
   - Add SSE endpoint
   - Implement progress tracking
   - Test with small batch

4. **Phase 4: Integration**
   - Connect Track update logic
   - End-to-end testing
   - Performance testing

## Open Questions

1. **THBWiki robots.txt** - Need to verify scraping is allowed
2. **API availability** - Does THBWiki have an official API to use instead?
3. **Matching algorithm** - Need to determine best fuzzy matching strategy
4. **Error recovery** - How to handle partially completed batch jobs?

---

*Architecture research for THBWiki integration*
*Researched: 2026-04-13*
