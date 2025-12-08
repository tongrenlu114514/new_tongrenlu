# 音乐播放器文档

## 概述

东方同人录音乐播放器是一个现代化的全屏音乐播放器，专为同人音乐播放设计。播放器采用模块化架构，支持LRC歌词显示、播放列表管理、音量控制、播放模式切换等功能。

## 文件结构

### 主要HTML文件
- `tongrenlu-web/src/main/resources/static/player.html` - 全屏播放器主页面

### CSS样式文件
- `tongrenlu-web/src/main/resources/static/assets/css/player-fullscreen.css` - 全屏播放器样式

### JavaScript模块文件 (位于 `components/player/` 目录下)

1. **核心工具模块**
   - `player-utils.js` - 通用工具函数和全局状态管理
   - `player-fullscreen-init.js` - 全屏播放器初始化变量

2. **数据模块**
   - `player-data.js` - 专辑和音频数据加载

3. **UI控制模块**
   - `player-base-ui.js` - 基础UI控制函数
   - `player-ui.js` - 高级UI控制函数

4. **功能模块**
   - `player-controls.js` - 音频播放核心功能
   - `player-lyrics.js` - 歌词解析和显示功能
   - `player-navigation.js` - 播放列表导航控制

5. **事件处理模块**
   - `player-events.js` - 播放器基础事件处理
   - `player-fullscreen-events.js` - 全屏播放器事件处理

6. **主整合模块**
   - `player-fullscreen.js` - 全屏播放器主模块整合

## 功能特性

### 1. 音乐播放
- 支持MP3、OGG、WAV、FLAC、M4A、AAC等多种音频格式
- 自动检测浏览器支持的音频格式
- 支持播放、暂停、停止、上一首、下一首操作
- 自定义音量控制（0-100%）
- 音频进度条控制和显示

### 2. 播放列表管理
- 动态生成播放列表
- 支持点击播放列表中的任一曲目
- 当前播放曲目高亮显示
- 曲目时长显示

### 3. 播放模式
- 顺序播放
- 随机播放（待实现）
- 单曲循环（待实现）
- 列表循环（待实现）

### 4. 歌词显示
- 支持标准LRC格式歌词解析
- 自动歌词时间轴同步
- 歌词自动滚动和高亮显示
- 支持纯文本歌词显示
- 可切换播放列表/歌词显示模式

### 5. 视觉效果
- 动态专辑封面浮动动画
- 漂浮音符背景动画
- 响应式全屏布局
- 毛玻璃特效背景
- 渐变背景色

### 6. 数据处理
- 支持从API加载随机专辑
- 支持通过URL参数加载指定专辑（`?album=专辑ID`）
- 内置测试专辑数据（`albumId='test001'`）
- 自动处理音频URL加载

## API接口

播放器依赖以下后端API接口：

### 1. 随机专辑API
- `GET /api/music/random`
- 返回随机专辑数据，包含：
  - `id` - 专辑ID
  - `title` - 专辑标题
  - `artist` - 艺术家名称
  - `coverUrl` - 专辑封面URL
  - `tracks` - 曲目列表（数组）

### 2. 专辑详情API
- `GET /api/music/detail?albumId={albumId}`
- 返回指定专辑的详细信息
- 参数：`albumId` - 专辑ID

### 3. 音频URL获取API
- `GET /api/music/track?id={trackId}`
- 返回曲目的音频URL和歌词
- 返回格式：
  ```json
  {
    "url": "音频文件URL",
    "lyric": "歌词文本（可选）"
  }
  ```

## 全局变量

播放器使用以下全局变量管理状态：

```javascript
// 当前音乐数据
window.currentMusicData = {
  id: "专辑ID",
  title: "专辑标题",
  artist: "艺术家",
  coverUrl: "封面URL",
  cloudMusicPicUrl: "网易云封面URL",
  tracks: [
    {
      id: "曲目ID",
      trackId: "曲目ID（兼容字段）",
      name: "曲目名称",
      duration: 时长（秒）,
      url: "音频URL"
    }
  ]
};

// 当前播放曲目索引
window.currentTrackIndex = 0;

// 播放状态
window.isPlaying = false;

// 歌词状态
window.currentLyrics = null; // 当前歌词数据
window.isLyricMode = false;  // 是否显示歌词模式

// 音频播放器对象
window.audioPlayer = document.getElementById('audioPlayer');
```

## 模块功能详解

### 1. player-utils.js - 工具函数模块
提供通用工具函数：
- `formatTime(seconds)` - 格式化时间显示（"分:秒"）
- `getUrlParams()` - 解析URL查询参数
- `generateId()` - 生成唯一ID
- `debounce(func, wait)` - 防抖函数
- `throttle(func, limit)` - 节流函数
- `isTestMode(albumId)` - 检查是否为测试模式
- `getTestAlbumData()` - 获取测试专辑数据
- 日志和错误处理函数

### 2. player-data.js - 数据加载模块
负责数据加载功能：
- `loadRandomAlbum(apiEndpoint, callback)` - 加载随机专辑
- `loadAlbumDetail(albumId, callback)` - 加载指定专辑详情
- `loadTestAlbum(testAlbumData, callback)` - 加载测试专辑
- `generatePlaylist(tracks, playlistContainer)` - 生成播放列表DOM
- `loadTrackUrl(track)` - 获取音频URL（返回Promise）

### 3. player-controls.js - 播放控制模块
核心播放功能：
- `playTrack(trackIndex)` - 播放指定索引的曲目
- `playTrackWithData()` - 兼容旧版调用的播放函数
- `stopTrack()` - 停止播放

### 4. player-lyrics.js - 歌词模块
歌词相关功能：
- `parseLrc(lrcString)` - 解析LRC格式歌词
- `loadLyrics(trackIdOrLyric)` - 加载歌词
- `displayLyrics(lyrics)` - 显示歌词
- `updateLyricsHighlight(currentTime)` - 更新歌词高亮
- `scrollLyricsToCurrent()` - 滚动歌词到当前行

### 5. player-navigation.js - 播放导航控制模块
播放列表导航和播放模式控制：
- `playPrevious()` - 播放上一首曲目
- `playNext()` - 播放下一首曲目
- `handleTrackEnd()` - 处理曲目播放结束事件（包括播放列表结束后自动加载新专辑）
- `togglePlayPause()` - 播放/暂停切换
- `toggleShuffle()` - 随机播放切换
- `toggleRepeat()` - 重复模式切换（'none'、'all'、'one'）
- `toggleLyricsMode()` - 切换歌词显示模式
- `showMessage(message, duration)` - 显示临时消息
- `loadNewRandomAlbum()` - 播放列表结束后加载新的随机专辑

### 6. player-fullscreen.js - 全屏播放器主模块
整合和全局功能：
- `startFloatingAnimation()` - 启动浮动音符动画
- `checkAutoplayPolicy()` - 检查浏览器自动播放策略
- `cleanup()` - 清理播放器资源
- `checkAudioFormatSupport()` - 检测支持的音频格式
- 全局错误处理
- 页面事件监听（卸载、可见性变化、全屏变化）

## CSS样式架构

### 布局结构
1. **全屏容器** (`body`)：渐变背景，固定背景，隐藏溢出
2. **播放器容器** (`.player-container`)：Flex布局，毛玻璃效果
3. **左侧区域** (`.album-section`)：专辑显示和控制区域
   - 专辑封面（悬浮动画）
   - 专辑信息（标题、艺术家）
   - 播放器控制栏
4. **右侧区域** (`.playlist-section`)：播放列表和歌词区域
   - 播放列表头部（标题和操作按钮）
   - 播放列表容器
   - 歌词容器

### 动画效果
- **专辑封面浮动**：`@keyframes float` 上下浮动效果
- **漂浮音符**：`@keyframes floatNote` 音符图标漂浮动画
- **悬停效果**：封面图片放大，按钮交互效果

### 响应式设计
- 使用Flexbox布局
- 固定宽度侧边栏（400px）
- 自适应主内容区域
- 移动端适配待完善

## 使用方式

### 1. 基本访问
- 访问 `http://localhost:8443/tongrenlu/player.html` 进入播放器
- 默认加载随机专辑

### 2. 指定专辑
- 通过URL参数指定专辑：`player.html?album={专辑ID}`
- 示例：`player.html?album=1001`

### 3. 测试模式
- 设置专辑ID为 `test001` 进入测试模式
- 使用内置测试专辑数据

### 4. 交互说明
1. **播放控制**：
   - 点击播放/暂停按钮
   - 使用上一首/下一首按钮切换曲目
   - 拖动进度条跳转播放位置

2. **音量控制**：
   - 点击音量图标静音/取消静音
   - 拖动音量滑块调整音量

3. **播放列表**：
   - 点击曲目直接播放
   - 当前播放曲目高亮显示

4. **歌词功能**：
   - 点击歌词按钮切换播放列表/歌词显示
   - 歌词随播放时间自动滚动和高亮

5. **全屏模式**：
   - 点击全屏按钮进入/退出全屏

## 技术依赖

### 前端库
- **jQuery 3.x** - DOM操作和事件处理
- **Font Awesome 5** - 图标字体
- **Google Fonts** - 字体加载（Noto Sans SC, Noto Serif SC）

### 浏览器支持
- 现代浏览器（Chrome 70+, Firefox 65+, Edge 79+）
- 支持HTML5 Audio API
- 支持CSS3 Flexbox和动画

### 浏览器自动播放策略
- 需要用户交互后才能自动播放音频
- 播放器提供播放覆盖层引导用户点击播放
- 播放列表播放结束后，**自动重新加载随机专辑**（已实现）

## 开发说明

### 模块化设计原则
1. **单一职责**：每个文件专注于单一功能领域
2. **代码复用**：提取通用工具函数
3. **文件大小**：每个文件不超过200行
4. **松耦合**：模块间通过事件或全局变量通信

### 事件系统
播放器使用jQuery事件系统进行通信：
- `trackPlay` - 曲目开始播放时触发
- `trackPause` - 曲目暂停时触发
- `trackEnd` - 曲目播放结束时触发
- `trackError` - 播放错误时触发

### 调试信息
播放器包含详细的控制台日志：
- 数据加载状态
- 播放控制流程
- 歌词解析和更新
- 错误信息

## 待办功能和改进

### 当前限制
1. 移动端适配不完善
2. 缺少播放历史记录
3. 缺少歌曲收藏功能
4. 音效均衡器待添加

### 计划功能
1. **高级播放模式**：
   - 随机播放算法优化
   - 自定义播放顺序
2. **用户功能**：
   - 用户登录和个性化
   - 播放记录同步
3. **音频增强**：
   - 均衡器预设
   - 音效调整
4. **社交功能**：
   - 分享播放列表
   - 评论和点赞

### 性能优化
1. 音频预加载
2. 图片懒加载
3. 歌词缓存
4. 模块懒加载

## 故障排除

### 常见问题

1. **音频无法播放**
   - 检查浏览器控制台错误
   - 确认网络连接正常
   - 检查音频格式浏览器支持

2. **歌词不显示**
   - 确认API返回的歌词数据格式正确
   - 检查歌词是否为标准LRC格式
   - 查看控制台歌词解析日志

3. **专辑加载失败**
   - 检查API接口是否可达
   - 确认数据库中有相应的专辑数据
   - 检查网络连接和CORS配置

4. **样式显示异常**
   - 清除浏览器缓存
   - 检查CSS文件是否正常加载
   - 确认浏览器支持CSS3特性

### 调试方法
1. 打开浏览器开发者工具（F12）
2. 查看控制台日志和错误信息
3. 检查网络请求状态
4. 查看元素样式和布局

## 注意事项

1. **浏览器兼容性**：确保使用支持HTML5 Audio的现代浏览器
2. **网络要求**：音频文件可能较大，需要稳定的网络连接
3. **移动设备**：在移动设备上可能存在自动播放限制
4. **性能考虑**：长时间播放时注意内存使用
5. **安全性**：确保音频文件来源可信

---

*文档最后更新：2025-12-09*
*东方同人录音乐播放器版本：1.0*