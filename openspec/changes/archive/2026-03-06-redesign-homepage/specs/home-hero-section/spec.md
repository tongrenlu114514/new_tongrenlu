## ADDED Requirements

### Requirement: Hero 区域使用图片画廊风格

Hero 区域 SHALL 使用图片画廊风格展示专辑封面网格，作为页面背景。

#### Scenario: 显示画廊背景
- **WHEN** 用户访问首页
- **THEN** 系统显示 15 个专辑封面组成的画廊网格
- **AND** 画廊包含大图、宽图、竖图混合布局

#### Scenario: 随机排列封面
- **WHEN** 用户访问首页或画廊自动刷新
- **THEN** 系统通过 API 获取随机专辑封面
- **AND** 每次刷新看到的封面排列不同

### Requirement: 导航栏显示专辑统计

导航栏 SHALL 显示专辑数量统计信息。

#### Scenario: 显示专辑数量
- **WHEN** 用户访问首页
- **THEN** 系统在导航栏显示专辑数量统计
- **AND** 显示格式为「X 张专辑」

### Requirement: 封面悬停显示标题

用户悬停专辑封面时 SHALL 显示专辑标题。

#### Scenario: 悬停显示标题
- **WHEN** 用户将鼠标悬停在画廊中的专辑封面上
- **THEN** 系统显示该专辑的标题
- **AND** 封面放大并增加透明度

### Requirement: 画廊自动刷新

画廊 SHALL 定时自动刷新专辑封面。

#### Scenario: 自动刷新
- **WHEN** 用户停留在首页
- **THEN** 系统每 30 秒自动刷新画廊封面
- **AND** 刷新时使用淡入淡出动画效果

### Requirement: 点击封面跳转播放器

用户 SHALL 能够点击画廊中的专辑封面跳转到播放器。

#### Scenario: 点击封面
- **WHEN** 用户点击画廊中的任意专辑封面
- **THEN** 系统打开播放器页面并加载该专辑