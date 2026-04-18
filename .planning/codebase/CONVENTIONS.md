# Coding Conventions

**Analysis Date:** 2026-04-12

## Naming Conventions

### Java Classes and Interfaces

**Pattern:** PascalCase

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

**Pattern:** camelCase

```java
// Good
private HomeMusicService musicService;
public List<ArticleBean> getRandomAlbums(int count)
public boolean reportAlbumError(Long albumId)

// Field naming
@TableField(value = "cloud_music_pic_url")
private String cloudMusicPicUrl;
```

### Constants

**Pattern:** UPPER_SNAKE_CASE

```java
public static final String ARTIST = "artist";
public static final String EVENT = "event";
public static final String M_ARTICLE = "m_article";
public static final int SEARCH_TYPE_ALBUM = 10;
```

### JavaScript

**Pattern:** camelCase for functions/variables, PascalCase for object constructors

```javascript
// Good - Module pattern with object
const GalleryHero = {
    $grid: null,
    albums: [],

    init: function() { ... },
    loadAlbums: function() { ... },
    renderGallery: function() { ... }
};

// Exposed to global
window.GalleryHero = GalleryHero;
```

## Code Style

### Formatting

- **Indentation:** 4 spaces (standard Java convention)
- **UTF-8 encoding:** All source files
- **Line endings:** Consistent with OS (Git handles this via .gitattributes)

### Java Annotations

```java
// Domain/Entity classes
@JsonInclude(Include.NON_DEFAULT)
@TableName(value = "m_article", autoResultMap = true)
@Data
public class ArticleBean { ... }

// Service classes
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class HomeMusicService { ... }

// Controller classes
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/music")
@Slf4j
public class ApiMusicController { ... }
```

### Lombok Usage

The project uses Lombok extensively to reduce boilerplate:

| Annotation | Usage |
|------------|-------|
| `@Data` | Domain entities, DTOs |
| `@Slf4j` | Service and Controller classes |
| `@RequiredArgsConstructor` | Service and Controller classes (constructor injection) |
| `@SneakyThrows` | When exception propagation is intentional |

```java
// Constructor injection pattern (preferred)
@Service
@RequiredArgsConstructor
public class HomeMusicService {
    private final ArticleMapper articleMapper;
    private final TrackMapper trackMapper;
}
```

### Import Organization

1. Java standard library
2. Third-party libraries (Spring, MyBatis, etc.)
3. Project imports

```java
import cn.hutool.http.HttpRequest;  // Third-party
import cn.hutool.http.HttpResponse;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import info.tongrenlu.domain.ArticleBean;  // Project imports
import info.tongrenlu.mapper.ArticleMapper;
```

## Documentation Standards

### JavaDoc Comments

Use JavaDoc for public APIs and important classes:

```java
/**
 * 网易云歌单专辑批量导入任务
 * 通过歌单ID获取所有专辑并批量导入到数据库
 * 使用 WebFlux 真正的异步流式输出执行结果
 */
@RestController
public class PlaylistImportJob { ... }

/**
 * Report an error for an album by setting its publishFlg to "0"
 *
 * @param albumId the ID of the album to mark as having an error
 * @return true if successful, false otherwise
 */
public boolean reportAlbumError(Long albumId) { ... }
```

### JavaScript Comments

```javascript
/**
 * 图片画廊 Hero 区域组件
 */
(function($) {
    'use strict';
    // ...
})();
```

## Git Workflow Conventions

### Branch Naming

Not explicitly enforced, but commit messages indicate module prefixes.

### Commit Message Format

**Pattern:** `<type>(<scope>): <description in Chinese>`

| Type | Usage |
|------|-------|
| `feat` | New feature |
| `fix` | Bug fix |
| `refactor` | Code refactoring |
| `docs` | Documentation changes |
| `style` | UI/styling changes |
| `chore` | Build, config, tooling |
| `perf` | Performance improvements |

**Scope Examples:** `web`, `tool`, `player`, `event`, `artist-showcase`, `music-library`, `home`, `playlist`, `agents`

**Examples from git history:**
```
fix(web): 修复音乐库报告错误按钮和搜索建议显示问题
feat(tool): 使用 WebFlux 实现 SSE 流式歌单导入
docs(agents): 更新项目文档反映管理后台迁移至 tool 模块
refactor(tool): 迁移管理后台至 tool 模块并重构批处理为 HTTP 端点
feat(player,event): 播放器显示Original字段 + 展会缓存与筛选优化
```

## Error Handling Patterns

### Service Layer

```java
// Pattern 1: Return null for not found
public AlbumDetailBean getAlbumDetail(Long albumId) {
    ArticleBean article = this.articleMapper.selectById(albumId);
    if (article == null) {
        return null;
    }
    // ...
}

// Pattern 2: Throw exception
public boolean updateAlbum(Long albumId, ...) {
    try {
        // ...
    } catch (Exception e) {
        log.error("Error updating album {}", albumId, e);
        throw e;
    }
}

// Pattern 3: Return boolean with logging
public boolean reportAlbumError(Long albumId) {
    try {
        // ...
    } catch (Exception e) {
        log.warn("cloudMusicId = {}", albumId);
        throw e;
    }
}
```

### Controller Layer

```java
@GetMapping("track")
public ResponseEntity<CloudMusicDetailResponse> getTrackById(@RequestParam Long id) {
    TrackBean track = musicService.getTrackById(id);
    if (track == null) {
        return ResponseEntity.notFound().build();
    }
    // ...
}

@GetMapping("/album-stats")
public ResponseEntity<Map<String, Object>> getAlbumStats() {
    Map<String, Object> response = new HashMap<>();
    try {
        Map<String, Long> stats = this.musicService.getAlbumStats();
        response.put("success", true);
        response.put("data", stats);
        return ResponseEntity.ok(response);
    } catch (Exception e) {
        log.error("Error getting album stats", e);
        response.put("success", false);
        response.put("message", "获取数据失败");
        return ResponseEntity.internalServerError().body(response);
    }
}
```

### Custom Exceptions

```java
@ResponseStatus(value = HttpStatus.FORBIDDEN)
public class ForbiddenException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ForbiddenException(final String message) {
        super(message);
    }
}
```

## Logging Conventions

### Logger Declaration

Use Lombok's `@Slf4j`:

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class HomeMusicService {
    // log is automatically available
}
```

### Log Levels

| Level | Usage |
|-------|-------|
| `log.info()` | Normal operations, significant milestones |
| `log.warn()` | Recoverable issues, skipped operations |
| `log.error()` | Failures that need attention |

### Log Patterns

```java
// Method entry/results
log.info("save cloud music album: {}", cloudMusicAlbum.getName());

// Errors with context
log.error("Error updating album {}", albumId, e);

// Warnings with context
log.warn("读取进度文件失败: {}", e.getMessage());

// Debug info
log.info("Searching cloud music: {}", url);
```

## Configuration Conventions

### Application Properties

**Location:** `src/main/resources/application.properties`

```properties
# Server
spring.application.name=tongrenlu
server.port=8443
server.servlet.context-path=/tongrenlu

# DataSource - Environment variable for password
spring.datasource.url=jdbc:mysql://${DB_HOST:localhost}:3306/tongrenlu?useSSL=false&serverTimezone=Asia/Shanghai
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.username= deepseek
spring.datasource.password= ${DB_PASSWORD}

# MyBatis
mybatis.mapper-locations=classpath:info/tongrenlu/mapper/*.xml
mybatis.type-aliases-package=info.tongrenlu.domain
mybatis.configuration.default-executor-type=REUSE
mybatis.configuration.lazy-loading-enabled=true
mybatis.configuration.aggressive-lazy-loading=false
```

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

```
src/main/resources/static/
├── components/
│   ├── home/           # Home page components
│   ├── player/         # Player components
│   ├── music-library/  # Music library components
│   ├── event-list/     # Event components
│   ├── artist-showcase/# Artist showcase components
│   └── shared/         # Shared utilities
├── assets/
│   └── css/           # Stylesheets
```

### JavaScript Module Pattern

```javascript
(function($) {
    'use strict';

    const ModuleName = {
        property: null,

        init: function() {
            // Initialization
        },

        methodName: function() {
            // Implementation
        }
    };

    // Expose to global
    window.ModuleName = ModuleName;

    $(document).ready(function() {
        ModuleName.init();
    });

})(jQuery);
```

### File Size Guideline

Per project CLAUDE.md: Frontend resource files should be split by module and kept under 200 lines when possible.

---

*Convention analysis: 2026-04-12*
