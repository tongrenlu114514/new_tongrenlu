# 同人录项目 (Tongrenlu) - 项目指南

## 项目概述

同人录是一个基于Spring Boot的多模块Java应用程序，专注于同人创作内容的分享和管理。项目包含Web应用和工具模块，支持文章、音乐、标签、评论等核心功能。

**技术栈:**
- **后端框架:** Spring Boot 3.4.3
- **数据库:** MySQL 8.2.0
- **ORM框架:** MyBatis Plus 3.5.10.1
- **构建工具:** Maven
- **Java版本:** 23
- **API文档:** Springdoc OpenAPI UI 1.6.9

## 项目结构

```
tongrenlu/                          # 父项目根目录
├── tongrenlu-web/                  # Web应用模块
│   └── src/main/java/info/tongrenlu/
│       ├── domain/                 # 数据模型类
│       ├── mapper/                 # MyBatis数据访问层
│       ├── service/                # 业务逻辑层
│       ├── manager/                # 业务管理类
│       ├── www/                    # Web控制器
│       ├── constants/              # 常量定义
│       ├── exception/              # 异常处理
│       └── support/                # 工具类
├── tongrenlu-tool/                 # 工具模块
│   └── src/main/java/info/tongrenlu/support/
│       ├── LSFileParser.java       # 文件列表解析器
│       └── MusicAlbumParser.java   # 音乐专辑解析器
└── sql/                            # 数据库脚本和视图
    ├── M_*.sql                     # 主表定义
    ├── R_*.sql                     # 关系表定义
    └── V_*.sql                     # 视图定义
```

## 模块说明

### 1. tongrenlu-web (Web应用模块)
Spring Boot Web应用，提供完整的RESTful API服务，包含：
- 用户认证和授权
- 文章和音乐内容管理
- 评论和点赞系统
- 标签分类管理
- 数据统计和分析

### 2. tongrenlu-tool (工具模块)
独立工具模块，提供数据处理和转换功能：
- **LSFileParser**: 解析文件目录结构并导出为CSV格式
- **MusicAlbumParser**: 解析音乐专辑信息并生成结构化数据

## 核心功能模块

### 1. 文章管理 (Article)
- 文章发布和编辑
- 访问统计和点赞功能
- 评论系统

### 2. 音乐管理 (Music)
- 音乐内容管理
- 热门音乐排行
- 用户收藏功能
- 专辑信息解析和导入

### 3. 标签系统 (Tag)
- 内容标签分类
- 标签关联管理

### 4. 用户系统 (User)
- 用户信息管理
- 用户设备管理
- 个人收藏库
- 权限控制

### 5. 工具功能 (Tool)
- 文件结构解析
- 音乐专辑数据提取
- 批量数据处理

## 数据库设计

### 主要数据表
- `m_article` - 文章主表
- `m_user` - 用户表
- `m_tag` - 标签表
- `m_comment` - 评论表
- `m_track` - 音乐曲目表
- `m_file` - 文件管理表
- `m_shop` - 商店表
- `m_order` - 订单表
- `r_like` - 点赞关系表
- `r_music` - 音乐关联表

### 核心视图
- `v_music` - 音乐信息综合视图
- `v_article_comment_count` - 文章评论统计视图
- `v_music_like_count` - 音乐点赞统计视图
- `v_user_follow_count` - 用户关注统计视图

## 构建和运行

### 环境要求
- Java 23
- MySQL 8.2+
- Maven 3.6+

### 构建项目
```bash
# 编译整个项目
mvn clean compile

# 编译web模块
cd tongrenlu-web
mvn clean compile

# 编译工具模块
cd tongrenlu-tool  
mvn clean compile

# 运行测试
mvn test

# 打包应用
mvn clean package
```

### 运行应用
```bash
# 运行web应用
cd tongrenlu-web
mvn spring-boot:run

# 或运行打包后的jar文件
java -jar target/tongrenlu-web.jar

# 运行工具模块
cd tongrenlu-tool
java -cp target/tongrenlu-tool-1.0-SNAPSHOT.jar info.tongrenlu.support.LSFileParser
java -cp target/tongrenlu-tool-1.0-SNAPSHOT.jar info.tongrenlu.support.MusicAlbumParser
```

### 应用配置
应用运行在端口8443，配置文件位于 `tongrenlu-web/src/main/resources/application.properties`

**关键配置项:**
- 数据库连接配置 (MySQL 8.2)
- HTTP客户端配置 (Apache HttpClient 5)
- MyBatis配置
- AI服务配置 (DeepSeek API)
- 文件上传和存储配置

## 开发规范

### 代码风格
- 使用Lombok简化代码
- 遵循MyBatis Plus的命名规范
- 使用Jackson进行JSON序列化
- 遵守Spring Boot最佳实践

### 数据库约定
- 主表前缀: `m_`
- 关系表前缀: `r_`
- 视图前缀: `v_`
- 使用软删除机制 (`del_flg`字段)
- 统一时间戳管理 (`upd_date`字段)

### API设计
- RESTful风格接口
- 统一的异常处理
- OpenAPI文档支持
- 分页和排序支持

## 工具模块使用

### LSFileParser
解析文件目录结构并导出为CSV：
```java
LSFileParser.parseAndExport("input.txt", "output.csv");
```

### MusicAlbumParser
解析音乐专辑信息并生成结构化数据：
```java
MusicAlbumParser.parseAndExport("album_list.txt", "music_album.csv");
```

## 部署说明

1. 确保MySQL数据库已创建并导入SQL脚本
2. 配置数据库连接参数
3. 构建并运行应用
4. 访问API文档: http://localhost:8443/swagger-ui.html

## 注意事项

- 项目使用Spring AI集成，需要配置AI服务API密钥
- 数据库密码等敏感信息应使用环境变量或配置中心管理
- 生产环境建议使用HTTPS和适当的网络安全配置
- 工具模块可独立运行，用于数据处理和批量操作
- 多模块结构便于功能分离和独立部署