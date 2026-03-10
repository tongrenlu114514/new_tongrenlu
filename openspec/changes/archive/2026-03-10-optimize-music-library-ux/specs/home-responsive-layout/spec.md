## MODIFIED Requirements

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

## ADDED Requirements

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
