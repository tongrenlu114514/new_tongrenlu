## 1. 数据模型创建

- [x] 1.1 创建 `CloudMusicPlaylistTrack.java` - 歌单曲目模型类
- [x] 1.2 创建 `CloudMusicPlaylistResponse.java` - 歌单API响应模型类

## 2. 服务层实现

- [x] 2.1 在 `HomeMusicService` 中添加 `getCloudMusicPlaylistTracks()` 方法 - 获取歌单曲目列表
- [x] 2.2 实现分页获取逻辑 - 支持 limit 和 offset 参数
- [x] 2.3 实现 `extractAlbumIds()` 方法 - 从歌曲列表提取并去重专辑ID
- [x] 2.4 添加专辑去重检查逻辑 - 使用 `getByCloudMusicId()` 检查

## 3. 批处理任务实现

- [x] 3.1 创建 `PlaylistImportJob.java` - 实现 CommandLineRunner 接口
- [x] 3.2 实现歌单ID参数接收 - 支持命令行参数或配置文件
- [x] 3.3 实现分页获取歌单数据逻辑
- [x] 3.4 实现专辑ID提取和去重
- [x] 3.5 实现专辑导入循环 - 调用 `saveCloudMusicAlbum()`
- [x] 3.6 实现进度文件读写 - `data/playlist_progress.txt`

## 4. 统计和日志

- [x] 4.1 添加导入统计计数器 - 成功/跳过/失败数量
- [x] 4.2 添加详细的日志输出 - 记录每个专辑的处理状态
- [x] 4.3 实现最终统计信息输出 - 显示汇总报告

## 5. 测试验证

- [x] 5.1 编译所有模块确保无错误
- [x] 5.2 使用示例歌单 ID 8248011702 测试导入功能
- [x] 5.3 验证断点续传功能 - 中断后重新运行
- [x] 5.4 验证去重功能 - 重复运行应跳过已存在专辑