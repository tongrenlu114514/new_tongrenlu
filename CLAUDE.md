<!-- GSD:project-start source:PROJECT.md -->
## Project

**同人音乐库管理 (tongrenlu)**

同人音乐库管理平台，用于管理、播放和分享同人音乐。支持从网易云音乐导入专辑信息，展示歌曲原曲出处，并提供管理后台进行批量数据维护。

**Core Value:** 帮助用户发现和管理同人音乐，快速了解歌曲的原曲出处。

### Constraints

- **数据源**: 仅支持 thbwiki.cc，不支持其他同人音乐 wiki
- **匹配策略**: 自动最佳匹配，不支持人工选择
- **操作范围**: 仅管理后台批量操作，不支持用户端触发
- **兼容旧数据**: 需要更新现有 Original 字段格式
<!-- GSD:project-end -->

<!-- GSD:stack-start source:codebase/STACK.md -->
## Technology Stack

## Languages
- Java 21 - Core backend development, all modules
- HTML5/CSS3/JavaScript - Frontend (vanilla JS, served as static resources)
- SQL - Database schema definitions in `sql/` directory
## Runtime
- Java 21 (JDK)
- Maven 3.x for dependency management and build
- Maven
- Parent POM: `tongrenlu/pom.xml`
- Modules: `tongrenlu-web`, `tongrenlu-dao`, `tongrenlu-tool`
- Lockfile: Not applicable (Maven uses `pom.xml` with version pinning)
## Frameworks
- Spring Boot 3.4.3 - Application framework
- MyBatis Plus 3.5.11 - ORM framework
- MySQL Connector/J 8.2.0 - Database driver
- WebJars for dependency management
- Google Fonts (external CDN)
- No frontend build tool (vanilla JS served as static files)
- Spring WebFlux (reactive web framework)
- Not explicitly configured in POM files
## Key Dependencies
- MyBatis Plus 3.5.11 - ORM and data access layer
- Spring Boot 3.4.3 - Core application framework
- Lombok - Boilerplate reduction via annotations
- HikariCP - Connection pooling (included via Spring Boot)
- Jackson - JSON serialization
- Apache HttpClient 5 - HTTP client
- Hutool 5.8.40 - Java utility library
- Apache Commons Lang3 3.18.0 - String utilities
- Apache Commons IO 2.14.0 / 2.18.0 - File I/O utilities
- Apache Commons Collections4 4.4 - Collection utilities
- Guava 33.3.1-jre - Google utilities
## Configuration
- Spring properties files (`application.properties`)
- Environment variables:
- Maven multi-module project structure
- Spring Boot Maven plugin for packaging
## Platform Requirements
- JDK 21
- Maven 3.x
- MySQL 8.x database
- JDK 21 runtime
- MySQL 8.x
- Server port: 8443 (configurable via `server.port`)
- Context path: `/tongrenlu`
<!-- GSD:stack-end -->

<!-- GSD:conventions-start source:CONVENTIONS.md -->
## Conventions

## Naming Conventions
### Java Classes and Interfaces
| Type | Convention | Example |
|------|------------|---------|
| Domain/Entity | `{Name}Bean` | `ArticleBean`, `TrackBean`, `ArtistBean` |
| DTO | `{Name}Bean` or `{Name}DetailBean` | `AlbumDetailBean`, `DtoBean` |
| Service | `{Name}Service` | `HomeMusicService`, `ArtistService` |
| Mapper | `{Name}Mapper` | `ArticleMapper`, `TrackMapper` |
| Controller | `Api{Name}Controller` or `Admin{Name}Controller` | `ApiMusicController`, `AdminArtistController` |
| Exception | `{Name}Exception` | `ForbiddenException`, `PageNotFoundException` |
| Job | `{Name}Job` | `PlaylistImportJob`, `MusicAlbumParseJob` |
| Constants | `CommonConstants` | `CommonConstants.SECOND` |
### Java Methods and Variables
### Constants
### JavaScript
## Code Style
### Formatting
- **Indentation:** 4 spaces (standard Java convention)
- **UTF-8 encoding:** All source files
- **Line endings:** Consistent with OS (Git handles this via .gitattributes)
### Java Annotations
### Lombok Usage
| Annotation | Usage |
|------------|-------|
| `@Data` | Domain entities, DTOs |
| `@Slf4j` | Service and Controller classes |
| `@RequiredArgsConstructor` | Service and Controller classes (constructor injection) |
| `@SneakyThrows` | When exception propagation is intentional |
### Import Organization
## Documentation Standards
### JavaDoc Comments
### JavaScript Comments
## Git Workflow Conventions
### Branch Naming
### Commit Message Format
| Type | Usage |
|------|-------|
| `feat` | New feature |
| `fix` | Bug fix |
| `refactor` | Code refactoring |
| `docs` | Documentation changes |
| `style` | UI/styling changes |
| `chore` | Build, config, tooling |
| `perf` | Performance improvements |
## Error Handling Patterns
### Service Layer
### Controller Layer
### Custom Exceptions
## Logging Conventions
### Logger Declaration
### Log Levels
| Level | Usage |
|-------|-------|
| `log.info()` | Normal operations, significant milestones |
| `log.warn()` | Recoverable issues, skipped operations |
| `log.error()` | Failures that need attention |
### Log Patterns
## Configuration Conventions
### Application Properties
### Environment Variables
| Variable | Default | Purpose |
|----------|---------|---------|
| `DB_HOST` | `localhost` | Database host |
| `DB_PASSWORD` | (none) | Database password (required) |
## Database Conventions
### Table Naming
| Prefix | Meaning | Example |
|--------|---------|---------|
| `m_` | Main table | `m_article`, `m_artist` |
| `r_` | Relation table | `r_article_tag` |
| `v_` | View | `v_music_stat` |
### Column Naming
| Pattern | Example |
|---------|---------|
| Snake_case | `cloud_music_pic_url` |
| `del_flg` | Soft delete flag (0=normal, 1=deleted) |
| `upd_date` | Update timestamp |
| `_cnt` | Count suffix |
| `_flg` | Flag suffix |
## Frontend Conventions
### File Organization
### JavaScript Module Pattern
### File Size Guideline
<!-- GSD:conventions-end -->

<!-- GSD:architecture-start source:ARCHITECTURE.md -->
## Architecture

## Pattern Overview
- Three-module Maven project: `tongrenlu-dao` (data layer), `tongrenlu-web` (public API), `tongrenlu-tool` (admin/batch processing)
- Spring Boot 3.4.3 with Java 21
- MyBatis-Plus for data access with MySQL
- Traditional HTML/jQuery frontend served as static resources
- Cloud Music API integration for music metadata
## Layers
### Data Access Layer (`tongrenlu-dao`)
- **Purpose:** Database abstraction and business logic for data operations
- **Location:** `tongrenlu-dao/src/main/java/info/tongrenlu/`
- **Contains:**
- **Depends on:** MySQL, MyBatis-Plus, Jackson
- **Used by:** `tongrenlu-web`, `tongrenlu-tool`
### Web Layer (`tongrenlu-web`)
- **Purpose:** Public-facing REST API and static frontend resources
- **Location:** `tongrenlu-web/src/main/java/info/tongrenlu/`
- **Contains:**
- **Static Resources:** `src/main/resources/static/` - HTML pages, CSS, JavaScript
- **Depends on:** `tongrenlu-dao`, Spring Web, Spring Actuator
- **Entry Point:** `TongrenluApplication.java` - runs on port 8443 with context path `/tongrenlu`
### Tool Layer (`tongrenlu-tool`)
- **Purpose:** Admin backend and batch processing endpoints
- **Location:** `tongrenlu-tool/src/main/java/info/tongrenlu/`
- **Contains:**
- **Static Resources:** `src/main/resources/static/` - Admin HTML pages
- **Depends on:** `tongrenlu-dao`, Spring Web, Spring WebFlux (for SSE streaming)
- **Features:**
## Data Flow
### Public Music API Flow:
### Cloud Music Integration Flow:
### Album Import Flow (SSE Streaming):
## Component Interactions
### Service Dependencies
```
```
### Tool Module Dependencies
```
```
## Configuration Management
### Database Configuration (`tongrenlu-web/src/main/resources/application.properties`)
```properties
```
### Environment Variables
- `DB_HOST` - MySQL host (default: localhost)
- `DB_PASSWORD` - Database password
### Module Configuration
- Root `pom.xml` defines Spring Boot 3.4.3 parent
- Each module inherits parent and declares its own dependencies
- Java 21 as compile target
## Deployment Architecture
### Build Output
- `tongrenlu-web/target/tongrenlu-web.jar` - Public web application
- `tongrenlu-tool/target/tongrenlu-tool.jar` - Admin/batch application
### Runtime Ports
- `tongrenlu-web`: Port 8443, context path `/tongrenlu`
- `tongrenlu-tool`: Default Spring Boot port (typically 8080)
### Database
- MySQL 8.x recommended
- HikariCP connection pool: max 20 connections
## Key Architectural Decisions
<!-- GSD:architecture-end -->

<!-- GSD:skills-start source:skills/ -->
## Project Skills

| Skill | Description | Path |
|-------|-------------|------|
| openspec-apply-change | Implement tasks from an OpenSpec change. Use when the user wants to start implementing, continue implementation, or work through tasks. | `.claude/skills/openspec-apply-change/SKILL.md` |
| openspec-archive-change | Archive a completed change in the experimental workflow. Use when the user wants to finalize and archive a change after implementation is complete. | `.claude/skills/openspec-archive-change/SKILL.md` |
| openspec-explore | Enter explore mode - a thinking partner for exploring ideas, investigating problems, and clarifying requirements. Use when the user wants to think through something before or during a change. | `.claude/skills/openspec-explore/SKILL.md` |
| openspec-propose | Propose a new change with all artifacts generated in one step. Use when the user wants to quickly describe what they want to build and get a complete proposal with design, specs, and tasks ready for implementation. | `.claude/skills/openspec-propose/SKILL.md` |
<!-- GSD:skills-end -->

<!-- GSD:workflow-start source:GSD defaults -->
## GSD Workflow Enforcement

Before using Edit, Write, or other file-changing tools, start work through a GSD command so planning artifacts and execution context stay in sync.

Use these entry points:
- `/gsd-quick` for small fixes, doc updates, and ad-hoc tasks
- `/gsd-debug` for investigation and bug fixing
- `/gsd-execute-phase` for planned phase work

Do not make direct repo edits outside a GSD workflow unless the user explicitly asks to bypass it.
<!-- GSD:workflow-end -->



<!-- GSD:profile-start -->
## Developer Profile

> Profile not yet configured. Run `/gsd-profile-user` to generate your developer profile.
> This section is managed by `generate-claude-profile` -- do not edit manually.
<!-- GSD:profile-end -->
