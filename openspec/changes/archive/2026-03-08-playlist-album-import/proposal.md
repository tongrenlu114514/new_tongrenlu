## Why

当前 tongrenlu-tool 模块只支持从本地文件解析专辑进行批量导入，无法直接利用网易云音乐的歌单数据进行专辑采集。用户需要手动整理专辑信息或逐个搜索导入，效率低下。

网易云音乐上有大量优质的东方同人音乐歌单（如"東方同人音楽流通 检索目录"包含 3,477 个专辑），如果能通过歌单ID直接批量导入专辑，将大幅提升内容采集效率。

## What Changes

- 新增基于网易云音乐歌单ID的专辑批量导入功能
- 新增歌单详情 API 集成（获取歌单中的所有歌曲及专辑信息）
- 新增批处理任务 `PlaylistImportJob`，支持断点续传
- 实现专辑去重逻辑，跳过已存在的专辑

## Capabilities

### New Capabilities

- `playlist-album-import`: 通过网易云歌单ID批量导入专辑的能力
  - 调用歌单详情API获取歌曲列表
  - 提取专辑ID并去重
  - 批量调用现有专辑保存接口完成导入
  - 支持进度保存和断点续传
  - 自动跳过已存在的专辑

### Modified Capabilities

无。此功能为新增能力，不修改现有 spec。

## Impact

### 代码变更

| 模块 | 文件 | 变更类型 |
|------|------|----------|
| tongrenlu-dao/model | CloudMusicPlaylistTrack.java | 新增 |
| tongrenlu-dao/model | CloudMusicPlaylistResponse.java | 新增 |
| tongrenlu-dao/service | HomeMusicService.java | 修改（新增方法） |
| tongrenlu-tool | PlaylistImportJob.java | 新增 |

### API 依赖

| 接口 | 用途 |
|------|------|
| `/playlist/track/all?id={playlistId}&limit={limit}&offset={offset}` | 获取歌单歌曲列表（分页） |

### 复用现有逻辑

- `HomeMusicService.saveCloudMusicAlbum()` - 保存专辑到数据库
- `ArticleService.getByCloudMusicId()` - 专辑去重检查
- 断点续传模式 - 参考 `MusicAlbumParseJob` 实现
