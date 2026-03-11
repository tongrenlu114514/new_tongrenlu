## Why

当前网站缺少一个面向访客的艺术家展示页面，现有的 `artist.html` 仅用于管理后台。用户希望能以视觉特效的方式展示大量艺术家名称，提升浏览体验和视觉吸引力。

## What Changes

- 新增公开访问的艺术家页面（在首页导航中可点击进入）
- 使用粒子/星空特效背景展示大量艺术家名称
- 艺术家名称可点击，跳转到该艺术家的专辑列表
- 支持搜索过滤艺术家
- 响应式布局适配移动端

## Capabilities

### New Capabilities

- `artist-showcase`: 艺术家展示页面，使用视觉特效展示大量艺术家名称

### Modified Capabilities

- 无现有 capability 需要修改

## Impact

- **前端代码**:
  - 新增 `artist-showcase.html` - 艺术家展示页面
  - 新增 `components/artist-showcase/` - 页面组件和脚本
  - `index.html` - 导航链接更新
- **后端 API**:
  - 新增 `GET /api/artists` - 返回所有艺术家列表（支持搜索）
- **依赖**: 无新增外部依赖
