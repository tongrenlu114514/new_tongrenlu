## ADDED Requirements

### Requirement: 最新专辑区域必须展示最近发布的专辑

最新专辑区域 SHALL 展示最近发布的专辑，帮助用户发现新内容。

#### Scenario: 页面加载最新专辑
- **WHEN** 主页加载完成
- **THEN** 系统 SHALL 获取最新发布的专辑列表
- **AND** 默认展示数量 SHALL 为 10 张专辑
- **AND** 专辑卡片 SHALL 显示封面图（使用 cloudMusicPicUrl 字段）

### Requirement: 最新专辑必须支持轮播浏览

最新专辑 SHALL 以横向轮播形式展示，用户可以浏览更多内容。

#### Scenario: 用户浏览轮播
- **WHEN** 最新专辑区域显示
- **THEN** 专辑卡片 SHALL 以横向滚动形式排列
- **AND** 系统 SHALL 提供左右导航按钮
- **AND** 轮播 SHALL 支持自动播放（间隔 5 秒）

#### Scenario: 用户手动控制轮播
- **WHEN** 用户点击导航按钮
- **THEN** 轮播 SHALL 移动到下一张或上一张专辑
- **AND** 自动播放 SHALL 暂停

#### Scenario: 用户悬停轮播
- **WHEN** 用户鼠标悬停在轮播区域
- **THEN** 自动播放 SHALL 暂停
- **AND** 移开鼠标后 SHALL 恢复自动播放

### Requirement: 专辑卡片必须包含封面图

每张专辑卡片 SHALL 显示专辑封面图。

#### Scenario: 专辑卡片展示封面
- **WHEN** 专辑卡片渲染
- **THEN** 卡片 SHALL 显示专辑封面图（cloudMusicPicUrl）
- **AND** 卡片 SHALL 显示专辑标题
- **AND** 卡片 SHALL 显示艺术家名称
- **AND** 悬停时 SHALL 显示播放按钮

### Requirement: 点击专辑卡片必须跳转播放器

用户点击专辑卡片 SHALL 能够打开播放器播放该专辑。

#### Scenario: 点击专辑卡片
- **WHEN** 用户点击专辑卡片或播放按钮
- **THEN** 系统 SHALL 打开全屏播放器并加载该专辑
