# Phase 3: 速率限制 - Context

**Gathered:** 2026-04-18
**Status:** Ready for planning

<domain>
## Phase Boundary

Phase 2 已实现 THBWiki 专辑页面 HTML 解析。Phase 3 目标：**在批量抓取时自动降速，防止 THBWiki 封禁 IP**。

核心交付物：
1. 连续请求之间自动插入延迟
2. 收到 429 响应后自动退避重试
3. 限速操作有完整日志记录

Phase 3 不处理缓存（Phase 4）、匹配算法（Phase 6）、状态追踪（Phase 8）—— 这些在各自阶段处理。
</domain>

<decisions>
## Implementation Decisions

### D-01: 退避策略

**Decision:** 指数退避 + 抖动

**Rationale:**
- 429 表示服务器主动拒绝，需要等待后重试
- 指数退避是业界标准做法，逐步增加等待时间
- 加随机抖动（±5 秒）防止多实例同时重试造成 thundering herd

**Implementation:**
```java
// 初始等待 60 秒，每次重试翻倍，最大 10 分钟
long baseWait = 60_000;  // 60s
long maxWait = 600_000;  // 10min
long jitter = ThreadLocalRandom.current().nextLong(-5_000, 5_001);
long waitTime = Math.min(baseWait * (1L << retryCount), maxWait) + jitter;
```

### D-02: 实现方式

**Decision:** HTTP 拦截器（封装 Hutool HttpRequest）

**Rationale:**
- 限速逻辑集中在拦截器，ThbwikiService 保持简洁
- 拦截器可复用，Phase 4 缓存层也可以调用同一个拦截器
- 与现有 Hutool HttpRequest 使用方式一致

**Component:**
```
tongrenlu-dao/src/main/java/info/tongrenlu/http/
└── ThbwikiHttpInterceptor.java   # 速率限制 + 429 处理拦截器
```

拦截器封装：
- 请求前：等待至满足最小间隔
- 响应后：检测 429 状态码，触发退避重试
- 日志：记录每次限速操作和退避事件

### D-03: 最小间隔

**Decision:** 可配置，默认 1 秒

**Rationale:**
- ORIGINAL-01.4 要求"至少 1 秒"，默认满足最低要求
- 保留配置项方便调优，2 秒更安全时可改为 2000

**Configuration:**
```properties
# application.properties (tongrenlu-dao)
thbwiki.rate-limit.interval-ms=1000
thbwiki.rate-limit.max-retries=3
```

### D-04: 配置化

**Decision:** @Value 注入

**Rationale:**
- 与现有 Spring 配置模式一致（DB_HOST、server.port 等）
- 硬编码默认值兜底，配置文件覆盖
- 单元测试可通过 @SpringBootTest 或手动注入 Mock 验证

**Implementation:**
```java
@Component
public class ThbwikiHttpInterceptor {
    @Value("${thbwiki.rate-limit.interval-ms:1000}")
    private long intervalMs;

    @Value("${thbwiki.rate-limit.max-retries:3}")
    private int maxRetries;
}
```

### Folded Todos

无

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Data Models
- `tongrenlu-dao/src/main/java/info/tongrenlu/model/ThbwikiAlbum.java` — 专辑数据模型
- `tongrenlu-dao/src/main/java/info/tongrenlu/model/ThbwikiTrack.java` — 曲目数据模型

### Service Implementation
- `tongrenlu-dao/src/main/java/info/tongrenlu/service/ThbwikiService.java` — 现有服务，待接入限速拦截器

### Configuration
- `tongrenlu-dao/src/main/resources/application.properties` — 限速参数配置目标

### Requirements
- `REQUIREMENTS.md` §ORIGINAL-01.4 — "请求间隔至少 1 秒，防止被封禁"
- `.planning/ROADMAP.md` Phase 3 — Success Criteria

### Prior Context
- `.planning/phases/01-thbwiki/01-CONTEXT.md` — Phase 1 决策（HTTP 库：Hutool，User-Agent）
- `.planning/phases/02-html/02-CONTEXT.md` — Phase 2 决策（解析器在 ThbwikiService 内）

### Conventions
- `.planning/codebase/CONVENTIONS.md` — 编码规范（日志、命名、错误处理）
- `.planning/codebase/INTEGRATIONS.md` — 外部集成模式（Hutool HttpRequest）

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **ThbwikiService**: 现有 HTTP 请求，待接入限速拦截器
- **Hutool HttpRequest**: HTTP 客户端，无内置限速，需要拦截器封装
- **@Slf4j**: 日志规范已建立（log.info/warn/error）

### Integration Points
- `ThbwikiService.searchAlbum()`: 接入限速拦截器
- `ThbwikiService.fetchAlbumDetail()`: 接入限速拦截器
- `application.properties`: 新增限速参数配置

### Established Patterns
- Service 层使用 `@RequiredArgsConstructor` + `@Value` 注入
- HTTP 请求使用 Hutool `HttpRequest.get(url).header().timeout().execute()`
- 错误时返回空集合/Optional + 日志警告（Phase 2 D-02 容错策略）
- User-Agent: `tongrenlu/1.0 (同人音乐库管理)`

### Creative Options
- 429 退避时是否重试整个操作，还是传播错误让调用方决定？→ 参考 Phase 2 D-02 容错策略，返回失败 + 日志记录
- 限速拦截器是否需要线程安全？→ 是，多线程批量任务会并发调用
- 退避时是否可中断？→ 否，批量任务设计为串行处理，不需要优雅中断

</code_context>

<specifics>
## Specific Ideas

- 抖动范围：±5 秒（jitter）
- 初始退避：60 秒
- 最大退避：10 分钟（600 秒）
- 最大重试次数：3 次（可配置）
- 日志级别：退避等待用 `log.info`，429 错误用 `log.warn`

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

---

*Phase: 03-rate-limit*
*Context gathered: 2026-04-18*
