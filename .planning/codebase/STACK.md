# Technology Stack

**Analysis Date:** 2026-04-12

## Languages

**Primary:**
- Java 21 - Core backend development, all modules

**Secondary:**
- HTML5/CSS3/JavaScript - Frontend (vanilla JS, served as static resources)
- SQL - Database schema definitions in `sql/` directory

## Runtime

**Environment:**
- Java 21 (JDK)
- Maven 3.x for dependency management and build

**Package Manager:**
- Maven
- Parent POM: `tongrenlu/pom.xml`
- Modules: `tongrenlu-web`, `tongrenlu-dao`, `tongrenlu-tool`
- Lockfile: Not applicable (Maven uses `pom.xml` with version pinning)

## Frameworks

**Core:**
- Spring Boot 3.4.3 - Application framework
  - `tongrenlu-web/pom.xml` - Web module with `spring-boot-starter-web`
  - `tongrenlu-tool/pom.xml` - Tool module with `spring-boot-starter-web` and `spring-boot-starter-webflux`

**Data Access:**
- MyBatis Plus 3.5.11 - ORM framework
  - `tongrenlu-dao/pom.xml` - Contains `mybatis-plus-spring-boot3-starter`
  - `tongrenlu-dao/pom.xml` - Contains `mybatis-plus-jsqlparser`
- MySQL Connector/J 8.2.0 - Database driver

**Frontend:**
- WebJars for dependency management
  - jQuery 3.7.1 (`tongrenlu-web/pom.xml`)
  - Font Awesome 6.5.1 (`tongrenlu-web/pom.xml`)
- Google Fonts (external CDN)
- No frontend build tool (vanilla JS served as static files)

**Reactive:**
- Spring WebFlux (reactive web framework)
  - Used in `tongrenlu-tool` for SSE streaming
  - `tongrenlu-tool/pom.xml` - Contains `spring-boot-starter-webflux`

**Testing:**
- Not explicitly configured in POM files

## Key Dependencies

**Critical:**
- MyBatis Plus 3.5.11 - ORM and data access layer
  - `tongrenlu-dao/src/main/java/info/tongrenlu/mapper/` - Mapper interfaces
  - `tongrenlu-dao/src/main/java/info/tongrenlu/domain/` - Entity classes
- Spring Boot 3.4.3 - Core application framework
- Lombok - Boilerplate reduction via annotations
  - `@Data`, `@Slf4j`, `@RequiredArgsConstructor` used extensively

**Infrastructure:**
- HikariCP - Connection pooling (included via Spring Boot)
  - `tongrenlu-web/src/main/resources/application.properties` - Pool configuration: max 20 connections
- Jackson - JSON serialization
  - `jackson-databind`, `jackson-annotations` in `tongrenlu-dao/pom.xml`
- Apache HttpClient 5 - HTTP client
  - `httpclient5-fluent` in `tongrenlu-web/pom.xml`

**Utilities:**
- Hutool 5.8.40 - Java utility library
  - `cn.hutool.http.HttpRequest` - HTTP requests to CloudMusic API
  - `cn.hutool.http.HttpResponse` - HTTP response handling
  - Used in `tongrenlu-dao/src/main/java/info/tongrenlu/service/HomeMusicService.java`
- Apache Commons Lang3 3.18.0 - String utilities
- Apache Commons IO 2.14.0 / 2.18.0 - File I/O utilities
- Apache Commons Collections4 4.4 - Collection utilities
- Guava 33.3.1-jre - Google utilities

## Configuration

**Environment:**
- Spring properties files (`application.properties`)
  - `tongrenlu-web/src/main/resources/application.properties` - Primary web config
  - `tongrenlu-tool/src/main/resources/application.properties` - Tool module config
- Environment variables:
  - `DB_HOST` - Database host (default: localhost)
  - `DB_PASSWORD` - Database password (required)

**Key configs required:**
```properties
spring.datasource.url=jdbc:mysql://${DB_HOST:localhost}:3306/tongrenlu?useSSL=false&serverTimezone=Asia/Shanghai
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.connection-timeout=3000
mybatis.mapper-locations=classpath:info/tongrenlu/mapper/*.xml
mybatis.type-aliases-package=info.tongrenlu.domain
```

**Build:**
- Maven multi-module project structure
- Spring Boot Maven plugin for packaging

## Platform Requirements

**Development:**
- JDK 21
- Maven 3.x
- MySQL 8.x database

**Production:**
- JDK 21 runtime
- MySQL 8.x
- Server port: 8443 (configurable via `server.port`)
- Context path: `/tongrenlu`

---

*Stack analysis: 2026-04-12*
