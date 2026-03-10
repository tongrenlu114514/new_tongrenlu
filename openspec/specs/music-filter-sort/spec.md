## ADDED Requirements

### Requirement: 动态筛选标签加载

筛选标签 SHALL 从后端动态加载，与数据库标签数据关联。

#### Scenario: 页面加载获取标签
- **WHEN** 音乐库页面加载完成
- **THEN** 系统 SHALL 调用 `api/music/tags` 接口
- **AND** 系统 SHALL 展示返回的热门标签

#### Scenario: 标签默认选项
- **WHEN** 标签接口返回数据
- **THEN** 系统 SHALL 在列表首位展示"全部"选项
- **AND** "全部"选项 SHALL 默认为选中状态

#### Scenario: 标签接口失败降级
- **WHEN** 标签接口请求失败
- **THEN** 系统 SHALL 仅展示"全部"标签
- **AND** 系统 SHALL 不展示错误提示

### Requirement: 标签筛选交互

用户 SHALL 能够通过点击标签筛选内容。

#### Scenario: 点击标签筛选
- **WHEN** 用户点击某个标签
- **THEN** 该标签 SHALL 变为选中状态
- **AND** 其他标签 SHALL 取消选中
- **AND** 系统 SHALL 刷新搜索结果

#### Scenario: 当前标签视觉反馈
- **WHEN** 标签处于选中状态
- **THEN** 标签 SHALL 显示高亮背景色
- **AND** 标签文字颜色 SHALL 对比清晰

### Requirement: 多维度排序功能

用户 SHALL 能够按多种维度对搜索结果排序。

#### Scenario: 排序选项展示
- **WHEN** 音乐库页面加载
- **THEN** 系统 SHALL 展示排序下拉框
- **AND** 默认排序 SHALL 为"最新发布"

#### Scenario: 按发布时间排序
- **WHEN** 用户选择"最新发布"排序
- **THEN** 系统 SHALL 按发布日期降序排列结果
- **AND** URL 参数 SHALL 包含 `orderBy=publishDate`

#### Scenario: 按热度排序
- **WHEN** 用户选择"最多播放"排序
- **THEN** 系统 SHALL 按访问量降序排列结果
- **AND** URL 参数 SHALL 包含 `orderBy=accessCount`

#### Scenario: 按标题排序
- **WHEN** 用户选择"标题排序"排序
- **THEN** 系统 SHALL 按标题拼音升序排列结果
- **AND** URL 参数 SHALL 包含 `orderBy=title`

### Requirement: 分页控件增强

分页控件 SHALL 提供完整的导航功能。

#### Scenario: 分页信息展示
- **WHEN** 分页控件渲染
- **THEN** 控件 SHALL 显示当前页码
- **AND** 控件 SHALL 显示总页数
- **AND** 控件 SHALL 显示总记录数

#### Scenario: 页码跳转输入
- **WHEN** 用户在跳转输入框输入页码并确认
- **THEN** 系统 SHALL 跳转到指定页码
- **AND** 输入超出范围 SHALL 跳转到最接近的有效页

#### Scenario: 省略号显示
- **WHEN** 总页数超过 7 页
- **THEN** 分页控件 SHALL 使用省略号压缩显示
- **AND** 当前页附近 2 页 SHALL 始终显示
