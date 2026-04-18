# Architecture

**Analysis Date:** 2026-04-12

## Pattern Overview

**Overall:** Spring Boot Multi-Module Monolith with External API Integration

**Key Characteristics:**
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
  - `domain/` - Entity beans (MyBatis-Plus annotated)
  - `mapper/` - MyBatis mapper interfaces and XML
  - `service/` - Business logic services
  - `model/` - External API response DTOs (Cloud Music API)
  - `support/` - Pagination and utility support classes
- **Depends on:** MySQL, MyBatis-Plus, Jackson
- **Used by:** `tongrenlu-web`, `tongrenlu-tool`

### Web Layer (`tongrenlu-web`)

- **Purpose:** Public-facing REST API and static frontend resources
- **Location:** `tongrenlu-web/src/main/java/info/tongrenlu/`
- **Contains:**
  - `www/` - REST controllers for public API
  - `config/` - Spring configuration (RestTemplate)
  - `constants/` - Application constants
  - `exception/` - Custom exceptions (ForbiddenException, PageNotFoundException)
  - `enums/` - Enumerations
  - `manager/` - Manager classes
- **Static Resources:** `src/main/resources/static/` - HTML pages, CSS, JavaScript
- **Depends on:** `tongrenlu-dao`, Spring Web, Spring Actuator
- **Entry Point:** `TongrenluApplication.java` - runs on port 8443 with context path `/tongrenlu`

### Tool Layer (`tongrenlu-tool`)

- **Purpose:** Admin backend and batch processing endpoints
- **Location:** `tongrenlu-tool/src/main/java/info/tongrenlu/`
- **Contains:**
  - Controllers: `AdminArtistController`, `AdminUnpublishController`
  - Jobs: `MusicArtistParseJob`, `MusicAlbumParseJob`, `PlaylistImportJob`
  - Support: Parsers and context classes for music data processing
- **Static Resources:** `src/main/resources/static/` - Admin HTML pages
- **Depends on:** `tongrenlu-dao`, Spring Web, Spring WebFlux (for SSE streaming)
- **Features:**
  - `@EnableScheduling` for scheduled tasks
  - WebFlux for SSE (Server-Sent Events) playlist import

## Data Flow

### Public Music API Flow:

1. **Request:** Client -> `tongrenlu-web` REST API (`/api/music/*`)
2. **Controller:** `ApiMusicController` receives request
3. **Service:** Delegates to `HomeMusicService` or `ArtistService`
4. **DAO:** `HomeMusicService` queries via MyBatis mappers
5. **Response:** JSON response returned to client

### Cloud Music Integration Flow:

1. **Admin Request:** `/api/artist/*` to `tongrenlu-tool`
2. **Controller:** `AdminArtistController` calls Cloud Music API via Hutool HTTP
3. **Parse:** Response parsed into `CloudMusic*` model classes
4. **Transform:** Data transformed and saved via `HomeMusicService`
5. **Store:** Entities persisted to MySQL via MyBatis-Plus

### Album Import Flow (SSE Streaming):

1. **POST:** `/api/playlist/import` with playlist ID
2. **WebFlux Controller:** Returns `Flux<String>` via SSE
3. **Background Job:** `PlaylistImportJob` fetches tracks in batches
4. **Progress:** Each album processed triggers SSE event
5. **Client:** Receives streaming progress updates

## Component Interactions

### Service Dependencies

```
ApiMusicController
  |-- HomeMusicService
  |     |-- ArticleMapper
  |     |-- TrackMapper
  |     |-- TagMapper
  |     |-- ArticleTagMapper
  |     |-- ArtistService
  |
  |-- ArtistService
        |-- ArticleTagMapper
        |-- TagMapper
        |-- ArtistMapper
```

### Tool Module Dependencies

```
AdminArtistController
  |-- HomeMusicService (from tongrenlu-dao)
  |-- ArticleService (from tongrenlu-dao)
  |-- ArtistService (from tongrenlu-dao)

PlaylistImportJob
  |-- HomeMusicService
```

## Configuration Management

### Database Configuration (`tongrenlu-web/src/main/resources/application.properties`)

```properties
spring.datasource.url=jdbc:mysql://${DB_HOST:localhost}:3306/tongrenlu
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}
mybatis.mapper-locations=classpath:info/tongrenlu/mapper/*.xml
mybatis.type-aliases-package=info.tongrenlu.domain
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

1. **Multi-Module Maven:** Shared `tongrenlu-dao` module prevents code duplication between web and tool modules

2. **MyBatis-Plus:** Used instead of JPA for fine-grained SQL control and complex queries (artist album counts with JOINs)

3. **Static HTML Frontend:** jQuery-based SPA served as static resources, API-driven

4. **WebFlux for SSE:** Used specifically for streaming playlist import progress without blocking

5. **Cloud Music API Integration:** External API (apis.netstart.cn) for music metadata, cached locally

6. **Publish Flag Pattern:** Albums use `publishFlg` ("0", "1", "2") for draft/published/no-match states

---

*Architecture analysis: 2026-04-12*
