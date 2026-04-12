# 同人音乐库管理 (tongrenlu)

## What This Is

同人音乐库管理平台，用于管理、播放和分享同人音乐。支持从网易云音乐导入专辑信息，展示歌曲原曲出处，并提供管理后台进行批量数据维护。

## Core Value

帮助用户发现和管理同人音乐，快速了解歌曲的原曲出处。

## Requirements

### Validated

<!-- 已上线功能 -->

- ✓ 专辑管理 - 上传和管理同人音乐专辑
- ✓ 歌曲管理 - 管理专辑内的曲目列表
- ✓ 网易云音乐导入 - 从网易云音乐 API 导入专辑信息
- ✓ 音乐播放 - 支持在线播放和管理播放列表
- ✓ 管理后台 - 专辑和歌曲的 CRUD 操作

### Active

<!-- 当前开发范围 -->

- [ ] **ORIGINAL-01**: 原曲信息抓取服务 - 从 thbwiki.cc 搜索并抓取歌曲原曲信息
- [ ] **ORIGINAL-02**: 原曲信息存储 - 将原曲信息标准化存储到数据库
- [ ] **ORIGINAL-03**: 专辑详情页展示 - 在专辑页展示歌曲原曲信息
- [ ] **ORIGINAL-04**: 批量抓取管理功能 - 管理后台批量抓取原曲信息

### Out of Scope

<!-- 明确排除的功能 -->

- 用户端实时搜索原曲 - 仅支持管理后台批量操作
- 手动编辑原曲信息 - 仅自动匹配，不支持手动关联
- 原曲信息更新机制 - 仅支持首次抓取，不支持定期更新

## Context

**现有 Original 字段：**
- `TrackBean.original` - 当前存储网易云音乐的别名（alia），格式为逗号分隔的字符串
- 需要重新标准化为 "原曲出处/原曲名称" 格式

**技术环境：**
- Java 21 + Spring Boot 3.4.3
- MyBatis Plus + MySQL
- jQuery 前端（静态资源）
- 已有 CloudMusic API 集成经验

**原曲信息格式：**
- 格式：`原曲出处/原曲名称`
- 例如：`东方Project/.BAD_APPLE!!`

## Constraints

- **数据源**: 仅支持 thbwiki.cc，不支持其他同人音乐 wiki
- **匹配策略**: 自动最佳匹配，不支持人工选择
- **操作范围**: 仅管理后台批量操作，不支持用户端触发
- **兼容旧数据**: 需要更新现有 Original 字段格式

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| 使用 thbwiki.cc 作为数据源 | 同人音乐领域权威百科 | — Pending |
| 自动最佳匹配策略 | 简化管理操作流程 | — Pending |
| 批量抓取而非实时搜索 | 避免频繁请求影响性能 | — Pending |
| 格式：原曲出处/原曲名称 | 与 THBWiki 词条格式保持一致 | — Pending |

---

*Last updated: 2026-04-13 after initial requirement gathering*
