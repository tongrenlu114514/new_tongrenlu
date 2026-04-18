# Codebase Structure

**Analysis Date:** 2026-04-12

## Directory Layout

```
tongrenlu/                          # Maven multi-module root
├── pom.xml                         # Parent POM (Spring Boot 3.4.3, Java 21)
├── tongrenlu-dao/                  # Data Access Module
│   ├── pom.xml
│   └── src/
│       ├── main/java/info/tongrenlu/
│       │   ├── domain/             # MyBatis-Plus entities
│       │   ├── mapper/            # MyBatis mapper interfaces
│       │   ├── model/             # External API DTOs
│       │   ├── service/           # Business logic
│       │   └── support/           # Utilities
│       ├── main/resources/        # Mapper XMLs
│       └── test/java/             # DAO tests
├── tongrenlu-web/                  # Public Web Module
│   ├── pom.xml
│   └── src/
│       ├── main/java/info/tongrenlu/
│       │   ├── www/               # REST controllers
│       │   ├── config/            # Spring configuration
│       │   ├── constants/         # Constants
│       │   ├── exception/         # Custom exceptions
│       │   ├── enums/             # Enumerations
│       │   └── manager/           # Manager classes
│       └── main/resources/
│           ├── application.properties
│           ├── application-prd.properties
│           └── static/            # Frontend resources
├── tongrenlu-tool/                # Admin/Tool Module
│   ├── pom.xml
│   └── src/
│       ├── main/java/info/tongrenlu/
│       │   ├── *Controller.java   # Admin controllers
│       │   ├── *Job.java          # Batch processing jobs
│       │   └── support/           # Parsers and utilities
│       └── main/resources/
│           └── static/            # Admin HTML pages
├── sql/                            # Database migrations
│   └── 20251124/                  # DDL for m_article, m_track, etc.
│   └── 20251128/                  # DDL for m_artist
├── docs/                           # Documentation
└── .planning/codebase/             # Generated analysis docs
```

## Module Purposes

### `tongrenlu-dao/` - Data Access Layer

**Purpose:** Database persistence and business logic for data operations

**Key Directories:**

| Directory | Files | Purpose |
|-----------|-------|---------|
| `domain/` | `ArtistBean.java`, `ArticleBean.java`, `TrackBean.java`, `TagBean.java`, `ArticleTagBean.java`, `AlbumDetailBean.java`, `DtoBean.java`, `FileBean.java` | MyBatis-Plus entity classes with `@TableName` annotations |
| `mapper/` | `ArtistMapper.java`, `ArticleMapper.java`, `TrackMapper.java`, `TagMapper.java`, `ArticleTagMapper.java`, `MybatisPlusConfig.java` | MyBatis mapper interfaces, some with `@Select` annotations for complex queries |
| `service/` | `ArtistService.java`, `ArticleService.java`, `HomeMusicService.java`, `TagService.java` | Business logic services |
| `model/` | `CloudMusic*.java` | DTOs for Cloud Music API responses (40+ classes) |
| `support/` | `PaginateSupport.java` | Pagination utilities |

### `tongrenlu-web/` - Public Web Application

**Purpose:** Public-facing REST API and static HTML frontend

**Key Directories:**

| Directory | Files | Purpose |
|-----------|-------|---------|
| `www/` | `ApiMusicController.java`, `ApiEventController.java` | REST API endpoints |
| `config/` | `RestTemplateConfig.java` | Spring bean configurations |
| `constants/` | `CommonConstants.java` | Application-wide constants |
| `exception/` | `ForbiddenException.java`, `PageNotFoundException.java` | Custom exception classes |
| `enums/` | (enums) | Enumeration types |

**Static Resources (`src/main/resources/static/`):**

| Directory | Purpose |
|-----------|---------|
| `index.html` | Homepage with gallery background |
| `album.html` | Music library browsing |
| `artist-showcase.html` | Artist listing page |
| `event.html` | Event listing page |
| `player.html` | Audio player page |
| `admin/` | (legacy admin pages) |
| `assets/css/` | Theme CSS files (geometric-theme.css) |
| `assets/js/` | Shared JavaScript |
| `components/` | Reusable frontend components |
| `js/` | Page-specific JavaScript |

### `tongrenlu-tool/` - Admin and Batch Processing

**Purpose:** Admin backend for data management and batch processing

**Key Files:**

| File | Purpose |
|------|---------|
| `TongrenluToolApplication.java` | Entry point with `@EnableScheduling` |
| `AdminArtistController.java` | Artist CRUD operations, Cloud Music search |
| `AdminUnpublishController.java` | Unpublished album management |
| `MusicArtistParseJob.java` | Scheduled artist data parsing |
| `MusicAlbumParseJob.java` | Scheduled album data parsing |
| `PlaylistImportJob.java` | Playlist import with SSE streaming |

**Static Resources (`src/main/resources/static/`):**

| File | Purpose |
|------|---------|
| `artist.html` | Admin artist management UI |
| `unpublish.html` | Admin unpublish management UI |
| `components/admin/` | Admin-specific components |

## Key File Locations

### Entry Points

| Module | File | Description |
|--------|------|-------------|
| Web | `tongrenlu-web/src/main/java/info/tongrenlu/TongrenluApplication.java` | Spring Boot main class |
| Tool | `tongrenlu-tool/src/main/java/info/tongrenlu/TongrenluToolApplication.java` | Tool main class |

### Configuration

| File | Purpose |
|------|---------|
| `tongrenlu-web/src/main/resources/application.properties` | Web module config |
| `tongrenlu-web/src/main/resources/application-prd.properties` | Production config |
| Root `pom.xml` | Maven parent POM |

### Core Logic

| Purpose | Location |
|---------|----------|
| Music search & browse | `tongrenlu-dao/src/main/java/info/tongrenlu/service/HomeMusicService.java` |
| Artist management | `tongrenlu-dao/src/main/java/info/tongrenlu/service/ArtistService.java` |
| Music API endpoints | `tongrenlu-web/src/main/java/info/tongrenlu/www/ApiMusicController.java` |
| Admin operations | `tongrenlu-tool/src/main/java/info/tongrenlu/AdminArtistController.java` |

### Frontend

| Purpose | Location |
|---------|----------|
| Homepage | `tongrenlu-web/src/main/resources/static/index.html` |
| Music library | `tongrenlu-web/src/main/resources/static/album.html` |
| Artist page | `tongrenlu-web/src/main/resources/static/artist-showcase.html` |
| Player | `tongrenlu-web/src/main/resources/static/player.html` |
| Theme CSS | `tongrenlu-web/src/main/resources/static/assets/css/geometric-theme.css` |

## Naming Conventions

### Java Files

- **Classes:** PascalCase (`HomeMusicService`, `ApiMusicController`)
- **Mapper interfaces:** PascalCase with `Mapper` suffix (`ArtistMapper`)
- **Entity beans:** PascalCase with `Bean` suffix (`ArtistBean`, `ArticleBean`)
- **DTOs:** PascalCase with descriptive names (`CloudMusicArtistDetailResponse`)
- **Controllers:** PascalCase with `Controller` suffix (`AdminArtistController`)

### Directories

- **Java packages:** lowercase (`info.tongrenlu.service`)
- **Resources:** kebab-case for custom directories (`static/assets/css/`)

### Frontend Files

- **HTML:** kebab-case (`artist-showcase.html`, `music-library/`)
- **CSS classes:** kebab-case with BEM-like naming (`geo-hero`, `geo-gallery-bg__item`)
- **JavaScript:** kebab-case, co-located with pages (`components/admin/artist.js`)

## Where to Add New Code

### New Feature in Public API

1. **Primary code:** `tongrenlu-dao/src/main/java/info/tongrenlu/service/` - Add service method
2. **Controller:** `tongrenlu-web/src/main/java/info/tongrenlu/www/` - Add REST endpoint
3. **Frontend:** `tongrenlu-web/src/main/resources/static/` - Add HTML/JS

### New Admin Feature

1. **Controller:** `tongrenlu-tool/src/main/java/info/tongrenlu/` - New `*Controller.java`
2. **Static UI:** `tongrenlu-tool/src/main/resources/static/` - New HTML
3. **JavaScript:** `tongrenlu-tool/src/main/resources/static/components/admin/` - Component JS

### New Entity

1. **Entity class:** `tongrenlu-dao/src/main/java/info/tongrenlu/domain/` - Add `*Bean.java`
2. **Mapper:** `tongrenlu-dao/src/main/java/info/tongrenlu/mapper/` - Add `*Mapper.java`
3. **SQL:** Create new file in `sql/` directory

### New External API Integration

1. **DTOs:** `tongrenlu-dao/src/main/java/info/tongrenlu/model/` - Add response classes
2. **Service:** Add API call method in relevant service class

## Special Directories

### `sql/`

- Purpose: Database schema migrations (DDL files)
- Pattern: Date-based folders (`YYYYMMDD/`)
- Contains: Table DDL for `m_article`, `m_track`, `m_artist`, `m_tag`, `r_article_tag`

### `docs/`

- Purpose: Project documentation
- Generated: No
- Committed: Yes

### `.planning/codebase/`

- Purpose: Generated architecture/structure analysis (this directory)
- Generated: Yes (by GSD mapping)
- Committed: Yes

### `tongrenlu-dao/src/graphify-out/`

- Purpose: Graphify knowledge graph output (generated by graphify skill)
- Generated: Yes
- Committed: No (likely in .gitignore)

---

*Structure analysis: 2026-04-12*
