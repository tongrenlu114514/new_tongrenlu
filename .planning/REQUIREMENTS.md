# Requirements

## v1 Requirements

### ORIGINAL-01: 原曲信息抓取服务

- [ ] **ORIGINAL-01.1**: THBWiki 专辑搜索接口 - 根据专辑名在 THBWiki 搜索专辑信息
- [ ] **ORIGINAL-01.2**: 曲目列表解析 - 解析 THBWiki 专辑页面的曲目列表，提取每首歌曲的原曲信息
- [ ] **ORIGINAL-01.3**: HTML 解析 - 解析 THBWiki 页面提取原曲出处和原曲名称
- [ ] **ORIGINAL-01.4**: 速率限制 - 请求间隔至少 1 秒，防止被封禁
- [ ] **ORIGINAL-01.5**: 缓存层 - 缓存已抓取的专辑信息，减少重复请求

### ORIGINAL-02: 原曲信息匹配与存储

- [ ] **ORIGINAL-02.1**: 歌曲名规范化 - 处理全角/半角、日文/中文差异
- [ ] **ORIGINAL-02.2**: 曲目匹配 - 将 THBWiki 曲目列表与本地专辑曲目匹配
- [ ] **ORIGINAL-02.3**: 存储格式更新 - 将 Original 字段更新为 "原曲出处/原曲名称" 格式

### ORIGINAL-03: 专辑详情页展示

- [ ] **ORIGINAL-03.1**: 歌曲原曲信息展示 - 在专辑详情页展示原曲信息
- [ ] **ORIGINAL-03.2**: THBWiki 链接 - 原曲信息可点击跳转至 THBWiki 页面

### ORIGINAL-04: 管理后台批量操作

- [ ] **ORIGINAL-04.1**: 定时任务 - 定时处理未抓取过原曲信息的专辑
- [ ] **ORIGINAL-04.2**: 串行处理 - 同时只处理 1 张专辑，避免过载
- [ ] **ORIGINAL-04.3**: 抓取状态追踪 - 记录每张专辑的抓取状态（未抓取/成功/失败）

## v2 Requirements

*(Deferred to future)*

- 手动触发抓取功能
- 重新抓取失败专辑
- 原曲信息编辑

## Out of Scope

- 用户端实时搜索原曲 — 仅支持管理后台批量操作
- 手动编辑原曲信息 — 仅自动匹配
- 多数据源支持 — 仅支持 THBWiki
- 定期更新机制 — 仅支持首次抓取

---

## Traceability

| REQ-ID | Phase | Status |
|--------|-------|--------|
| ORIGINAL-01.1 | Phase 1 | Pending |
| ORIGINAL-01.2 | Phase 2 | Pending |
| ORIGINAL-01.3 | Phase 2 | Pending |
| ORIGINAL-01.4 | Phase 3 | Pending |
| ORIGINAL-01.5 | Phase 4 | Pending |
| ORIGINAL-02.1 | Phase 5 | Pending |
| ORIGINAL-02.2 | Phase 6 | Pending |
| ORIGINAL-02.3 | Phase 6 | Pending |
| ORIGINAL-03.1 | Phase 9 | Pending |
| ORIGINAL-03.2 | Phase 9 | Pending |
| ORIGINAL-04.1 | Phase 7 | Pending |
| ORIGINAL-04.2 | Phase 7 | Pending |
| ORIGINAL-04.3 | Phase 8 | Pending |

---

*Last updated: 2026-04-13*
