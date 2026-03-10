## ADDED Requirements

### Requirement: 搜索建议实时展示

用户在搜索框输入时，系统 SHALL 实时展示匹配的搜索建议。

#### Scenario: 输入触发建议
- **WHEN** 用户在搜索框输入至少 1 个字符
- **THEN** 系统 SHALL 在 300ms 防抖后调用建议接口
- **AND** 系统 SHALL 展示最多 5 条匹配建议

#### Scenario: 建议内容展示
- **WHEN** 建议接口返回结果
- **THEN** 系统 SHALL 展示匹配的专辑标题
- **AND** 每条建议 SHALL 显示标题和艺术家名称

#### Scenario: 点击建议执行搜索
- **WHEN** 用户点击某条建议
- **THEN** 系统 SHALL 使用建议文本执行搜索
- **AND** 搜索建议面板 SHALL 关闭

### Requirement: 搜索历史记录

系统 SHALL 记录用户的搜索历史并在搜索框聚焦时展示。

#### Scenario: 保存搜索历史
- **WHEN** 用户执行搜索
- **THEN** 系统 SHALL 将搜索词保存到 localStorage
- **AND** 历史 SHALL 保留最近 10 条搜索

#### Scenario: 展示搜索历史
- **WHEN** 用户聚焦搜索框且输入为空
- **THEN** 系统 SHALL 展示搜索历史列表
- **AND** 每条历史 SHALL 显示删除按钮

#### Scenario: 清除单条历史
- **WHEN** 用户点击历史项的删除按钮
- **THEN** 该条历史 SHALL 从列表中移除

#### Scenario: localStorage 不可用时降级
- **WHEN** localStorage 被禁用或不可用
- **THEN** 搜索历史功能 SHALL 静默跳过
- **AND** 其他功能 SHALL 保持正常

### Requirement: 搜索建议面板样式

搜索建议面板 SHALL 具有清晰的视觉层级和交互反馈。

#### Scenario: 建议面板定位
- **WHEN** 建议面板展示
- **THEN** 面板 SHALL 定位在搜索框下方
- **AND** 面板宽度 SHALL 与搜索框一致

#### Scenario: 建议项悬停效果
- **WHEN** 用户悬停在建议项上
- **THEN** 建议 SHALL 显示高亮背景色
- **AND** 光标 SHALL 变为指针

#### Scenario: 键盘导航
- **WHEN** 用户按下上下方向键
- **THEN** 高亮 SHALL 在建议项间移动
- **WHEN** 用户按下 Enter 键
- **THEN** 当前高亮的建议 SHALL 被选中执行搜索
