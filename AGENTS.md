# 项目上下文文档 (AGENTS.md)

## 项目概述

**同人录 (Tongrenlu)** 是一个专注于同人创作内容分享和管理的平台，主要服务于同人音乐、文章等创作爱好者。该平台提供了音乐专辑管理、文章发布、标签分类、展会信息等功能，帮助创作者和用户更好地管理和发现优质内容。

### 核心功能
- **音乐管理**: 音乐专辑展示、曲目播放、艺术家信息管理
- **文章管理**: 文章发布、浏览、标签分类、评论系统
- **标签系统**: 多层级内容分类和检索
- **用户系统**: 用户注册、登录、收藏管理
- **艺术家展示**: 视觉化艺术家名称展示，支持搜索过滤
- **展会模块**: 东方同人展会收录，包括 COMIC MARKET、例大祭、红楼梦等

## 技术栈

### 后端技术
- **框架**: Spring Boot 3.4.3
- **Java 版本**: 21 (推荐开发使用 Java 23)
- **数据库**: MySQL 8.2.0
- **ORM**: MyBatis Plus 3.5.11
- **构建工具**: Maven 3.6+
- **编码**: UTF-8
- **连接池**: HikariCP (最大连接数 20)

### 前端技术
- **基础框架**: 原生 HTML/CSS/JavaScript
- **UI 主题**: Geometric Futurism (几何未来主义)
- **字体**: Google Fonts (Bebas Neue, Space Grotesk, Outfit, Noto Sans SC)
- **工具库**: jQuery 3.7.1, FontAwesome 6.5.1
- **组件化**: 模块化组件设计

### 工具库
- **Lombok**: 减少样板代码
- **Hutool**: Java 工具类库 5.8.40
- **Guava**: Google 工具库 33.3.1-jre
- **Apache Commons**: 通用工具库 (commons-io 2.18.0, commons-lang3 3.18.0, commons-collections4 4.4)

## 项目结构

### Maven 多模块架构

```
tongrenlu/
├── tongrenlu-web/          # 公开 Web 应用层 (Spring Boot Web)
│   └── info.tongrenlu/
│       ├── www/                    # 公开 API 控制器层
│       ├── manager/                # 业务逻辑层 (当前为空)
│       ├── domain/                 # 数据传输对象 (DTO/VO)
│       ├── config/                 # 配置类
│       ├── constants/              # 常量定义
│       ├── enums/                  # 枚举类
│       └── exception/              # 异常处理
├── tongrenlu-dao/          # 数据访问层
│   └── info.tongrenlu/
│       ├── domain/                 # 实体类 (Entity)
│       ├── mapper/                 # MyBatis 映射器
│       ├── model/                  # 数据模型 (CloudMusic 集成)
│       ├── service/                # 服务层
│       └── support/                # 支持类
└── tongrenlu-tool/         # 管理后台 + 数据工具模块 (Spring Boot Web)
    └── info.tongrenlu/
        ├── AdminArtistController.java    # 艺人管理 API
        ├── AdminUnpublishController.java # 未发布专辑管理 API
        ├── MusicAlbumParseJob.java       # 专辑解析 HTTP 端点
        ├── MusicArtistParseJob.java      # 艺人解析 HTTP 端点
        ├── PlaylistImportJob.java        # 歌单导入 HTTP 端点
        └── support/                      # 解析器工具类
```

### 模块职责划分

| 模块 | 职责 | 访问权限 |
|------|------|----------|
| `tongrenlu-web` | 公开访问的 Web 应用，提供音乐库、播放器、展会等公开功能 | 公开 |
| `tongrenlu-tool` | 管理后台 + 数据导入工具，提供艺人管理、专辑发布、数据解析等功能 | 内部/管理员 |
| `tongrenlu-dao` | 数据访问层，提供实体类、Mapper、Service 等数据服务 | 共享 |

### 目录组织
- **SQL 文件**: 按日期组织 `/sql/YYYYMMDD/`
- **公开静态资源**: `/tongrenlu-web/src/main/resources/static/`
  - `components/`: 可复用组件
    - `artist-showcase/`: 艺术家展示组件
    - `event-list/`: 展会列表组件
    - `home/`: 首页组件 (画廊Hero、最新专辑、热门推荐、艺术家推荐)
    - `music-library/`: 音乐库组件
    - `player/`: 播放器组件
    - `shared/`: 共享组件 (图片加载器)
  - `assets/`: 静态资源文件
    - `css/`: 样式文件 (geometric-theme.css 共享主题)
- **管理后台静态资源**: `/tongrenlu-tool/src/main/resources/static/`
  - `artist.html`: 艺人管理页面
  - `unpublish.html`: 未发布专辑管理页面
  - `components/admin/`: 管理后台组件
  - `components/shared/`: 共享组件
  - `assets/css/`: 样式文件
- **文档**: `/docs/` - PRD、架构设计、API 规范等
- **OpenSpec**: `/openspec/` - 规格驱动开发变更记录

### 静态页面

**公开页面 (tongrenlu-web)**
- `index.html` - 首页 (动态专辑封面画廊背景 + 统计数据)
- `album.html` - 音乐库页面 (专辑列表、搜索、筛选)
- `artist-showcase.html` - 艺术家展示页 (粒子特效背景)
- `event.html` - 展会列表页 (公开)
- `player.html` - 全屏音乐播放器

**管理后台页面 (tongrenlu-tool)**
- `artist.html` - 艺人管理页 (搜索网易云艺人、导入专辑)
- `unpublish.html` - 未发布专辑管理 (匹配网易云专辑并发布)

## 构建和运行

### 前置要求
- JDK 21 或更高版本
- Maven 3.6+
- MySQL 8.2+

### 构建命令
```bash
# 清理并编译所有模块
mvn clean compile

# 打包所有模块
mvn clean package

# 跳过测试打包
mvn clean package -DskipTests

# 仅编译特定模块
mvn clean compile -pl tongrenlu-web

# 安装到本地仓库
mvn clean install
```

### 运行应用
```bash
# 运行 Web 应用
cd tongrenlu-web
mvn spring-boot:run

# 或直接运行打包后的 JAR
java -jar tongrenlu-web/target/tongrenlu-web.jar
```

### 应用配置
- **服务端口**: 8443
- **上下文路径**: `/tongrenlu`
- **数据库**: MySQL (localhost:3306/tongrenlu)

### 环境变量
- `DB_HOST`: 数据库主机地址 (默认: localhost)
- `DB_PASSWORD`: 数据库密码 (必需)

### 数据库初始化
```sql
-- 按日期顺序执行 SQL 脚本
sql/20251124/m_article_1.sql
sql/20251124/m_tag_1.sql
sql/20251124/m_track_1.sql
sql/20251124/r_article_tag_1.sql
sql/20251128/m_artist.sql
```

## 开发规范

### 命名规范
- **包名**: `info.tongrenlu`
- **GroupId**: `top.tonrenlu`
- **表名前缀**:
  - `m_` - 主表 (如: m_article, m_artist)
  - `r_` - 关系表 (如: r_article_tag)
  - `v_` - 视图 (如: v_music_stat)
- **软删除字段**: `del_flg` (0=正常, 1=删除)
- **时间戳字段**: `upd_date` (更新时间)

### 代码风格
- **UTF-8 编码**: 全项目统一使用 UTF-8
- **Lombok**: 广泛使用 @Data, @Builder, @Slf4j 等注解
- **组件化**: 前端组件文件 < 200 行，优先复用
- **分层架构**: Controller → Service → DAO

### 数据访问规范
- **软删除**: 所有实体使用 `del_flg` 字段
- **乐观锁**: 推荐使用版本号控制并发
- **分页查询**: 使用 MyBatis Plus 分页插件
- **SQL 日志**: 开发环境开启 SQL 日志输出
- **懒加载**: 启用 MyBatis 懒加载优化

### Git 工作流
- **主分支**: `master`
- **提交消息**: 使用约定式提交 (Conventional Commits)
- **代码审查**: 所有代码变更需经过审查

### 测试策略
- **单元测试**: Service 层
- **集成测试**: Controller 层
- **测试数据库**: H2 内存数据库
- **TODO**: 当前项目缺少完整的测试覆盖

## 核心功能模块

### 音乐管理模块
- 专辑列表展示 (支持搜索建议、动态筛选、排序)
- 曲目详情和播放
- 艺术家信息管理
- CloudMusic 网易云音乐集成

### 首页模块
- **Hero 区域**: 动态专辑封面画廊背景，6x4 网格轮播展示
  - 点击封面跳转到播放器
  - 鼠标悬浮时暂停轮换
  - 渐变遮罩保证内容可读性
- **统计数据**: 显示专辑/艺术家/展会数量
- **快速入口**: 进入音乐库按钮

### 全屏播放器模块
- 左侧专辑封面展示 (500x500)
- 右侧播放列表 (简化显示曲目编号和名称)
- 底部播放控制栏 (播放/暂停、上一首/下一首、进度条、音量)
- 支持歌词显示、随机播放、循环播放
- 全屏模式支持

### 艺术家展示模块
- 视觉化展示大量艺术家名称
- 粒子/星空特效背景
- 搜索过滤艺术家
- 点击跳转到艺术家专辑列表
- 响应式布局

### 展会模块
- 东方同人展会收录 (COMIC MARKET、例大祭、红楼梦等)
- 展会列表展示 (支持搜索、筛选、排序)
- 展会详情弹窗查看专辑列表
- 展会统计数据展示
- 基于 tag 表中 type='event' 的数据

### 文章管理模块
- 文章发布和编辑
- 文章浏览和搜索
- 标签分类系统
- 访问统计

### 管理后台模块 (tongrenlu-tool)

**艺人管理功能**
- 搜索网易云音乐艺人
- 分页查询本地艺人列表 (含专辑数量统计)
- 获取艺人专辑列表 (标记已导入状态)
- 保存专辑到数据库
- 删除艺人及其关联数据

**未发布专辑管理**
- 获取未发布专辑列表 (publishFlg=0 或 2)
- 搜索网易云音乐匹配专辑
- 更新专辑信息并发布
- 标记专辑为无匹配状态

**数据导入 HTTP 端点**
- `GET /album/import` - 专辑解析导入
- `GET /artist/import` - 艺人信息解析
- `GET /playlist/import?playlistId=xxx` - 网易云歌单批量导入

## UI 主题设计

### Geometric Futurism (几何未来主义)

项目使用统一的几何未来主义主题，主要特点：

- **色彩方案**:
  - 主色: `#1a1a2e` (深蓝黑)
  - 强调色: `#e94560` (玫红)
  - 背景: `#ffffff` / `#f8f9fa`
  - 边框: `#e0e0e5`

- **字体**:
  - 标题: Bebas Neue
  - 正文: Space Grotesk, Noto Sans SC
  - 辅助: Outfit

- **设计元素**:
  - 几何网格背景
  - 切角按钮和卡片 (clip-path)
  - 阴影偏移动画效果
  - 响应式布局

- **共享样式**: `assets/css/geometric-theme.css`

## 图片优化

### 网易云音乐图片参数

网易云音乐图片支持 URL 参数控制尺寸，格式为 `?param=宽y高`：

```javascript
// 图片加载器 (components/shared/image-loader.js)
function getOptimizedImageUrl(url, width, height) {
    if (url.includes('music.126.net') || url.includes('127.net')) {
        const baseUrl = url.split('?')[0];
        return `${baseUrl}?param=${width}y${height}`;
    }
    return url;
}
```

### 各页面图片尺寸
| 页面/组件 | 尺寸 |
|-----------|------|
| 首页画廊背景 | 300x300 |
| 音乐库封面 | 300x300 |
| 播放器封面 | 500x500 |

## 外部集成

### CloudMusic (网易云音乐)
- **目的**: 获取音乐元数据、封面图、艺术家信息
- **模型类**: `CloudMusicAlbum`, `CloudMusicTrack`, `CloudMusicArtist`, `CloudMusicPlaylistTrack`, `CloudMusicPlaylistResponse`
- **API 位置**: `info.tongrenlu.model`
- **集成功能**:
  - 专辑详情获取
  - 艺术家信息获取
  - 歌单歌曲列表获取 (分页)
  - 搜索功能

### 文件系统
- **存储位置**: 本地文件系统
- **音乐文件**: 支持标准音乐文件格式
- **艺术家目录**: 使用 `+` 前缀标识

## API 端点

### 公开 API (tongrenlu-web)
| 端点 | 方法 | 描述 |
|------|------|------|
| `/api/music/search` | GET | 搜索音乐 (支持分页、关键词、排序) |
| `/api/music/detail` | GET | 获取专辑详情 |
| `/api/music/track` | GET | 获取曲目播放地址 |
| `/api/music/random-albums` | GET | 获取随机专辑列表 |
| `/api/music/album-stats` | GET | 获取专辑统计数据 |
| `/api/music/tags` | GET | 获取标签列表 |
| `/api/music/artists` | GET | 获取艺术家列表 (支持搜索) |
| `/api/events` | GET | 获取展会列表 (支持分页、搜索、排序) |
| `/api/events/{id}` | GET | 获取展会详情 |
| `/api/events/{id}/albums` | GET | 获取展会专辑列表 |
| `/api/events/stats` | GET | 获取展会统计数据 |
| `/api/events/count` | GET | 获取展会总数 |

### 管理 API (tongrenlu-tool)
| 端点 | 方法 | 描述 |
|------|------|------|
| `/api/artist/search` | GET | 搜索网易云音乐艺人 |
| `/api/artist/list` | GET | 分页查询本地艺人列表 |
| `/api/artist/albums` | POST | 获取艺人专辑列表 |
| `/api/artist/save-album` | POST | 保存专辑到数据库 |
| `/api/artist/delete/{id}` | DELETE | 删除艺人及其关联数据 |
| `/api/admin/unpublished-list` | GET | 获取未发布专辑列表 |
| `/api/admin/search-cloud-music` | GET | 搜索网易云音乐专辑 |
| `/api/admin/update-album` | POST | 更新专辑信息并发布 |
| `/api/admin/mark-no-match` | POST | 标记专辑为无匹配状态 |
| `/album/import` | GET | 专辑解析导入 |
| `/artist/import` | GET | 艺人信息解析 |
| `/playlist/import` | GET | 歌单批量导入 |

## OpenSpec 变更记录

### 已完成变更 (Archive)
| 日期 | 变更名称 | 描述 |
|------|----------|------|
| 2026-03-06 | redesign-homepage | 首页重新设计，Hero画廊 + 最新专辑轮播 |
| 2026-03-08 | playlist-album-import | 网易云歌单批量导入功能 |
| 2026-03-10 | optimize-music-library-ux | 音乐库UX优化 (搜索建议、动态筛选、排序) |
| 2026-03-11 | artist-page | 艺术家展示页面 (粒子特效背景) |
| 2026-03-13 | geometric-futurism-theme | Geometric Futurism 主题重构全站页面 |
| 2026-03-19 | admin-migration | 管理后台迁移至 tool 模块，批处理改为 HTTP 端点 |

## 重要约束

### 安全要求
- **数据库密码**: 使用环境变量 `DB_PASSWORD`，禁止硬编码
- **SQL 注入防护**: 使用 MyBatis 参数化查询
- **XSS 防护**: 输出内容需转义
- **CSRF 防护**: 表单提交需验证令牌

### 性能要求
- 页面加载时间 < 3 秒
- 支持 1000+ 并发用户
- 数据库查询响应时间 < 100ms
- HikariCP 连接池最大连接数 20

### 兼容性要求
- MySQL 8.2+ 必需
- Java 21 正式支持，Java 23 推荐开发使用
- 现代浏览器支持 (Chrome, Firefox, Safari, Edge)

## 已知技术债务

- [ ] 硬编码数据库密码需迁移到环境变量 (已部分完成)
- [ ] 缺少完整的单元测试覆盖
- [ ] 日志系统需要完善
- [ ] 缺少 API 文档自动生成
- [ ] 需要引入 Spring Security 完善安全机制
- [ ] 缺少 API 网关层
- [ ] 需要数据库读写分离
- [ ] 需要引入消息队列异步处理

## 扩展性规划

### 未来功能
- [ ] 高级搜索功能
- [ ] 个性化推荐
- [ ] 创作者中心
- [ ] 内容审核系统
- [ ] 手机 App 开发
- [ ] 社交功能增强
- [ ] 付费内容支持
- [ ] 多语言支持

### 微服务拆分
当前为单体架构，未来可拆分为：
- user-service (用户服务)
- content-service (内容服务)
- search-service (搜索服务)
- file-service (文件服务)
- stats-service (统计服务)

## 常见问题

### 数据库连接失败
检查 MySQL 服务是否启动，配置是否正确：
```properties
spring.datasource.url=jdbc:mysql://localhost:3306/tongrenlu
spring.datasource.username=your_username
spring.datasource.password=${DB_PASSWORD}
```

### 端口冲突
修改 `server.port` 配置：
```properties
server.port=8080
```

### Maven 依赖下载慢
配置国内镜像源：
```xml
<mirror>
  <id>aliyun</id>
  <mirrorOf>central</mirrorOf>
  <url>https://maven.aliyun.com/repository/public</url>
</mirror>
```

## 联系和支持

- **项目仓库**: https://github.com/tongrenlu114514/new_tongrenlu.git
- **文档位置**: `/docs/` 目录
- **OpenSpec**: `/openspec/` 目录 (规格驱动开发)

---

**文档版本**: v1.4
**创建日期**: 2026-03-04
**最后更新**: 2026-03-19