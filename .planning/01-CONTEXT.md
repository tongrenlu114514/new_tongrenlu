# Phase 1 Context

**Phase:** 1 - THBWiki 服务基础
**Created:** 2026-04-13
**Status:** Discussed

## Prior Decisions (from STATE.md)

- 使用 thbwiki.cc 作为原曲信息数据源
- 使用 Hutool HttpRequest 进行 HTTP 请求
- 使用 Jsoup 进行 HTML 解析
- 使用 Caffeine 作为缓存层

## Phase 1 Decisions

### 1. THBWiki 搜索方式

**Decision:** URL 搜索 + 页面抓取 (选项 A)

**Rationale:**
- THBWiki 没有公开文档化的 API
- URL 搜索方式简单可靠：`https://thwiki.cc/index.php?search={专辑名}`
- 直接抓取专辑页面获取完整曲目列表
- 如后续发现 API 可用，可切换

**Implementation:**
```java
String searchUrl = "https://thwiki.cc/index.php?search=" + URLEncoder.encode(albumName, StandardCharsets.UTF_8);
```

### 2. 搜索结果详细程度

**Decision:** 完整曲目列表 (选项 C)

**Rationale:**
- Phase 1 目标需要解析曲目列表来提取原曲信息
- 需要获取每首曲目的名称和原曲出处
- 这是 ORIGINAL-01 的核心功能

**Required Data:**
- 专辑名称
- 专辑页面 URL
- 曲目列表（曲名、原曲出处、原曲名称）

### 3. 服务存放位置

**Decision:** tongrenlu-dao

**Rationale:**
- ThbwikiService 是核心业务逻辑
- 需要被多个模块复用（tool 批处理、后续 web 展示）
- dao 模块是数据层，适合放置外部数据获取逻辑

**Component Structure:**
```
tongrenlu-dao/
├── service/
│   └── ThbwikiService.java      # 核心抓取服务
├── cache/
│   └── ThbwikiCacheService.java # Caffeine 缓存封装
└── model/
    └── ThbwikiAlbum.java        # 抓取的专辑数据模型

tongrenlu-tool/
├── job/
│   └── ThbwikiFetchJob.java     # 批处理 Job
└── controller/
    └── AdminThbwikiController.java
```

### 4. 缓存策略

**Decision:** Caffeine TTL 缓存，24 小时过期

**Rationale:**
- 避免重复请求同一专辑
- 24 小时 TTL 平衡数据新鲜度和请求频率
- Caffeine 是已确定的依赖

**Cache Configuration:**
```java
Cache<String, ThbwikiAlbum> cache = Caffeine.newBuilder()
    .expireAfterWrite(Duration.ofHours(24))
    .maximumSize(1000)
    .build();
```

## Component Specs

### ThbwikiService

**Responsibilities:**
1. 根据专辑名搜索 THBWiki
2. 抓取专辑页面 HTML
3. 解析曲目列表，提取原曲信息
4. 调用缓存服务

**Methods:**
```java
public interface ThbwikiService {
    Optional<ThbwikiAlbum> searchAlbum(String albumName);
    Optional<ThbwikiTrack> findTrack(String albumName, String trackName);
}
```

### ThbwikiCacheService

**Responsibilities:**
1. 管理 Caffeine 缓存实例
2. 提供缓存读写接口
3. 缓存失效处理

### ThbwikiAlbum / ThbwikiTrack

**Data Model:**
```java
public record ThbwikiAlbum(
    String name,
    String url,
    List<ThbwikiTrack> tracks
) {}

public record ThbwikiTrack(
    String name,
    String originalSource,    // 原曲出处，如 "东方Project"
    String originalName,       // 原曲名称，如 "永夜抄 〜 Imperishable Night"
    String originalUrl        // THBWiki 链接
) {}
```

## Dependencies

- Jsoup 1.18.1 (HTML 解析) - NEW
- Caffeine (缓存) - NEW
- Hutool 5.8.40 (HTTP 请求) - EXISTING

## Open Questions

1. **THBWiki API 可用性**: THBWiki 可能没有公开 API，研究建议使用 HTML 抓取。需要验证 MediaWiki API (`/api.php`) 是否可用。
2. **robots.txt 合规性**: 需要验证 THBWiki 是否允许抓取
3. **HTML 结构验证**: 需要实际访问 THBWiki 验证页面结构

## API vs Scraping 决策

**研究结论**: THBWiki 可能没有公开文档的 API，建议使用 HTML 抓取。

**MediaWiki API 试探** (待验证):
```bash
# MediaWiki 标准 API 格式
curl "https://thbwiki.cc/api.php?action=query&list=search&srsearch=Satori+Maiden&format=json"
```

**备选方案 - HTML 抓取**:
```bash
# 直接搜索页面
curl "https://thbwiki.cc/index.php?search=Satori+Maiden"
```

**建议**: 先尝试 MediaWiki API (结构化数据)，不可用时回退到 HTML 抓取。

## Next Steps

1. Research: 验证 THBWiki 页面结构
2. Plan: 创建 01-PLAN.md
3. Execute: 实现 ThbwikiService

---

*Context created during discuss-phase for Phase 1*
