## Why

当前主页设计虽然功能完整，但存在以下问题：
- 视觉层次不够清晰，Hero 区域缺乏视觉冲击力
- 缺少最新专辑展示入口
- 进度条组件功能单一，没有充分利用首页流量展示更多内容
- 移动端体验需要优化

现在是重新设计的时机，因为内容库已经积累足够，需要更好的展示方式来吸引用户探索。

## What Changes

- 重新设计 Hero 区域，采用**图片画廊风格**展示专辑封面
- 画廊背景使用随机排列的专辑封面，配合大图/宽图/竖图布局
- 画廊上方叠加半透明遮罩层，显示标题、副标题、专辑数量统计和 CTA 按钮
- 添加「最新专辑」轮播展示区域，突出新发布内容
- 在 Hero 区域显示专辑数量统计（移除进度条）
- 优化移动端响应式布局

## Capabilities

### New Capabilities

- `home-hero-section`: 首页 Hero 区域设计，采用**图片画廊风格**，背景展示专辑封面网格，中心叠加标题、副标题、专辑数量统计、CTA 按钮
- `home-latest-albums`: 最新专辑轮播展示区域，展示最近发布的专辑
- `home-responsive-layout`: 首页响应式布局，移动端适配优化

### Modified Capabilities

（无 - 这是全新的设计，不修改现有的 spec-level 行为）

## Impact

- 前端文件：
  - `index.html` - 主页 HTML 结构重构
  - `assets/css/home-redesign.css` - 新增样式文件
  - `components/home/gallery-hero.js` - 新增画廊组件
  - `components/home/latest-albums.js` - 最新专辑组件
  - `components/home/home.js` - JavaScript 逻辑更新
- 后端 API：复用现有 API，无需新增端点
- 用户影响：首次访问用户将看到全新的主页体验