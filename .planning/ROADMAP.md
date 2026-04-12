# Roadmap

## Phases

- [ ] **Phase 1: THBWiki服务基础** - 专辑搜索接口实现
- [ ] **Phase 2: HTML解析层** - 解析THBWiki页面提取原曲信息
- [ ] **Phase 3: 速率限制** - 防止THBWiki封禁
- [ ] **Phase 4: 缓存层** - 减少重复请求
- [ ] **Phase 5: 文本规范化** - CJK字符标准化处理
- [ ] **Phase 6: 匹配算法与存储** - 曲目匹配与数据更新
- [ ] **Phase 7: 批量任务调度** - 定时串行处理专辑
- [ ] **Phase 8: 状态追踪** - 抓取状态记录
- [ ] **Phase 9: 专辑详情页展示** - 用户端原曲信息展示

---

## Phase Details

### Phase 1: THBWiki服务基础

**Goal**: 用户可以在管理后台触发原曲抓取，系统能成功从THBWiki搜索专辑

**Depends on**: Nothing (first phase)

**Requirements**: ORIGINAL-01.1

**Success Criteria** (what must be TRUE):
1. 管理后台可以输入专辑名触发THBWiki搜索
2. 系统返回THBWiki中匹配的专辑列表
3. 搜索结果包含专辑名和THBWiki链接

**Plans**: TBD

---

### Phase 2: HTML解析层

**Goal**: 系统能从THBWiki专辑页面正确解析出曲目列表和每首歌曲的原曲出处

**Depends on**: Phase 1

**Requirements**: ORIGINAL-01.2, ORIGINAL-01.3

**Success Criteria** (what must be TRUE):
1. 给定THBWiki专辑页面URL，系统能解析出曲目列表
2. 每首曲目能提取出原曲出处和原曲名称
3. 解析结果格式为"原曲出处/原曲名称"

**Plans**: TBD

---

### Phase 3: 速率限制

**Goal**: 批量抓取时自动降速，防止THBWiki封禁IP

**Depends on**: Phase 2

**Requirements**: ORIGINAL-01.4

**Success Criteria** (what must be TRUE):
1. 连续请求之间至少间隔1秒
2. 收到429响应后自动退避
3. 日志记录每次限速操作

**Plans**: TBD

---

### Phase 4: 缓存层

**Goal**: 已抓取的专辑信息被缓存，减少对THBWiki的重复请求

**Depends on**: Phase 3

**Requirements**: ORIGINAL-01.5

**Success Criteria** (what must be TRUE):
1. 相同专辑的第二次请求直接返回缓存结果
2. 缓存有效期至少24小时
3. 缓存命中率可在日志或监控中查看

**Plans**: TBD

---

### Phase 5: 文本规范化

**Goal**: 歌曲名匹配前进行标准化，消除全角/半角、日文/中文差异

**Depends on**: Phase 4

**Requirements**: ORIGINAL-02.1

**Success Criteria** (what must be TRUE):
1. "BAD APPLE!!" 和 "BAD　APPLE!!" 规范化后相同
2. "Forward" 和 "-forward" 规范化后相同
3. Unicode组合字符处理正确

**Plans**: TBD

---

### Phase 6: 匹配算法与存储

**Goal**: 本地专辑曲目能自动匹配THBWiki原曲信息并更新到数据库

**Depends on**: Phase 5

**Requirements**: ORIGINAL-02.2, ORIGINAL-02.3

**Success Criteria** (what must be TRUE):
1. 本地专辑名匹配THBWiki专辑名后，曲目列表自动关联
2. 匹配成功的曲目，Original字段更新为"原曲出处/原曲名称"格式
3. 匹配失败的曲目记录原因

**Plans**: TBD

---

### Phase 7: 批量任务调度

**Goal**: 管理后台能启动批量抓取任务，自动串行处理未抓取的专辑

**Depends on**: Phase 6

**Requirements**: ORIGINAL-04.1, ORIGINAL-04.2

**Success Criteria** (what must be TRUE):
1. 管理员可以触发"批量抓取所有未抓取专辑"任务
2. 任务启动后自动处理所有待抓取专辑
3. 同一时间只处理1张专辑

**Plans**: TBD

---

### Phase 8: 状态追踪

**Goal**: 每张专辑的抓取状态可追踪，管理员可查看进度和结果

**Depends on**: Phase 7

**Requirements**: ORIGINAL-04.3

**Success Criteria** (what must be TRUE):
1. 每张专辑显示抓取状态：未抓取/成功/失败
2. 批量任务显示整体进度（X/Y专辑已完成）
3. 失败专辑显示失败原因

**Plans**: TBD

---

### Phase 9: 专辑详情页展示

**Goal**: 用户在专辑详情页能看到歌曲的原曲出处信息

**Depends on**: Phase 8

**Requirements**: ORIGINAL-03.1, ORIGINAL-03.2

**Success Criteria** (what must be TRUE):
1. 专辑详情页每首歌曲显示原曲信息
2. 原曲信息格式为"原曲出处/原曲名称"
3. 原曲信息可点击跳转到THBWiki页面

**Plans**: TBD

**UI hint**: yes

---

## Progress

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. THBWiki服务基础 | 0/3 | Not started | - |
| 2. HTML解析层 | 0/3 | Not started | - |
| 3. 速率限制 | 0/3 | Not started | - |
| 4. 缓存层 | 0/3 | Not started | - |
| 5. 文本规范化 | 0/3 | Not started | - |
| 6. 匹配算法与存储 | 0/3 | Not started | - |
| 7. 批量任务调度 | 0/3 | Not started | - |
| 8. 状态追踪 | 0/3 | Not started | - |
| 9. 专辑详情页展示 | 0/3 | Not started | - |

---

*Last updated: 2026-04-13*
