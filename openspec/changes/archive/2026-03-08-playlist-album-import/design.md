## Context

### 当前状态

tongrenlu-tool 模块已有两个批处理任务：
- `MusicAlbumParseJob` - 从本地文件解析专辑目录并导入
- `MusicArtistParseJob` - 解析艺术家信息

`HomeMusicService` 已实现网易云 API 集成：
- `getCloudMusicAlbumById()` - 获取专辑详情
- `saveCloudMusicAlbum()` - 保存专辑到数据库
- 使用 `apis.netstart.cn/music` 作为 API 代理

### 约束

- 网易云 API 需要通过代理 `apis.netstart.cn` 访问
- 歌单 API `/playlist/track/all` 需要分页获取（每页最多 500 条）
- 专辑数量可能很大（如示例歌单有 3,477 个专辑）

## Goals / Non-Goals

**Goals:**

1. 实现通过网易云歌单 ID 批量导入专辑的功能
2. 支持分页获取歌单中的所有歌曲
3. 自动提取专辑 ID 并去重
4. 复用现有的 `saveCloudMusicAlbum()` 方法
5. 支持断点续传，避免重复导入

**Non-Goals:**

1. 不实现歌单增量同步（只做全量导入）
2. 不实现 API 失败重试机制（保持简单，失败即停止）
3. 不修改现有专辑保存逻辑

## Decisions

### 1. API 端点选择

**决定**: 使用 `/playlist/track/all` 而非 `/playlist/detail`

**原因**:
- `/playlist/detail` 只返回 10 首歌曲，无法获取完整列表
- `/playlist/track/all` 支持分页，可获取全部歌曲
- 歌曲数据中包含 `al.id` 字段，直接可用

**API 参数**:
```
GET /playlist/track/all?id={playlistId}&limit={limit}&offset={offset}
Response: { songs: [{ id, name, al: { id, name, picUrl }, ... }] }
```

### 2. 数据模型设计

**决定**: 创建简化的响应模型，只保留必要字段

```java
// CloudMusicPlaylistTrack.java
public class CloudMusicPlaylistTrack {
    private Long id;           // 歌曲ID
    private String name;       // 歌曲名称
    private CloudMusicAlbum al; // 专辑信息（复用现有模型）
    private List<CloudMusicArtist> ar; // 艺术家
}

// CloudMusicPlaylistResponse.java
public class CloudMusicPlaylistResponse {
    private List<CloudMusicPlaylistTrack> songs;
}
```

**替代方案**: 直接使用 Map 解析 JSON
- **优点**: 无需定义模型类
- **缺点**: 类型不安全，代码可读性差
- **选择**: 定义模型类，保持代码风格一致

### 3. 批处理任务架构

**决定**: 参考 `MusicAlbumParseJob` 的实现模式

```
PlaylistImportJob
├── 输入: 歌单ID (命令行参数或配置文件)
├── 处理流程:
│   1. 分页获取歌单歌曲
│   2. 提取专辑ID并去重
│   3. 检查专辑是否已存在
│   4. 调用 saveCloudMusicAlbum() 保存
│   5. 更新进度文件
└── 输出: 导入统计日志
```

**替代方案**: 实现 REST API 接口
- **优点**: 可通过 Web 界面触发
- **缺点**: 长时间运行可能导致请求超时
- **选择**: 命令行批处理任务，简单可靠

### 4. 去重策略

**决定**: 使用 `cloudMusicId` 字段去重，跳过已存在的专辑

**实现**:
```java
// 检查专辑是否已存在
ArticleBean existing = articleService.getByCloudMusicId(albumId);
if (existing != null) {
    log.info("专辑已存在，跳过: albumId={}", albumId);
    continue;
}
// 调用保存
homeMusicService.saveCloudMusicAlbum(albumId);
```

**替代方案**: 使用专辑标题去重
- **缺点**: 同名专辑可能存在，误判风险高
- **选择**: cloudMusicId 是唯一标识，更可靠

### 5. 进度存储

**决定**: 使用文本文件存储进度，与现有批处理任务保持一致

```
data/playlist_progress.txt
内容: 当前处理的专辑索引 (如 150)
```

**替代方案**: 数据库存储进度
- **优点**: 更可靠，支持多实例
- **缺点**: 增加复杂度
- **选择**: 文件存储，简单够用

## Risks / Trade-offs

### Risk 1: API 限流

**风险**: 大量请求可能触发网易云 API 限流

**缓解措施**:
- 每次请求间隔 500ms
- 单页获取 500 条，减少请求次数
- 支持断点续传，可随时中断和恢复

### Risk 2: 专辑数据不完整

**风险**: 部分专辑可能在网易云下架或信息不完整

**缓解措施**:
- 捕获异常并记录日志
- 跳过失败的专辑继续处理
- 最终输出成功/失败统计

### Risk 3: 内存溢出

**风险**: 歌单曲目过多导致内存问题

**缓解措施**:
- 分页获取，不一次性加载全部
- 边获取边处理，及时释放内存

### Trade-off: 同步处理 vs 异步队列

**选择**: 同步处理

**权衡**:
- 优点：实现简单，易于调试
- 缺点：长时间运行，无法并行处理
- 结论：批处理任务场景下可接受

## Migration Plan

### 部署步骤

1. 编译并打包 `tongrenlu-tool` 模块
2. 部署到服务器
3. 执行命令: `java -jar tongrenlu-tool.jar --playlist.id=8248011702`

### 回滚策略

- 无数据库 schema 变更，无需数据回滚
- 如需重新导入，删除 `data/playlist_progress.txt` 即可

## Open Questions

1. **歌单ID来源**: 是否需要支持多个歌单批量导入？
   - 当前设计: 单个歌单
   - 扩展方案: 支持配置文件指定多个歌单ID

2. **导入频率**: 是否需要定时任务自动增量导入？
   - 当前设计: 手动触发
   - 扩展方案: 后续可增加定时任务支持
