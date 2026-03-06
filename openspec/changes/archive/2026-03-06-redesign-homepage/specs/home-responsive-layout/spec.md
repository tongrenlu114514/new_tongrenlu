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

音乐库专辑网格 SHALL 限制每行最多显示的专辑数量。

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