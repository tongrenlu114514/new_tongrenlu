---
name: 东方同人录 TONGRENLU
description: 东方同人音乐的可检索声音档案——白底图鉴式内容页与奶油纸色首页共用一套朱红 / 墨蓝语言
colors:
  ink: "#1a1a2e"
  ink-soft: "#23233d"
  accent: "#e94560"
  accent-deep: "#c22c47"
  blue: "#0f3460"
  paper: "#ffffff"
  paper-2: "#f6f7fb"
  line: "#e5e7ef"
  muted: "#666b80"
  body-text: "#3a3d52"
  archive-ink: "#0b0b10"
  archive-ink-2: "#14141c"
  archive-paper: "#f4f3ef"
  archive-paper-2: "#e9e7e1"
typography:
  display:
    fontFamily: "Bebas Neue, Noto Sans SC, sans-serif"
    fontSize: "clamp(3.2rem, 9vw, 6.5rem)"
    fontWeight: 400
    lineHeight: 0.95
    letterSpacing: "0.04em"
  headline:
    fontFamily: "Bebas Neue, Noto Sans SC, sans-serif"
    fontSize: "clamp(2.2rem, 5vw, 3.6rem)"
    fontWeight: 400
    lineHeight: 1
    letterSpacing: "0.03em"
  title:
    fontFamily: "Bebas Neue, Noto Sans SC, sans-serif"
    fontSize: "clamp(1.5rem, 2.6vw, 1.9rem)"
    fontWeight: 400
    lineHeight: 1.15
    letterSpacing: "0.12em"
  body:
    fontFamily: "Noto Sans SC, sans-serif"
    fontSize: "0.96rem"
    fontWeight: 300
    lineHeight: 2
    letterSpacing: "0.01em"
  label:
    fontFamily: "Space Grotesk, Noto Sans SC, sans-serif"
    fontSize: "0.72rem"
    fontWeight: 500
    lineHeight: 1.4
    letterSpacing: "0.35em"
  numeric:
    fontFamily: "Outfit, sans-serif"
    fontSize: "0.88rem"
    fontWeight: 500
    letterSpacing: "0.05em"
  mono:
    fontFamily: "JetBrains Mono, ui-monospace, SFMono-Regular, monospace"
    fontSize: "0.75rem"
    fontWeight: 400
    lineHeight: 1.6
    letterSpacing: "0.04em"
rounded:
  none: "0px"
  avatar: "50%"
spacing:
  xs: "0.5rem"
  sm: "1rem"
  md: "1.5rem"
  lg: "2.5rem"
  xl: "4rem"
  2xl: "clamp(4rem, 9vw, 7.5rem)"
  nav: "76px"
components:
  nav-bar:
    backgroundColor: "{colors.paper}"
    textColor: "{colors.muted}"
    padding: "1rem 2.5rem"
  archive-card:
    backgroundColor: "{colors.paper}"
    textColor: "{colors.ink}"
    rounded: "{rounded.none}"
    padding: "1rem 1.1rem 1.1rem"
  archive-card-hover:
    backgroundColor: "{colors.paper}"
    textColor: "{colors.accent}"
  tag-chip:
    backgroundColor: "{colors.paper-2}"
    textColor: "{colors.ink}"
    rounded: "{rounded.none}"
    padding: "0.42rem 0.9rem"
  stat-badge:
    backgroundColor: "{colors.blue}"
    textColor: "{colors.paper}"
    padding: "0.42rem 0.95rem"
  search-input:
    backgroundColor: "{colors.paper}"
    textColor: "{colors.ink}"
    rounded: "{rounded.none}"
    padding: "1.1rem 3.2rem 1.1rem 1.4rem"
  section-panel:
    backgroundColor: "{colors.paper}"
    textColor: "{colors.body-text}"
    rounded: "{rounded.none}"
    padding: "clamp(1.5rem, 3vw, 2.4rem)"
  count-badge:
    backgroundColor: "{colors.accent}"
    textColor: "{colors.paper}"
    padding: "0.3rem 0.65rem"
  index-row:
    backgroundColor: "{colors.archive-paper}"
    textColor: "{colors.archive-ink}"
    padding: "1rem 0"
  index-row-hover:
    backgroundColor: "{colors.archive-ink}"
    textColor: "{colors.archive-paper}"
  pagination-button:
    backgroundColor: "{colors.paper}"
    textColor: "{colors.ink}"
    rounded: "{rounded.none}"
    padding: "0 0.7rem"
  pagination-button-active:
    backgroundColor: "{colors.accent-deep}"
    textColor: "{colors.paper}"
---

# Design System: 东方同人录 TONGRENLU

## Overview

**Creative North Star: "同人图鉴"**

东方同人录的界面不是唱片店，也不是资料库后台，而是一本可以翻的同人图鉴：白纸、细描线、编号分节、朱红小印章、印在页边的大号编号。信息密度高，但每一页都保持目录感——读者先看到"这是什么、属于谁、有多少张"，再往下读词条、跳到相关专辑。

实现上，这套系统用三件事固定住图鉴气质：**零圆角的切角轮廓**（clip-path 斜切，不用 border-radius）、**硬边偏移阴影**（3–8px、零模糊，像贴纸被揭起）、以及**字号跨度极大的三字体组合**（Bebas Neue 承担标题与编号，Noto Sans SC 承担正文，Space Grotesk 承担控件与标签）。装饰全部来自网格、编号、细线和裁切，不靠渐变光效或大插图。

首页是刻意保留的**姊妹世界**：奶油纸底（`--paper: #f4f3ef` + `--ink: #0b0b10`）的编辑式地标页，比内容页更暗、更像纸质档案封面，但共享同一个朱砂印泥与同一套切角／偏移阴影语言。两个世界不合并——新页面按表面归属选择其一，不混用令牌。

**Key Characteristics:**
- 白纸内容页 + 奶油纸首页两个世界，共享朱砂印泥与切角语言；两套令牌同表登记（`archive-*` 前缀）
- 零圆角；所有"角"由 clip-path 斜切（6 / 8 / 10 / 15px）
- 1.5px 细描线构成所有分隔与边界，不用粗边框
- 硬边偏移阴影（无模糊），只作为 hover / 焦点响应出现
- Bebas Neue 的编号与标题几乎总是大写 + 0.03–0.35em 字距
- 深色页头（墨蓝渐变 + 56px 网格 + 径向朱红辉光）与正文白纸形成硬分界

## Colors

一个近黑的墨蓝、一点朱砂印泥、一个深蓝、一张白纸——没有第四种情绪色。

### Primary
- **朱砂印泥** (`#e94560`): 系统里唯一的强调色，**只用于非文字**：hover 时的阴影与描边、分节标题前的三角标、词条导语的左侧竖条、`::selection` 底色、深色页头里的强调词。它标记"此刻被选中 / 可点 / 是重点"的那一件事。
- **朱砂印泥·深 / `--wiki-accent-ink`** (`#c22c47`): 凡是**承字**的地方都用它——强调色的文字、白字压强调底的徽章与选中态。与纸底 5.6:1、白字压它 5.6:1，过 AA。

### Secondary
- **深蓝** (`#0f3460`): 结构性的第二色。承担 stat 徽章底色、页头径向辉光、`geo-wiki-tag--artist` 的描边与 hover 填充、阅读进度条的起点。它表示"分类/归属"，不表示强调。

### Neutral
- **墨蓝** (`#1a1a2e`): 标题、正文强调、深色页头底色、页脚底色。它承担"黑"的角色，但不给纯黑。
- **墨蓝·柔** (`#23233d`): 深色页头的渐变中段，避免大面积纯墨蓝显得死板。
- **白纸** (`#ffffff`): 卡片、面板、搜索框的底色。
- **纸·二** (`#f6f7fb`): 页面背景的第二层、tag 底、面板内嵌块。用色调差而不是投影做层次。
- **描线** (`#e5e7ef`): 所有 1.5px 边框与分隔线。
- **弱文字** (`#666b80`): 元信息、计数单位、占位符、空状态。纸底 5.3:1、纸·二 4.9:1，过 AA——旧的 `#8a90a6` 只有 3.2:1，不要再用。
- **正文灰** (`#3a3d52`): 词条长文的正文色，比墨蓝浅一级，避免大段文字过重。

### Archive World（首页）

首页是第二个世界。它的令牌与白纸世界**并列登记在同一份规范里**（frontmatter 用 `archive-*` 前缀区分），但两边不互换、不混用：

- **档案墨** (`#0b0b10`): 首页正文、深色块、索引行 hover 反色底。
- **档案墨·二** (`#14141c`): 首页深色块内部的分层，避免大面积纯墨显死。
- **档案纸** (`#f4f3ef`): 首页底色，一张奶油纸，替代内容页的白纸。
- **档案纸·二** (`#e9e7e1`): 首页次级底与分隔区。
- **朱砂印泥·深** (`#c22c47`): 两个世界共用——首页 hover 加深、浅底上的朱红文字。

### Named Rules

**The One Accent Rule.** 朱砂印泥在任何一屏上的覆盖不超过约 10%。它靠稀有来强调；一旦用于大面积底色或装饰性背景，强调就失效。

**The Two Worlds Rule.** 白纸令牌（`--wiki-*`）只用于内容页，奶油纸令牌（`--ink` / `--paper`）只用于首页。两套令牌都在本规范里登记（frontmatter 用 `archive-*` 前缀区分），同一份规范、两套纸，不互相替代——跨页复用等于把两本书的纸混在一起。

**The Ink Accent Rule.** 强调色分两档：`#e94560` 只做描边、阴影、图形与深色底上的文字；凡承载文字、或作为白字底色的强调块，一律用 `#c22c47`（`--wiki-accent-ink`）。拿错一档就是对比度事故。

### Token Layers

界面分红两层，令牌不跨层借用：

- **chrome 层**：导航、页脚、播放器。令牌前缀 `--geo-*`，定义在 `assets/css/geometric-theme.css`。
- **内容层**：页头、搜索、筛选条、列表卡、词条详情。令牌前缀 `--wiki-*` / `--ws-*`，定义在 `assets/css/wiki.css`；列表页共用的皮肤（页头、搜索、筛选条、Chip、卡片底座、网格）在 `assets/css/list-page.css`，页面文件不再自带 `:root`。

两层各有一套灰阶（`--geo-text-light` 与 `--wiki-muted`），做新页面时先判断它属于哪一层，再只引用该层的令牌。

## Typography

**Display Font:** Bebas Neue（fallback: Noto Sans SC, sans-serif）
**Body Font:** Noto Sans SC（300 / 400）
**UI & Label Font:** Space Grotesk（控件、标签、按钮、tag）
**Numeric Font:** Outfit（计数、统计、meta 数字）
**Mono Font:** JetBrains Mono（仅首页：索引行计数与元数据；内容页不使用）

**Character:** 窄体大写 Bebas 给页面"书脊上的标题"感，Space Grotesk 给控件一点工程感，Noto Sans SC 让中文长文保持安静。三者对比强烈但分工严格：Bebas 从不出现在句子里，Space Grotesk 从不承担长段落。

### Hierarchy
- **Display** (400, `clamp(3.2rem, 9vw, 6.5rem)`, 0.95): 列表页页头主标题，几乎总是"中文主词 + accent 色副词"（如 同人<span class="accent">创作者</span>）。
- **Headline** (400, `clamp(2.2rem, 5vw, 3.6rem)`, 1.0): 详情页实体名（艺术家/词条名），0.03em 字距。
- **Title** (400, `clamp(1.5rem, 2.6vw, 1.9rem)`, 1.15, 0.12em): 分节标题「01 词条正文」，前面带 Bebas 编号。
- **Body** (300, `0.96rem`, 2.0): 词条正文；行高 2 是这套系统的阅读节奏，换页面不要降到 1.6 以下。
- **Label** (500, `0.72rem`, 0.35em, uppercase): 页头眉题（Touhou Artist Archive 之类），字距拉到 0.35em。
- **Numeric** (500, `0.88rem`, 0.05em): stat 徽章与计数单位；大数字（如 1840）用 Bebas 而不是 Outfit。
- **Mono** (400, `0.75rem`, 0.04em, JetBrains Mono): 只出现在首页索引行的计数（"228 张"）与元数据；内容页数字用 Outfit 或 Bebas。

### Named Rules

**The Index Number Rule.** 分节编号（01 / 02 / 03）是图鉴的页码，必须用 Bebas、必须由 JS 按可见分节重排，隐藏一节后不允许出现断号。

**The No Sentences In Bebas Rule.** Bebas Neue 只用于标题、编号、与 ≤4 个词的短语。任何完整句子、任何正文，一律 Noto Sans SC。

**The Card Title Rule.** 列表卡片的第一个名字只有一个角色：音乐库与艺术家卡用 Space Grotesk 600（书名与社团名往往很长，需要缩略号）；展会卡的名称是「系列 + 届数」这类两三个字的档案标签，用 Bebas 1.5rem。除此之外不再有第三种卡片标题字体。

## Layout

- **容器**：内容区 `max-width: 1400px`，水平居中；页面级内边距 `2rem`（≤600px 降到 `1.25rem`）。
- **固定导航**：高 76–80px，页面用 `padding-top: 80px` 让开；页头区块内部纵向留白 `--ws-2xl` → `--ws-xl`。
- **网格**：卡片列表 `grid-template-columns: repeat(auto-fill, minmax(210px, 1fr))`，间距 `clamp(1rem, 2.2vw, 1.75rem)`；详情页专辑格同源，minmax 收窄到 150–170px。
- **节奏**：纵向节奏只使用 `--ws-*`（0.5 / 1 / 1.5 / 2.5 / 4rem / clamp(4rem,9vw,7.5rem)）。分节之间固定 `--ws-l`，页头内 `--ws-xl`，全页首屏 `--ws-2xl`。
- **断点**：900px（标题降级、多列收成 3 列）、600px（网格 2 列、内边距收窄、导航压缩）。另有一条 `prefers-reduced-motion` 关闭位移与动画。
- **首页（档案世界）**：导航高 `--nav-h: 76px`，首屏用 `padding-top: calc(var(--nav-h) + 2.6vh)` 让开；索引行是 4 列网格（编号 `2.6rem` / 名称 `minmax(0,1fr)` / 计数 `auto` / 箭头 `1.6rem`），行间只有 1px 半透明墨线，没有卡片边框。
- **密度**：列表页偏密（一屏 8–15 张卡），详情页偏疏（每节独立面板 + 大留白），首页最疏（满屏叙事）。

## Elevation & Depth

系统**没有柔和投影**。深度由三种方式表达：色调分层（白纸 / 纸·二 的色差）、1.5px 描线、以及**硬边偏移阴影**——`box-shadow` 只接受无模糊的偏移值（3px / 5px / 6px / 8px），颜色是朱红或墨蓝。

### Shadow Vocabulary
- **贴纸揭起** (`box-shadow: 5px 5px 0 var(--wiki-accent)`): 卡片、返回按钮、tag 的 hover 态；配合 `translateY(-2~4px)`。
- **轻托** (`box-shadow: 6px 6px 0 rgba(26, 26, 46, 0.08)`): 详情页分节面板的 hover，几乎不可见，只暗示"这一节可以聚焦"。
- **重印** (`box-shadow: 8px 8px 0 var(--wiki-ink)`): 首页的少量强对比元素（大幅卡片/入口）。
- **氛围浮起** (`0 26px 60px -20px rgba(11,11,16,.6)`): 仅首页悬停预览图使用，属于另一个世界的例外。

### Named Rules

**The Registration Offset Rule.** 阴影永远是硬边偏移、零模糊，且与位移方向一致（右下 5px 配向左上 -2px 位移）。柔和阴影会让图鉴质感塌回普通网页。

**The Flat At Rest Rule.** 静止状态不加阴影。阴影是状态（hover / focus）的回应，不是元素的默认装饰。

## Shapes

- **零圆角**：`border-radius` 在整个系统里只出现在头像（`50%`）。其余所有矩形都是直角——需要"切角"时用 `clip-path`。
- **切角语法**：`polygon(8px 0, 100% 0, 100% calc(100% - 8px), calc(100% - 8px) 100%, 0 100%, 0 8px)` 是最常见的 8px 对角切；卡片角标用 6px，输入框用 15px，首页大字块用 10px。
- **单角切**：标签角标 `polygon(6px 0, 100% 0, 100% 100%, 0 100%)` 只切左上；分节标题前的装饰三角 `polygon(0 0, 100% 0, 0 100%)` 也是形状语言的一部分。
- **描线**：1.5px 是标准线宽（`--wiki-line`）；2px 只用于焦点环（`outline: 2px solid accent; outline-offset: 3px`）。
- **网格底纹**：深色页头用 56px、几何主题用 60px 的 `linear-gradient` 方格，配合径向遮罩淡出——这是"图纸"感的来源。

## Components

### Buttons
- **Shape:** 直角 + 8px 切角，1.5px 描线边框，无圆角。
- **Primary（返回 / 动作）:** 白底 + 墨蓝文字 + 描线；hover 时描边转朱砂印泥、出现 5px 硬阴影、整体左移 2px、箭头左移 3px。
- **Ghost（链接型）:** 无底色，Space Grotesk 0.85rem，`letter-spacing: 0.08em`；hover 只换颜色或加下划线。
- **Hover / Focus:** 过渡 `0.35s var(--wiki-ease)`；键盘焦点用 2px 朱红外环 + 3px 偏移，从不依赖颜色以外的唯一提示。

### Chips
- **Style:** `--paper-2` 底、1.5px 描线、6px 切角、`Space Grotesk 0.82rem / 500`。
- **分类 chip（链接）:** 描边为半透明深蓝，hover 填充深蓝白字并上浮 2px + 5px 朱红硬阴影。
- **风格 chip（非链接）:** 弱文字色 + 虚线描边 + `cursor: default`——虚线是"这不可点"的唯一信号。

### Cards
- **Corner Style:** 直角（0px）。
- **Background:** 白纸；封面区用 `--paper-2` → `#eceef5` 的斜向渐变兜底缺图。
- **Shadow Strategy:** 静止无阴影；hover 上浮 4px + 朱砂印泥描边 + 8px 朱红硬阴影（卡片 hover、详情大块与弹层共用同一个 8px 上限）。
- **Border:** 1.5px 描线。
- **Internal Padding:** `1rem 1.1rem 1.1rem`；封面 `aspect-ratio: 1`。
- **Badge:** 右下角朱砂印泥角标，只切左上角，内容为数量（"228 张"）。

### Inputs / Fields
- **Style:** 白底 94% 不透明、1px 半透明白描边（在深色页头上）、15px 对角切、`Space Grotesk 1rem`。
- **Focus:** `box-shadow: 0 0 0 2px var(--wiki-accent) inset`，不改变布局。
- **Empty / Error:** 只用文字说明（弱文字色、斜体），不加红色边框。

### Navigation
- **Style:** 固定顶栏，白底，底部 3px 墨蓝实线（几何主题）或 1.5px 描线（内容页）。
- **Typography:** 左侧 TONGRENLU 用 Bebas 1.8rem / 3px 字距，下方 30×3px 朱红短线；链接 Space Grotesk 0.85rem / 大写 / 1px 字距。
- **States:** 默认弱文字色；hover 与当前页转墨蓝/朱红下划线。
- **Mobile:** 900px 以下链接间距 2rem → 1.2rem，内边距 `0.8rem 1.5rem`；首页切换为全屏菜单。

### Section Panel（签名组件）
- 详情页每节一块白纸面板：1.5px 描线、`clamp(1.5rem,3vw,2.4rem)` 内边距、节间 `--ws-l`。
- 标题行：Bebas 编号（朱红）+ 中文标题 + 底部描线；正文导语（`.geo-wiki-lead`）用 `--paper-2` 底 + 4px 朱砂印泥左边条。

### Index Row（首页签名组件）
- 4 列网格：编号 / 名称 / 计数 / 箭头，行间 1px 半透明墨线。
- hover 时整行反色（墨底纸字），右侧预览图浮起——这是首页世界唯一允许的"氛围阴影"用法。

## Do's and Don'ts

### Do:
- **Do** 从 `assets/css/wiki.css` 读取白纸令牌（`--wiki-*` / `--ws-*`），并把它当作唯一权威；`wiki.html`、`album.html` 里的内联 `:root` 是历史副本，新页面不要再复制一份。首页令牌以 `assets/css/home-archive.css` 为准，改首页时不要引入 `--wiki-*`。
- **Do** 用 clip-path 表达切角，并把同一元素的所有角切成一致尺寸（8px 常用、6px 用于小角标、15px 用于输入框）。
- **Do** 让朱砂印泥承担"当前状态 + 可点 + 重点"三件事之一，一屏内通常只出现 2–4 处。
- **Do** 强调色承字时用 `#c22c47`（`--wiki-accent-ink`）；`#e94560` 只出现在描边、阴影、图形与深色页头上。
- **Do** 每个页面都保留 `<main id="main">` 与 `.skip-link`，键盘用户第一次 Tab 就能跳过固定导航。
- **Do** 在深色页头里保留网格底纹与径向辉光，正文区保持纯净白纸——对比来自区块本身，不来自正文装饰。
- **Do** 长文行高保持 2.0、字重 300；换行宽度控制在 34em 左右（页头副题当前的 `max-width`）。
- **Do** 分节编号用 JS 按可见分节重排，隐藏任何一节后不出现 01 → 03。

### Don't:
- **Don't** 使用 `border-radius` 做卡片或按钮圆角（头像除外）；圆角会直接摧毁图鉴的印刷感。
- **Don't** 写柔和模糊阴影（`0 4px 24px rgba(...)`）；阴影只能是 3–8px 的硬偏移。
- **Don't** 把 Bebas Neue 用在完整句子或正文里，也不要给中文正文加 Bebas 回退。
- **Don't** 把首页的奶油纸 / 墨黑令牌带进内容页，反之亦然（Two Worlds Rule）。
- **Don't** 用大面积朱砂印泥做背景或装饰条；一旦超过约 10% 屏占比，强调失效。
- **Don't** 用纯黑 `#000` 或纯灰 `#888`——墨与灰都要带蓝调（`#1a1a2e` / `#8a90a6`）。
- **Don't** 引入新的强调色（绿、紫、青）；系统只有一支朱红，分类靠深蓝和色调分层解决。
- **Don't** 为了视觉效果引入前端框架或构建链——视觉规则必须能在静态 CSS 里落地。
