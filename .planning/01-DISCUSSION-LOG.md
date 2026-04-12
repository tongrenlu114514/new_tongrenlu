# Phase 1 Discussion Log

**Phase:** 1 - THBWiki 服务基础
**Date:** 2026-04-13
**Participants:** User, Claude Code

## Session Start

- User invoked `/gsd-discuss-phase 1`
- Phase 1 identified as "THBWiki 服务基础"
- Prior context loaded: STATE.md, research/SUMMARY.md

## Gray Areas Presented

User selected **"全部讨论"** (discuss all 4 areas)

### Gray Area 1: THBWiki 搜索方式

**Options:**
- A: 直接使用 URL 搜索
- B: 使用 API 接口
- C: 先搜索再抓取

**Discussion:**
- 研究摘要显示 THBWiki 使用模板格式 `{{原曲|游戏编号|曲号}}`
- WebFetch 无法访问 thbwiki.cc 进行验证
- 基于现有信息，选择 URL 搜索 + 页面抓取方式

**Decision:** 选项 A - URL 搜索 + 页面抓取

**Rationale:**
- THBWiki 没有公开文档化的 API
- URL 搜索方式简单可靠
- 可根据实际访问结果调整

### Gray Area 2: 搜索结果详细程度

**Options:**
- A: 仅专辑名和链接
- B: 包含简介摘要
- C: 包含完整曲目列表

**Discussion:**
- Phase 1 需要解析曲目列表来提取原曲信息
- 这是 ORIGINAL-01 的核心功能
- 需要获取每首曲目的原曲出处和名称

**Decision:** 选项 C - 完整曲目列表

**Rationale:**
- 满足 Phase 1 核心需求
- 为后续匹配功能提供数据基础

### Gray Area 3: 服务存放位置

**Options:**
- A: tongrenlu-dao
- B: tongrenlu-tool

**Discussion:**
- 研究摘要建议 ThbwikiService 放在 dao 层
- ThbwikiJob 放在 tool 层
- dao 层服务可被多个模块复用

**Decision:** tongrenlu-dao

**Rationale:**
- 核心业务逻辑应放在共享模块
- 便于后续 web 模块调用
- 符合项目分层架构

### Gray Area 4: 缓存策略

**Options:**
- A: 无缓存
- B: TTL 缓存
- C: 永久缓存

**Discussion:**
- STATE.md 已记录使用 Caffeine
- 研究摘要建议 24 小时 TTL
- 需要平衡数据新鲜度和请求频率

**Decision:** 选项 B - Caffeine TTL 缓存 (24小时)

**Rationale:**
- 避免重复请求被封禁
- 24 小时是合理的缓存周期
- Caffeine 性能优秀

## Decisions Made

| Gray Area | Decision | Notes |
|-----------|----------|-------|
| 搜索方式 | URL 搜索 + 页面抓取 | 如发现 API 可切换 |
| 详细程度 | 完整曲目列表 | Phase 1 必需 |
| 服务位置 | tongrenlu-dao | 核心逻辑共享 |
| 缓存策略 | Caffeine 24h TTL | 已确定 |

## Open Questions

1. THBWiki API 可用性 - 待验证
2. robots.txt 合规性 - 待验证
3. HTML 结构 - 待实际访问验证

## Session End

- 01-CONTEXT.md created
- Next step: /gsd-plan-phase 1

---

*Discussion log for Phase 1*
