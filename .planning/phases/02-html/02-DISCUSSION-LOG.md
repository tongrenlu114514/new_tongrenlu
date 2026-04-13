# Phase 2 Discussion Log

**Phase:** 2 - HTML解析层
**Date:** 2026-04-13
**Mode:** discuss

---

## Pre-Discussion Analysis

### Prior Context Loaded
- `ROADMAP.md` — Phase 2 scope: "解析THBWiki页面提取原曲信息"
- `STATE.md` — Phase 1 已完成验证，3/3 success criteria passed
- `01-CONTEXT.md` — Phase 1 决策：Jsoup、Hutool、Caffeine、24h TTL
- `ThbwikiService.java` — 现有代码结构

### Gray Areas Identified
1. **解析器实现方式**: 直接在 Service 内解析 vs 单独 Parser 类
2. **容错策略**: 异常 vs 空列表 + 日志
3. **验证方式**: 单元测试 vs 集成测试

---

## Discussion Summary

### Question 1: 解析器实现方式

**Q:** Phase 1 确定使用 Jsoup 进行 HTML 解析。解析逻辑应该放在哪里？
- A. 单独 Parser 类（ThbwikiParser）
- B. 直接在 ThbwikiService 内解析

**Answer:** **B - 直接在 Service 内解析**

**Rationale:**
- 保持代码简洁，避免过度工程化
- 解析与 HTTP 请求紧密耦合，不需独立复用

---

### Question 2: 容错策略

**Q:** 解析失败时（如页面结构变化、网络错误）如何处理？
- A. 抛出异常，调用方处理
- B. 返回空列表 + 日志警告

**Answer:** **B - 返回空列表 + 日志警告**

**Rationale:**
- 批量任务不应因单张专辑失败而中断
- 日志可追踪失败情况
- 调用方自行决定如何处理空列表

---

### Question 3: 验证方式

**Q:** 如何验证解析逻辑的正确性？
- A. 单元测试 + Mock HTML
- B. 集成测试 + 真实 HTML 文件

**Answer:** **B - 集成测试 + 真实 HTML**

**Rationale:**
- 需要验证真实页面结构
- 使用 `Jsoup.parse()` 加载真实 HTML 文件
- 测试文件放在 `src/test/resources/thbwiki/`

---

## Decisions Made

| Decision | Choice | Rationale |
|----------|--------|-----------|
| D-01: 解析器位置 | ThbwikiService 内 | 简洁，避免过度工程 |
| D-02: 容错策略 | 空列表 + 日志 | 批量任务容错性 |
| D-03: 验证方式 | 集成测试 + 真实 HTML | 验证实际解析正确性 |

---

## Notes

- Phase 1 的代码审查问题已修复（CRITICAL: 错误消息泄露；HIGH: 防御性复制）
- CSS 选择器已在 01-CONTEXT.md 验证：`.wikitable.musicTable`, `.ogmusic`, `.source`
- Phase 2 依赖 Phase 1 的搜索功能

---

## Next Steps

1. [ ] `/gsd-plan-phase 2` — 创建 02-PLAN.md
2. [ ] Research — 验证真实 THBWiki 页面结构
3. [ ] Execute — 实现解析逻辑
4. [ ] Test — 创建集成测试

---

*Discussion completed: 2026-04-13*
