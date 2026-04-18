# Phase 3: 速率限制 - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-18
**Phase:** 03-rate-limit
**Areas discussed:** 退避策略, 实现方式, 最小间隔, 配置化

---

## 退避策略

| Option | Description | Selected |
|--------|-------------|----------|
| 固定等待 | 等一个固定时长（如 60 秒） | |
| 线性递增 | 每次重试 +30 秒（60s → 90s → 120s） | |
| 指数退避 | 每次重试翻倍（60s → 120s → 240s） | |
| 指数退避 + 抖动 | 指数退避基础上加随机值（±5s） | ✓ |

**User's choice:** 指数退避 + 抖动
**Notes:** 防止多实例同时重试造成 thundering herd

---

## 实现方式

| Option | Description | Selected |
|--------|-------------|----------|
| 独立 RateLimiter 类 | 新建 ThbwikiRateLimiter.java，ThbwikiService 调用它 | |
| 内嵌 ThbwikiService | 把限速逻辑直接写在 ThbwikiService 里 | |
| HTTP 拦截器 | 封装 Hutool HttpRequest，加入限速和退避逻辑 | ✓ |

**User's choice:** HTTP 拦截器
**Notes:** 限速逻辑集中在拦截器，ThbwikiService 保持简洁，可复用

---

## 最小间隔

| Option | Description | Selected |
|--------|-------------|----------|
| 1 秒 | 最低要求，够保守 | |
| 2 秒 | 更安全，THBWiki 不太可能封 | |
| 3 秒 | 非常保守，批量抓取会很慢 | |
| 可配置 | 硬编码默认 1 秒，但留配置项可调 | ✓ |

**User's choice:** 可配置，默认 1 秒
**Notes:** ORIGINAL-01.4 要求"至少 1 秒"，保留配置项方便调优

---

## 配置化

| Option | Description | Selected |
|--------|-------------|----------|
| application.properties | 在 Spring 配置文件中声明 thbwiki.rate-limit.interval=2000 | |
| Java constants | 在 RateLimiter 类里写 private static final long INTERVAL_MS = 1000 | |
| @Value 注入 | 用 Spring 的 @Value 注解，从配置文件读取 | ✓ |

**User's choice:** @Value 注入
**Notes:** 与现有 Spring 配置模式一致，硬编码默认值兜底

---

## Deferred Ideas

无
