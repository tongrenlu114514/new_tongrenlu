## Why

当前音乐库页面存在多项用户体验问题：搜索功能较为基础（无自动补全、无搜索历史）、筛选标签为硬编码且与实际数据脱节、缺少排序功能、分页控件样式简陋、加载状态缺少骨架屏反馈。这些痛点导致用户难以高效发现和浏览内容，影响整体使用体验。

## What Changes

- 新增搜索建议功能，输入时自动展示匹配的专辑/艺术家建议
- 新增搜索历史记录，保存用户最近搜索词
- 将硬编码的筛选标签改为动态加载，与后端标签数据关联
- 新增排序功能，支持按最新、热门、标题等维度排序
- 优化分页控件样式，添加页码跳转和总数显示
- 新增骨架屏加载效果，提升加载时的视觉反馈
- 优化音乐卡片悬停交互和响应式布局

## Capabilities

### New Capabilities

- `music-search-suggestions`: 搜索建议与自动补全功能，输入时展示匹配结果
- `music-filter-sort`: 动态筛选标签与排序功能，支持多维度内容过滤和排序

### Modified Capabilities

- `home-responsive-layout`: 扩展响应式布局支持以适配音乐库页面的优化需求

## Impact

- **前端代码**:
  - `components/music-library/music-library.js` - 核心交互逻辑
  - `assets/css/style.css` - 样式优化
  - `index.html` - HTML 结构调整
- **后端 API**:
  - `api/music/suggestions` - 新增搜索建议接口
  - `api/music/tags` - 新增标签列表接口
  - `api/music/search` - 扩展排序参数支持
- **依赖**: 无新增外部依赖
