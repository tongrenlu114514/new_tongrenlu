# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 技术栈与架构

这是一个基于 Spring Boot 的多模块 Maven 项目，专注于同人创作内容（音乐、文章）的分享和管理。

**技术栈:**
- **后端框架**: Spring Boot 3.4.3
- **数据库**: MySQL 8.2.0
- **ORM框架**: MyBatis Plus 3.5.11
- **Java版本**: 21/23
- **构建工具**: Maven 3.6+
- **Spring Boot端口**: 8443 (context-path: `/tongrenlu`)

**三模块结构:**
1. **tongrenlu-web** - Web应用模块 (Spring Boot Web)
2. **tongrenlu-tool** - 工具模块 (数据解析与处理)
3. **tongrenlu-dao** - 数据访问层 (MyBatis域模型和映射器)

## 代码组织结构

### tongrenlu-web (Web模块)
Package: `info.tongrenlu`
- `config/` - 配置类
- `constants/` - 常量定义
- `domain/` - 数据传输对象(DTO/VO)
- `enums/` - 枚举类
- `exception/` - 异常处理
- `manager/` - 业务管理器层
- `www/` - 控制器层 (RESTful API)
  - `AdminArtistController.java` - 艺术家管理API
  - `AdminUnpublishController.java` - 取消发布管理API
  - `ApiMusicController.java` - 音乐相关API

### tongrenlu-dao (数据访问层)
Package: `info.tongrenlu`
- `domain/` - 实体类 (ArticleBean, ArtistBean, TagBean, TrackBean 等)
- `mapper/` - MyBatis映射器 (ArticleMapper, ArtistMapper, TagMapper, TrackMapper 等)

## 数据库设计

SQL文件位于 `/sql/` 目录，按日期分版本管理:
- `sql/20251124/` - 主表数据
- `sql/20251128/` - 扩展表数据

**核心表**:
- `m_article` - 文章主表 (ID 2997-3009 及更多)
- `m_track` - 音乐曲目表
- `m_artist` - 艺术家表
- `m_tag` - 标签表
- `r_article_tag` - 文章标签关系表

**表名前缀约定**:
- `m_` - 主表 (main tables)
- `r_` - 关系表 (relationship tables)
- `v_` - 视图 (views)

**公共字段**:
- `upd_date` - 更新时间戳
- `del_flg` - 软删除标记 (`0=未删除, 1=已删除`)

## 常用开发命令

### 构建项目
```bash
# 编译整个项目
mvn clean compile

# 编译特定模块
cd tongrenlu-web && mvn clean compile
cd tongrenlu-tool && mvn clean compile

# 运行测试
mvn test

# 打包应用
mvn clean package
```

### 运行应用
```bash
# 运行Web应用 (开发模式)
cd tongrenlu-web
mvn spring-boot:run

# 运行打包后的jar
java -jar tongrenlu-web/target/tongrenlu-web.jar

# 运行工具模块 (需要先指定输入文件参数)
cd tongrenlu-tool
# 具体运行方式需查看工具类main方法
```

### 数据库配置
配置文件: `tongrenlu-web/src/main/resources/application.properties`

**关键配置**:
```properties
server.port=8443
server.servlet.context-path=/tongrenlu
spring.datasource.url=jdbc:mysql://localhost:3306/tongrenlu
spring.datasource.username=你的用户名
spring.datasource.password=你的密码
```

## 工具模块 (tongrenlu-tools)

### 音乐专辑解析器 (MusicAlbumParser)
位置: `tongrenlu-tool/src/main/java/info/tongrenlu/support/MusicAlbumParser.java`

解析文件列表并导入音乐数据，支持解析:
- 艺术家目录 (以 `+` 开头)
- 专辑信息 (第二层目录)
- 唱片盘符 (第三层目录)
- MP3文件 (以 `-` 开头并以 `.mp3` 结尾)

### 音乐艺术家解析器 (MusicArtistParser)
位置: `tongrenlu-tool/src/main/java/info/tongrenlu/support/MusicArtistParser.java`

批量解析和导入艺术家数据的工具类

## MyBatis 配置

映射器XML文件: `tongrenlu-dao/src/main/resources/info/tongrenlu/mapper/*.xml`

**MyBatis Plus 配置**:
- 映射器包: `info.tongrenlu.mapper`
- 别名包: `info.tongrenlu.domain`
- 软删除支持: `del_flg` 字段
- 延迟加载: 已启用
- 默认执行器: REUSE

## API 文档

Springdoc OpenAPI UI:
- 本地访问: http://localhost:8443/tongrenlu/swagger-ui.html

## Maven 依赖关系

- **tongrenlu-web** ← 依赖 → **tongrenlu-dao**
- **tongrenlu-tool** ← 依赖 → **tongrenlu-dao**
- **tongrenlu-dao** - 独立模块，包含所有域模型和MyBatis配置

## 开发注意事项

1. **数据库密码**: 当前 application.properties 中包含硬编码密码，**请勿提交到版本控制**
2. **软删除**: 所有实体支持软删除（`del_flg='1'` 表示已删除）
3. **时间字段**: 统一使用 `upd_date` 字段管理时间戳
4. **Lombok**: 项目大量使用 Lombok，需确保IDE支持 Lombock注解处理
5. **编码**: 项目使用 UTF-8 编码
6. **Java版本**: pom.xml 指定的 Java 21，但建议使用 Java 23

## 测试策略

目前项目中未发现测试文件，开发新功能时建议:
- 为Service层和Manager层编写单元测试
- 为Controller层编写集成测试
- 使用H2内存数据库进行测试

## 部署注意

- 确保MySQL版本为 8.2+
- 应用默认端口为8443，注意避免端口冲突
- 单体应用，可直接部署 jar 文件
- 工具模块可独立打包运行用于数据批量处理