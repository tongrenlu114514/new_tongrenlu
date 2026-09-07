## ADDED Requirements

### Requirement: 首页必须支持响应式布局

首页 SHALL 在不同设备尺寸下正确显示。

#### Scenario: 桌面端显示
- **WHEN** 用户使用宽度 > 1024px 的设备访问
- **THEN** 画廊 SHALL 使用 5 列网格布局
- **AND** 画廊 SHALL 包含大图、宽图、竖图混合布局

#### Scenario: 平板端显示
- **WHEN** 用户使用宽度 <= 1024px 的设备访问
- **THEN** 画廊 SHALL 使用 4 列网格布局
- **AND** 大图布局 SHALL 调整为横跨 2 列

#### Scenario: 移动端显示
- **WHEN** 用户使用宽度 <= 768px 的设备访问
- **THEN** 画廊 SHALL 使用 3 列或 2 列网格布局
- **AND** 所有特殊布局（大图/宽图/竖图）SHALL 简化为普通格子

### Requirement: 音乐库布局限制

音乐库专辑网格 SHALL 限制每行最多显示的专辑数量，并提供骨架屏加载效果。

#### Scenario: 大屏幕音乐库显示
- **WHEN** 用户使用宽度 > 1600px 的设备访问音乐库
- **THEN** 专辑网格 SHALL 每行最多显示 8 个专辑

#### Scenario: 中等屏幕音乐库显示
- **WHEN** 用户使用宽度 1200px-1600px 的设备访问音乐库
- **THEN** 专辑网格 SHALL 每行最多显示 6 个专辑

#### Scenario: 小屏幕音乐库显示
- **WHEN** 用户使用宽度 1024px-1200px 的设备访问音乐库
- **THEN** 专辑网格 SHALL 每行最多显示 5 个专辑

#### Scenario: 平板音乐库显示
- **WHEN** 用户使用宽度 768px-1024px 的设备访问音乐库
- **THEN** 专辑网格 SHALL 每行最多显示 4 个专辑

#### Scenario: 移动端音乐库显示
- **WHEN** 用户使用宽度 < 768px 的设备访问音乐库
- **THEN** 专辑网格 SHALL 每行最多显示 3 个或 2 个专辑

#### Scenario: 骨架屏加载效果
- **WHEN** 搜索结果正在加载
- **THEN** 系统 SHALL 显示骨架屏卡片
- **AND** 骨架屏 SHALL 使用渐变动画效果
- **AND** 骨架屏布局 SHALL 与实际卡片布局一致

### Requirement: 画廊必须适配移动端

画廊在移动端 SHALL 简化布局，保证可读性。

#### Scenario: 移动端画廊简化
- **WHEN** 用户使用移动设备访问
- **THEN** 画廊 SHALL 使用更简单的网格布局
- **AND** 专辑封面点击 SHALL 保持可用

### Requirement: 轮播必须支持触摸手势

轮播组件 SHALL 支持移动端触摸滑动操作。

#### Scenario: 触摸滑动轮播
- **WHEN** 用户在轮播区域左右滑动
- **THEN** 轮播 SHALL 跟随手指移动
- **AND** 滑动结束后 SHALL 滚动到最近的专辑卡片

### Requirement: 搜索建议面板响应式

搜索建议面板 SHALL 在不同设备尺寸下正确显示。

#### Scenario: 移动端建议面板
- **WHEN** 用户在移动设备上使用搜索功能
- **THEN** 建议面板 SHALL 全宽显示
- **AND** 建议项 SHALL 具有足够的触摸区域（最小 44px 高度）

### Requirement: 筛选排序控件响应式

筛选和排序控件 SHALL 在移动端优化显示。

#### Scenario: 移动端标签滚动
- **WHEN** 标签数量超过屏幕宽度
- **THEN** 标签容器 SHALL 支持横向滚动
- **AND** 首尾标签 SHALL 显示渐变阴影提示

#### Scenario: 移动端排序控件
- **WHEN** 用户在移动设备访问
- **THEN** 排序控件 SHALL 以更紧凑的形式显示
- **AND** 下拉选项 SHALL 具有足够的触摸区域