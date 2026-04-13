# Phase 2: HTML解析层 - Context

**Phase:** 2 - HTML解析层
**Created:** 2026-04-13
**Status:** Ready for planning

## Phase Boundary

Phase 1 已实现 THBWiki 搜索功能（专辑名 → 专辑 URL）。Phase 2 目标：**给定专辑 URL，解析出曲目列表和每首歌曲的原曲出处**。

核心交付物：
1. 给定 THBWiki 专辑页面 URL，能解析出曲目列表
2. 每首曲目能提取出原曲出处（source）和原曲名称（name）
3. 解析结果格式为"原曲出处/原曲名称"

Phase 1 的 `ThbwikiService.searchAlbum()` 返回 `Optional<ThbwikiAlbum>`，但 tracks 列表为空。Phase 2 需要**补充专辑页面的完整抓取逻辑**。

## Prior Decisions (from Phase 1)

| 决策 | 选择 |
|------|------|
| HTTP 库 | Hutool HttpRequest |
| HTML 解析 | Jsoup 1.18.1 |
| 缓存层 | Caffeine 24h TTL |
| 服务位置 | tongrenlu-dao |
| CSS 选择器 | `.wikitable.musicTable`, `.ogmusic`, `.source`, `.mw-page-title-main` |

## Implementation Decisions

### D-01: 解析器实现方式

**Decision:** 直接在 ThbwikiService 内解析，不单独拆分 Parser 类

**Rationale:**
- Phase 1 已确定使用 Jsoup，解析逻辑与 HTTP 请求紧密耦合
- 避免过度工程化，保持代码简洁
- 解析逻辑在 Service 内私有化，外部只暴露业务接口

**Implementation:**
```java
// ThbwikiService.java - 新增方法
public Optional<ThbwikiAlbum> fetchAlbumDetail(String url) {
    String html = HttpRequest.get(url)
        .header("User-Agent", "...")
        .timeout(10000)
        .execute()
        .body();

    Document doc = Jsoup.parse(html);
    return parseAlbumDetail(doc, url);
}

private Optional<ThbwikiAlbum> parseAlbumDetail(Document doc, String url) {
    // 在同一文件内解析 HTML
}
```

### D-02: 容错策略

**Decision:** 返回空列表 + 日志警告，不抛出异常

**Rationale:**
- 网络波动、页面结构变化等是常见场景
- 批量任务不应因单张专辑解析失败而中断
- 调用方可以通过日志了解解析失败情况

**Implementation:**
```java
if (tracks.isEmpty()) {
    log.warn("No tracks parsed from album page: {}", url);
}
return album;  // 始终返回对象，tracks 可能为空
```

### D-03: 验证方式

**Decision:** 集成测试 + 真实 HTML（使用真实 THBWiki 页面 HTML）

**Rationale:**
- 需要验证实际页面结构的解析正确性
- 使用 Jsoup 测试助手加载真实 HTML
- 测试文件放在 `src/test/resources/thbwiki/` 目录

**Test Structure:**
```
src/test/
├── java/.../
│   └── ThbwikiServiceTest.java      # 单元测试
└── resources/
    └── thbwiki/
        └── satori-maiden.html       # 真实 HTML 样本
```

## Specific Ideas

- **页面结构已验证**：`.wikitable.musicTable` 表格、`tr` 行、`.ogmusic` 原曲信息、`.source` 出处链接
- **示例数据**：Satori Maiden 专辑已确认可用
- **防御性复制**：ThbwikiAlbum.getTracks() 已实现防御性复制（Phase 1 HIGH 修复）

## Canonical References

### Data Models
- `tongrenlu-dao/src/main/java/info/tongrenlu/model/ThbwikiTrack.java` — 数据模型定义
- `tongrenlu-dao/src/main/java/info/tongrenlu/model/ThbwikiAlbum.java` — 专辑模型定义

### Service Implementation
- `tongrenlu-dao/src/main/java/info/tongrenlu/service/ThbwikiService.java` — 现有搜索服务，待扩展

### Requirements
- `REQUIREMENTS.md` §ORIGINAL-01.2, §ORIGINAL-01.3 — 曲目列表解析需求

### Research Notes
- `01-CONTEXT.md` — Phase 1 已验证的 CSS 选择器和页面结构

## Existing Code Insights

### Reusable Assets
- **ThbwikiTrack / ThbwikiAlbum**: 数据模型已定义，直接使用
- **ThbwikiCacheService**: 可复用缓存逻辑，解析结果缓存
- **Hutool HttpRequest**: HTTP 请求已在项目中使用

### Integration Points
- `ThbwikiService`: 扩展 `fetchAlbumDetail()` 方法
- 复用现有异常处理模式（日志 + 返回 Optional）

### Established Patterns
- Service 层使用 `@Slf4j` + `@RequiredArgsConstructor`
- 私有解析方法前缀 `parse` 或 `extract`
- 防御性复制（`List.copyOf()`）

## Deferred Ideas

- **匹配算法优化**: Phase 6 考虑（曲目名相似度匹配）
- **批量解析进度**: Phase 7/8 考虑（状态追踪）
- **缓存刷新机制**: Phase 4 考虑（手动刷新、TTL 调整）

---

*Phase: 02-html*
*Context gathered: 2026-04-13*
