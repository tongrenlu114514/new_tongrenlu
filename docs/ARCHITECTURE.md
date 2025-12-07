# 架构设计文档 (ARCHITECTURE)

## 1. 系统架构总览

### 1.1 架构设计原则
- **微服务架构**: 采用模块化设计，支持独立部署和扩展
- **分层架构**: 清晰的分层职责分离，提高可维护性
- **RESTful API**: 标准化接口设计，支持前后端分离
- **云原生**: 支持容器化部署和弹性伸缩

### 1.2 整体架构图
```
┌─────────────────────────────────────────────────────────────┐
│                  客户端层 (Client Layer)                     │
├─────────────────────────────────────────────────────────────┤
│  Web端(React/Vue)  │  移动端(App)    │  第三方接入(API)      │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                  网关层 (Gateway Layer)                     │
├─────────────────────────────────────────────────────────────┤
│  负载均衡  │  API网关  │  认证授权  │  限流熔断  │  日志收集  │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                  业务服务层 (Business Layer)                │
├─────────────────────────────────────────────────────────────┤
│  用户服务  │  内容服务  │  搜索服务  │  文件服务  │  统计服务  │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                  数据访问层 (Data Access Layer)             │
├─────────────────────────────────────────────────────────────┤
│  MySQL主从 │  Redis缓存 │  ES搜索  │  对象存储  │  消息队列  │
└─────────────────────────────────────────────────────────────┘
```

## 2. 模块架构设计

### 2.1 当前模块分层 (基于现有代码)
```
tongrenlu-project/
├── tongrenlu-web/                 # Web应用主模块
│   ├── info.tongrenlu.www/        # 控制器层 (Controller)
│   ├── info.tongrenlu.manager/    # 业务逻辑层 (Service/Manager)
│   ├── info.tongrenlu.domain/     # 数据传输对象 (DTO/VO)
│   └── info.tongrenlu.support/    # 支撑工具类
├── tongrenlu-dao/                 # 数据访问层
│   ├── info.tongrenlu.domain/     # 实体类 (Entity)
│   └── info.tongrenlu.mapper/     # MyBatis映射器
└── tongrenlu-tool/                # 工具模块
    └── info.tongrenlu.support/    # 批处理工具类
```

### 2.2 控制器层 (Controller)设计
**职责范围**:
- 接收HTTP请求
- 参数验证和转换
- 调用业务逻辑层
- 返回响应结果

**设计模式**:
- RESTful API设计
- 统一的异常处理
- 统一的响应格式
- 跨域处理支持

**关键控制器**:
- `ApiMusicController` - 音乐相关API
- `AdminArtistController` - 艺术家管理
- `AdminUnpublishController` - 未发布内容管理

### 2.3 业务逻辑层 (Manager/Service)设计
**职责范围**:
- 业务规则实现
- 事务管理
- 数据验证
- 缓存策略

**设计原则**:
- 单一职责原则
- 依赖注入
- 接口分离原则
- 单元测试友好

### 2.4 数据访问层 (DAO)设计
**技术选型**:
- **ORM框架**: MyBatis Plus 3.5.11
- **数据库**: MySQL 8.2.0
- **连接池**: HikariCP
- **事务管理**: Spring声明式事务

**核心配置**:
- 软删除支持 (`del_flg`字段)
- 乐观锁配置
- 分页插件配置
- SQL日志输出

## 3. 数据架构设计

### 3.1 数据库设计规范
**命名规范**:
- 主表前缀: `m_` (如: m_article, m_user)
- 关系表前缀: `r_` (如: r_article_tag)
- 视图前缀: `v_` (如: v_music_stat)

**表结构设计**:
```sql
-- 示例: 文章表结构
CREATE TABLE m_article (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT COMMENT '作者ID',
    title VARCHAR(255) NOT NULL COMMENT '标题',
    content TEXT COMMENT '内容',
    publish_flg CHAR(1) DEFAULT '0' COMMENT '发布标志',
    publish_date DATETIME COMMENT '发布时间',
    access_cnt INT DEFAULT 0 COMMENT '访问次数',
    upd_date DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flg CHAR(1) DEFAULT '0' COMMENT '删除标志(0:正常,1:删除)'
);
```

### 3.2 关键数据模型
#### 3.2.1 音乐专辑模型
```java
public class ArticleBean {
    private Long id;
    private String title;
    private String artist;
    private String description;
    private String code;               // 专辑编号
    private String cloudMusicPicUrl;   // 封面图URL
    private Long cloudMusicId;        // 网易云音乐ID
    private String cloudMusicName;    // 网易云音乐名称
    private Date publishDate;
    private Integer accessCount;
    // ... getters/setters
}
```

#### 3.2.2 艺术家模型
```java
public class ArtistBean {
    private Long id;
    private String name;
    private String description;
    private String avatarUrl;
    private Integer musicCount;
    // ... getters/setters
}
```

### 3.3 缓存策略设计
**一级缓存**: MyBatis Session级别缓存
**二级缓存**: Redis分布式缓存
- 热点数据缓存 (音乐列表、用户信息)
- 会话状态缓存
- 临时数据缓存

**缓存失效策略**:
- TTL + 主动失效
- 写操作触发缓存更新
- 缓存穿透保护

## 4. 技术架构细节

### 4.1 Spring Boot配置
**核心配置类**:
- `TongrenluApplication` - 主启动类
- `WebConfig` - Web相关配置
- `MyBatisConfig` - 数据访问配置
- `SecurityConfig` - 安全配置

**配置管理**:
- 多环境配置支持
- 配置文件加密
- 配置热更新

### 4.2 数据库连接配置
```properties
spring.datasource.url=jdbc:mysql://localhost:3306/tongrenlu
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.connection-timeout=3000
mybatis.mapper-locations=classpath:info/tongrenlu/mapper/*.xml
```

### 4.3 监控和日志
**监控指标**:
- Spring Boot Actuator端点
- 自定义业务指标
- 数据库连接池监控

**日志策略**:
- SLF4J + Logback
- 分级日志输出
- 结构化日志格式
- 日志文件轮转

## 5. 安全架构设计

### 5.1 认证授权
**认证方式**:
- Session/Cookie认证
- JWT Token支持（扩展）
- OAuth2.0集成（扩展）

**权限控制**:
- 基于角色的访问控制(RBAC)
- 方法级别权限注解
- 数据权限控制

### 5.2 安全防护
- SQL注入防护 (MyBatis参数化查询)
- XSS攻击防护 (输出转义)
- CSRF防护
- 文件上传安全校验
- 敏感信息加密存储

## 6. 性能优化设计

### 6.1 数据库优化
**索引策略**:
- 主键索引
- 唯一索引 (用户名、邮箱等)
- 复合索引 (查询频繁的组合字段)
- 全文索引 (搜索功能)

**查询优化**:
- 分页查询优化
- 延迟加载策略
- 批量操作优化

### 6.2 缓存优化
**多级缓存架构**:
- 本地缓存 (Caffeine)
- 分布式缓存 (Redis)
- 浏览器缓存 (HTTP缓存头)

**缓存粒度控制**:
- 对象级缓存
- 列表级缓存
- 页面级缓存

## 7. 扩展性设计

### 7.1 水平扩展支持
- 无状态服务设计
- 会话外部化存储
- 数据库读写分离
- 负载均衡支持

### 7.2 微服务拆分规划
当前单体架构，未来可拆分为：
- 用户服务 (user-service)
- 内容服务 (content-service)
- 搜索服务 (search-service)
- 文件服务 (file-service)
- 统计服务 (stats-service)

## 8. 部署架构

### 8.1 容器化部署
**Docker配置**:
```dockerfile
FROM openjdk:21-jre-slim
COPY target/tongrenlu-web.jar app.jar
EXPOSE 8443
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

**编排工具**:
- Docker Compose (开发环境)
- Kubernetes (生产环境)

### 8.2 云原生架构
- 服务注册发现
- 配置中心
- 链路追踪
- 监控告警

## 9. 技术债务和优化方向

### 9.1 当前技术债务
- 硬编码数据库密码
- 缺少单元测试覆盖
- 日志系统需要完善
- 缺少API文档自动生成

### 9.2 优化方向
- 引入Spring Security完善安全机制
- 增加API网关层
- 引入ELK栈进行日志分析
- 数据库读写分离
- 引入消息队列异步处理

---
**文档版本**: v1.0
**最后更新**: 2025-12-07
**负责人**: 架构团队