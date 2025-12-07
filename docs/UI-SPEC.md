# UI规范文档 (UI-SPEC)

## 1. 设计系统概览

### 1.1 设计理念
- **简洁直观**: 界面简洁明了，操作路径清晰
- **内容优先**: 突出同人创作内容，减少干扰元素
- **一致性**: 保持视觉和交互的一致性
- **响应式**: 支持多设备适配

### 1.2 设计原则
- 减少认知负担
- 提供明确的视觉反馈
- 保持界面层次清晰
- 优化加载体验

## 2. 设计规范系统

### 2.1 色彩体系

#### 2.1.1 主色调
```scss
// 主品牌色
$primary-color: #8B5CF6;          // 紫色，代表创作和灵感
$primary-hover: #7C3AED;
$primary-active: #6D28D9;

// 辅助色
$secondary-color: #10B981;        // 绿色，代表成功和确认
$info-color: #3B82F6;             // 蓝色，代表信息和链接
$warning-color: #F59E0B;          // 橙色，代表警告
$error-color: #EF4444;            // 红色，代表错误和危险
```

#### 2.1.2 中性色系
```scss
// 文字颜色
$text-primary: #1F2937;           // 主要文字
$text-secondary: #6B7280;         // 次要文字
$text-tertiary: #9CA3AF;          // 辅助文字
$text-disabled: #D1D5DB;          // 禁用文字

// 背景颜色
$bg-primary: #FFFFFF;             // 主背景
$bg-secondary: #F9FAFB;           // 次要背景
$bg-tertiary: #F3F4F6;           // 辅助背景
$bg-overlay: rgba(0, 0, 0, 0.5);  // 遮罩背景

// 边框颜色
$border-light: #E5E7EB;           // 浅色边框
$border-normal: #D1D5DB;          // 正常边框
$border-dark: #9CA3AF;            // 深色边框
```

#### 2.1.3 语义色系
```scss
// 成功状态
$success-bg: #D1FAE5;
$success-text: #065F46;

// 警告状态
$warning-bg: #FEF3C7;
$warning-text: #92400E;

// 错误状态
$error-bg: #FEE2E2;
$error-text: #991B1B;

// 信息状态
$info-bg: #DBEAFE;
$info-text: #1E40AF;
```

### 2.2 字体规范

#### 2.2.1 字体家族
```scss
$font-family-base: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto,
                   "Helvetica Neue", Arial, "Noto Sans", sans-serif;
$font-family-heading: "Noto Sans SC", "Microsoft YaHei", "PingFang SC",
                      "Hiragino Sans GB", sans-serif;
$font-family-code: "Monaco", "Consolas", "Courier New", monospace;
```

#### 2.2.2 字体大小
```scss
// 标题层级
$font-size-xxl: 2.5rem;      // 40px - H1
$font-size-xl: 2rem;         // 32px - H2
$font-size-lg: 1.5rem;       // 24px - H3
$font-size-md: 1.25rem;      // 20px - H4
$font-size-base: 1rem;       // 16px - 正文
$font-size-sm: 0.875rem;     // 14px - 小文本
$font-size-xs: 0.75rem;      // 12px - 辅助文本
```

#### 2.2.3 字重和行高
```scss
$font-weight-light: 300;
$font-weight-normal: 400;
$font-weight-medium: 500;
$font-weight-semibold: 600;
$font-weight-bold: 700;

$line-height-tight: 1.25;
$line-height-normal: 1.5;
$line-height-loose: 1.75;
```

### 2.3 间距系统

#### 2.3.1 基础单位
基础间距单位: **8px** (0.5rem)

#### 2.3.2 间距阶梯
```scss
$space-0: 0;          // 0px
$space-1: 0.5rem;     // 8px
$space-2: 1rem;       // 16px
$space-3: 1.5rem;     // 24px
$space-4: 2rem;       // 32px
$space-5: 3rem;       // 48px
$space-6: 4rem;       // 64px
$space-8: 6rem;       // 96px
$space-10: 8rem;      // 128px
```

#### 2.3.3 应用场景
- 组件内间距: $space-2 (16px)
- 组件间间距: $space-3 (24px)
- 区块间间距: $space-4 (32px)
- 页面边距: $space-4 (32px)

### 2.4 圆角规范
```scss
$radius-none: 0;
$radius-sm: 0.25rem;      // 4px
$radius-base: 0.5rem;     // 8px
$radius-md: 0.75rem;      // 12px
$radius-lg: 1rem;         // 16px
$radius-xl: 1.5rem;       // 24px
$radius-full: 9999px;     // 圆形
```

### 2.5 阴影规范
```scss
$shadow-sm: 0 1px 2px 0 rgba(0, 0, 0, 0.05);
$shadow-base: 0 1px 3px 0 rgba(0, 0, 0, 0.1), 0 1px 2px 0 rgba(0, 0, 0, 0.06);
$shadow-md: 0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06);
$shadow-lg: 0 10px 15px -3px rgba(0, 0, 0, 0.1), 0 4px 6px -2px rgba(0, 0, 0, 0.05);
$shadow-xl: 0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04);
```

## 3. 组件设计规范

### 3.1 按钮组件

#### 3.1.1 按钮变体
```scss
// 主按钮
.btn-primary {
  background: $primary-color;
  color: white;

  &:hover { background: $primary-hover; }
  &:active { background: $primary-active; }
}

// 次按钮
.btn-secondary {
  background: transparent;
  color: $primary-color;
  border: 1px solid $primary-color;

  &:hover {
    background: $primary-color;
    color: white;
  }
}

// 文字按钮
.btn-text {
  background: transparent;
  color: $text-secondary;

  &:hover { color: $primary-color; }
}

// 禁用状态
.btn-disabled {
  background: $bg-tertiary;
  color: $text-disabled;
  cursor: not-allowed;
}
```

#### 3.1.2 按钮尺寸
```scss
.btn-sm {
  padding: $space-1 $space-2;
  font-size: $font-size-sm;
  height: 32px;
}

.btn-md {
  padding: $space-2 $space-3;
  font-size: $font-size-base;
  height: 40px;
}

.btn-lg {
  padding: $space-3 $space-4;
  font-size: $font-size-md;
  height: 48px;
}
```

### 3.2 表单组件

#### 3.2.1 输入框
```scss
.input-base {
  border: 1px solid $border-normal;
  border-radius: $radius-base;
  padding: $space-2;
  font-size: $font-size-base;

  &:focus {
    border-color: $primary-color;
    box-shadow: 0 0 0 3px rgba(139, 92, 246, 0.1);
  }

  &.error {
    border-color: $error-color;
    box-shadow: 0 0 0 3px rgba(239, 68, 68, 0.1);
  }
}
```

#### 3.2.2 选择器
```scss
.select-base {
  @extend .input-base;
  background-image: url("data:image/svg+xml,...");
  background-position: right $space-2 center;
  background-repeat: no-repeat;
  background-size: 16px;
  padding-right: $space-8;
}
```

### 3.3 卡片组件
```scss
.card {
  background: white;
  border-radius: $radius-lg;
  box-shadow: $shadow-base;
  overflow: hidden;

  &-header {
    padding: $space-4;
    border-bottom: 1px solid $border-light;
  }

  &-body {
    padding: $space-4;
  }

  &-footer {
    padding: $space-4;
    border-top: 1px solid $border-light;
    background: $bg-secondary;
  }
}
```

### 3.4 音乐卡片设计
```scss
.music-card {
  @extend .card;
  transition: transform 0.2s, box-shadow 0.2s;

  &:hover {
    transform: translateY(-2px);
    box-shadow: $shadow-lg;
  }

  .cover-image {
    width: 100%;
    aspect-ratio: 1;
    object-fit: cover;
  }

  .music-info {
    padding: $space-3;

    .title {
      font-size: $font-size-md;
      font-weight: $font-weight-semibold;
      margin-bottom: $space-1;
    }

    .artist {
      color: $text-secondary;
      font-size: $font-size-sm;
    }

    .stats {
      display: flex;
      gap: $space-3;
      margin-top: $space-2;

      .stat-item {
        display: flex;
        align-items: center;
        gap: $space-1;
        color: $text-secondary;
        font-size: $font-size-xs;
      }
    }
  }
}
```

## 4. 布局系统

### 4.1 栅格系统
采用12列栅格系统
```scss
.container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 $space-4;
}

.row {
  display: flex;
  flex-wrap: wrap;
  margin: 0 -$space-2;
}

.col-1 { width: 8.333%; }
.col-2 { width: 16.667%; }
.col-3 { width: 25%; }
.col-4 { width: 33.333%; }
.col-6 { width: 50%; }
.col-8 { width: 66.667%; }
.col-12 { width: 100%; }
```

### 4.2 响应式断点
```scss
$breakpoint-sm: 640px;    // 手机
$breakpoint-md: 768px;    // 平板
$breakpoint-lg: 1024px;   // 小桌面
$breakpoint-xl: 1280px;   // 大桌面

@media (min-width: $breakpoint-md) {
  .container { padding: 0 $space-6; }
}
```

## 5. 动效规范

### 5.1 过渡动画
```scss
$transition-fast: 0.15s ease-in-out;
$transition-normal: 0.3s ease-in-out;
$transition-slow: 0.5s ease-in-out;

// 应用示例
.fade-in {
  animation: fadeIn $transition-normal;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}
```

### 5.2 交互反馈
```scss
// 悬停效果
.hover-lift {
  transition: transform $transition-fast;

  &:hover {
    transform: translateY(-2px);
  }
}

// 点击效果
.active-scale {
  transition: transform $transition-fast;

  &:active {
    transform: scale(0.98);
  }
}
```

## 6. 内容展示规范

### 6.1 图片处理
- 封面图比例: 1:1 (正方形)
- 文章配图比例: 16:9
- 头像比例: 1:1，圆形裁剪
- 图片格式优化: WebP优先，JPEG备选

### 6.2 文字排版
```scss
// 文章正文
.article-content {
  line-height: $line-height-loose;
  font-size: $font-size-base;

  h1, h2, h3, h4, h5, h6 {
    margin-top: $space-4;
    margin-bottom: $space-2;
    font-weight: $font-weight-semibold;
  }

  p {
    margin-bottom: $space-3;
  }

  img {
    max-width: 100%;
    height: auto;
    border-radius: $radius-base;
  }
}
```

## 7. 无障碍设计

### 7.1 键盘导航
- Tab键顺序符合视觉流
- 焦点状态明显可见
- 支持回车键触发主要操作

### 7.2 屏幕阅读器支持
- 语义化HTML标签
- 合适的ARIA属性
- 图片alt文本描述
- 表单标签关联

## 8. 图标规范

### 8.1 图标尺寸
```scss
$icon-xs: 16px;
$icon-sm: 20px;
$icon-md: 24px;
$icon-lg: 32px;
$icon-xl: 48px;
```

### 8.2 常用图标
- 🔍 搜索
- ♡ 收藏
- ▶️ 播放
- ⏸️ 暂停
- ⬇️ 下载
- 📝 编辑
- 🗑️ 删除
- 👤 用户
- 🎵 音乐
- 📄 文章

## 9. 设计资源

### 9.1 设计文件
- Figma设计稿链接
- 图标库文件
- 组件标注文档

### 9.2 开发资源
- CSS变量定义文件
- 组件库文档
- 样式指南网站

---
**文档版本**: v1.0
**最后更新**: 2025-12-07
**负责人**: 设计团队