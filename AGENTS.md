
# 项目上下文文档 (AGENTS.md)

## 项目概述

**同人录 (Tongrenlu)** 是一个专注于同人创作内容分享和管理的平台，主要服务于同人音乐、文章等创作爱好者。该平台提供了音乐专辑管理、文章发布、标签分类等功能，帮助创作者和用户更好地管理和发现优质内容。

### 核心功能
- **音乐管理**: 音乐专辑展示、曲目播放、艺术家信息管理
- **文章管理**: 文章发布、浏览、标签分类、评论系统
- **标签系统**: 多层级内容分类和检索
- **用户系统**: 用户注册、登录、收藏管理

## 技术栈

### 后端技术
- **框架**: Spring Boot 3.4.3
- **Java 版本**: 21 (推荐开发使用 Java 23)
- **数据库**: MySQL 8.2.0
- **ORM**: MyBatis Plus 3.5.11
- **构建工具**: Maven 3.6+
- **编码**: UTF-8

### 前端技术
- **基础框架**: 原生 HTML/CSS/JavaScript
- **UI 库**: Bootstrap CSS + Material Design 原则
- **工具库**: jQuery 3.7.1, FontAwesome 6.5.1
- **组件化**: 模块化组件设计

### 工具库
- **Lombok**: 减少样板代码
- **Hutool**: Java 工具类库 5.8.40
- **Guava**: Google 工具库 33.3.1
- **Apache Commons**: 通用工具库

## 项目结构

### Maven 多模块架构

```
tongrenlu/
├── tongrenlu-web/          # Web 应用层 (Spring Boot Web)
│   ├── info.tongrenlu.www/        # 控制器层 (Controller)
│   ├── info.tongrenlu.manager/    # 业务逻辑层 (Service/Manager)
│   ├── info.tongrenlu.domain/     # 数据传输对象 (DTO/VO)
│   └── info.tongrenlu.support/    # 支撑工具类
├── tongrenlu-dao/          # 数据访问层
│   ├── info.tongrenlu.domain/     # 实体类 (Entity)
│   ├── info.tongrenlu.mapper/     # MyBatis 映射器
│   └── info.tongrenlu.model/      # 数据模型 (CloudMusic 集成)
└── tongrenlu-tool/         # 工具模块
    └── info.tongrenlu.support/    # 批处理工具类 (数据解析)
```

### 目录组织
- **SQL 文件**: 按日期组织 `/sql/YYYYMMDD/`
- **静态资源**: `/tongrenlu-web/src/main/resources/static/`
  - `admin/`: 管理后台页面
  - `components/`: 可复用组件
  - `assets/`: 静态资源文件
- **文档**: `/docs/` - PRD、架构设计、API 规范等

## 构建和运行

### 前置要求
- JDK 21 或更高版本
- Maven 3.6+
- MySQL 8.2+
- Node.js (如需前端构建工具)

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
- **Swagger UI**: http://localhost:8443/tongrenlu/swagger-ui.html
- **数据库**: MySQL (localhost:3306/tongrenlu)

### 环境变量
- `DB_HOST`: 数据库主机地址 (默认: localhost)

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
- **分层架构**: Controller → Manager → Service → DAO

### 数据访问规范
- **软删除**: 所有实体使用 `del_flg` 字段
- **乐观锁**: 推荐使用版本号控制并发
- **分页查询**: 使用 MyBatis Plus 分页插件
- **SQL 日志**: 开发环境开启 SQL 日志输出

### Git 工作流
- **主分支**: `master`
- **提交消息**: 使用约定式提交 (Conventional Commits)
- **代码审查**: 所有代码变更需经过审查

### 测试策略
- **单元测试**: Service 和 Manager 层
- **集成测试**: Controller 层
- **测试数据库**: H2 内存数据库
- **TODO**: 当前项目缺少完整的测试覆盖

## 核心功能模块

### 音乐管理模块
- 专辑列表展示
- 曲目详情和播放
- 艺术家信息管理
- CloudMusic 网易云音乐集成

### 文章管理模块
- 文章发布和编辑
- 文章浏览和搜索
- 标签分类系统
- 访问统计

### 数据解析工具 (tongrenlu-tool)
- 音乐专辑解析 (`MusicAlbumParser`)
- 艺术家信息解析 (`MusicArtistParser`)
- 批处理任务支持

## 外部集成

### CloudMusic (网易云音乐)
- **目的**: 获取音乐元数据、封面图、艺术家信息
- **模型类**: `CloudMusicAlbum`, `CloudMusicTrack`, `CloudMusicArtist`
- **API 位置**: `info.tongrenlu.model`

### 文件系统
- **存储位置**: 本地文件系统
- **音乐文件**: 支持标准音乐文件格式
- **艺术家目录**: 使用 `+` 前缀标识

## 重要约束

### 安全要求
- **⚠️ 数据库密码**: 不得将硬编码密码提交到版本控制
- **SQL 注入防护**: 使用 MyBatis 参数化查询
- **XSS 防护**: 输出内容需转义
- **CSRF 防护**: 表单提交需验证令牌

### 性能要求
- 页面加载时间 < 3 秒
- 支持 1000+ 并发用户
- 数据库查询响应时间 < 100ms

### 兼容性要求
- MySQL 8.2+ 必需
- Java 21 正式支持，Java 23 推荐开发使用
- 现代浏览器支持 (Chrome, Firefox, Safari, Edge)

## 已知技术债务

- [ ] 硬编码数据库密码需迁移到环境变量
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
spring.datasource.password=your_password
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

**文档版本**: v1.0
**创建日期**: 2026-03-04
**最后更新**: 2026-03-04
