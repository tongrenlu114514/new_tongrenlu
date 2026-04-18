# External Integrations

**Analysis Date:** 2026-04-12

## APIs & External Services

**Music Data Integration:**
- **NetEase CloudMusic (Music.163.com)** - Primary music data source
  - Base URL: `https://apis.netstart.cn/music`
  - Uses Hutool `HttpRequest` for API calls
  - Implementation: `tongrenlu-dao/src/main/java/info/tongrenlu/service/HomeMusicService.java`

  **Endpoints used:**
  - `/album?id={id}` - Album detail and tracks
  - `/cloudsearch` - Search albums and artists
  - `/artist/desc?id={id}` - Artist biography
  - `/artist/detail?id={id}` - Artist detail
  - `/playlist/track/all?id={id}&limit=&offset=` - Playlist tracks
  - `/song/download/url` - Music file URLs

  **Response models:** `tongrenlu-dao/src/main/java/info/tongrenlu/model/CloudMusic*.java`

**Google Fonts:**
- Fonts: Bebas Neue, Space Grotesk, Outfit, Noto Sans SC
- Loaded via CDN in HTML templates

**Font Awesome:**
- Version 6.5.1
- Included via WebJars in `tongrenlu-web/pom.xml`

**jQuery:**
- Version 3.7.1
- Included via WebJars in `tongrenlu-web/pom.xml`

## Data Storage

**Database:**
- **MySQL 8.2.0**
  - Driver: `mysql-connector-j:8.2.0`
  - Connection: Configured via `spring.datasource.url`
  - ORM: MyBatis Plus 3.5.11
  - Connection Pool: HikariCP (max 20 connections, 3s timeout)

**Table prefixes:**
- `m_` - Main tables (e.g., `m_article`, `m_artist`, `m_tag`, `m_track`)
- `r_` - Relation tables (e.g., `r_article_tag`)

**Schema files:** `sql/` directory

## Caching

**MyBatis Session Cache:**
- Level 1 cache enabled by default
- Lazy loading enabled: `mybatis.configuration.lazy-loading-enabled=true`
- Aggressive lazy loading disabled

**Note:** Redis mentioned in architecture docs but not implemented in current codebase

## Async Processing

**Spring Async:**
- `@EnableAsync` enabled in `tongrenlu-web`
  - `tongrenlu-web/src/main/java/info/tongrenlu/TongrenluApplication.java`

**Spring WebFlux (Reactive):**
- Used in `tongrenlu-tool` for SSE streaming
- `PlaylistImportJob` (`tongrenlu-tool/src/main/java/info/tongrenlu/PlaylistImportJob.java`)
  - Produces `MediaType.TEXT_EVENT_STREAM_VALUE`
  - Uses `Flux<ServerSentEvent<String>>` for real-time progress updates
  - Implements resumable imports via progress file

**Scheduled Tasks:**
- `@EnableScheduling` enabled in `tongrenlu-tool`
  - `tongrenlu-tool/src/main/java/info/tongrenlu/TongrenluToolApplication.java`

## Authentication & Authorization

**Current State:**
- No authentication framework implemented
- Spring Security not detected in dependencies
- Admin endpoints in `tongrenlu-tool` are unprotected

**Architecture docs mention:**
- Session/Cookie authentication (planned)
- JWT Token support (planned)
- OAuth2.0 integration (planned)

## Monitoring & Observability

**Spring Boot Actuator:**
- Included in `tongrenlu-web/pom.xml`
- Provides `/actuator` endpoints for health, metrics

**Logging:**
- SLF4J API with Logback implementation
- `@Slf4j` Lombok annotation used throughout
- Example: `tongrenlu-dao/src/main/java/info/tongrenlu/service/HomeMusicService.java`

**Log Configuration:**
- Logback config not explicitly present (uses Spring Boot defaults)
- Console logging primarily used

## CI/CD & Deployment

**Build:**
- Maven multi-module build
- Spring Boot Maven plugin for executable JAR packaging

**Containerization:**
- Dockerfile not present in repository
- Architecture docs mention Docker support (planned)

## Environment Configuration

**Required env vars:**
- `DB_HOST` - MySQL host (default: localhost)
- `DB_PASSWORD` - Database password (required)

**Local development:**
```properties
spring.datasource.url=jdbc:mysql://localhost:3306/tongrenlu
server.port=8443
server.servlet.context-path=/tongrenlu
```

## File Storage

**Local filesystem:**
- Progress files for batch jobs stored in `tongrenlu-tool/src/main/resources/data/`
  - `playlist_progress.txt`
  - `album_progress.txt`
  - `artist_progress.txt`

**No external storage service:**
- No S3, OSS, or similar cloud storage integration

## Web Clients

**HTTP Client (Synchronous):**
- RestTemplate - Configured in `tongrenlu-web/src/main/java/info/tongrenlu/config/RestTemplateConfig.java`

**HTTP Client (Hutool):**
- `cn.hutool.http.HttpRequest` - Used for CloudMusic API calls
- Simpler API than RestTemplate

**HTTP Client (Reactive):**
- Spring WebFlux WebClient (implicit via WebFlux)

## API Endpoints

**Music API:**
- `GET /tongrenlu/api/music/search` - Search albums
- `GET /tongrenlu/api/music/detail` - Album details
- `GET /tongrenlu/api/music/track` - Track (music file) URL
- `GET /tongrenlu/api/music/album-stats` - Statistics
- `POST /tongrenlu/api/music/report-error` - Report album error
- `GET /tongrenlu/api/music/random` - Random album
- `GET /tongrenlu/api/music/random-albums` - Multiple random albums
- `GET /tongrenlu/api/music/tags` - Popular tags
- `GET /tongrenlu/api/music/artists` - Artist list with album count

**Admin/Tool API:**
- `GET /playlist/import` - SSE stream for playlist import
- Various batch processing endpoints

---

*Integration audit: 2026-04-12*
