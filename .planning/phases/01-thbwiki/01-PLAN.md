# Phase 1 Plan: THBWiki 服务基础

## Phase Information

- **Phase**: 01-thbwiki
- **Goal**: 用户可以在管理后台触发原曲抓取，系统能成功从THBWiki搜索专辑
- **Requirement IDs**: ORIGINAL-01.1

---

## Plan 01: 基础依赖与模型定义

**Wave**: 1
**Depends on**: None
**Files Modified**: `tongrenlu-dao/pom.xml`, `tongrenlu-dao/src/main/java/info/tongrenlu/model/ThbwikiAlbum.java`, `tongrenlu-dao/src/main/java/info/tongrenlu/model/ThbwikiTrack.java`
**Autonomous**: true
**Requirements**: ORIGINAL-01.1

### Must-Haves

**Truths**:
- ThbwikiAlbum 和 ThbwikiTrack 数据模型可以正确序列化/反序列化
- Jsoup 和 Caffeine 依赖已添加到 tongrenlu-dao 模块

**Artifacts**:
- `tongrenlu-dao/src/main/java/info/tongrenlu/model/ThbwikiAlbum.java` - 专辑数据模型
- `tongrenlu-dao/src/main/java/info/tongrenlu/model/ThbwikiTrack.java` - 曲目数据模型
- `tongrenlu-dao/pom.xml` - 更新依赖

**Key Links**:
- ThbwikiAlbum 包含 List<ThbwikiTrack> - 组合关系

---

### Task 1: 添加 Jsoup 和 Caffeine 依赖

<files>tongrenlu-dao/pom.xml</files>

<action>
在 tongrenlu-dao/pom.xml 的 dependencies 部分添加以下依赖：

1. **Jsoup** (HTML 解析):
```xml
<dependency>
    <groupId>org.jsoup</groupId>
    <artifactId>jsoup</artifactId>
    <version>1.18.1</version>
</dependency>
```

2. **Caffeine** (缓存):
```xml
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
    <version>3.1.8</version>
</dependency>
```

注意：放在现有依赖之后，确保版本号与项目其他依赖兼容。
</action>

<verify>
```bash
mvn dependency:tree -pl tongrenlu-dao | grep -E "jsoup|caffeine"
```
</verify>

<done>Jsoup 1.18.1 和 Caffeine 3.1.8 已添加到 tongrenlu-dao pom.xml</done>

---

### Task 2: 创建 ThbwikiTrack 模型

<files>tongrenlu-dao/src/main/java/info/tongrenlu/model/ThbwikiTrack.java</files>

<action>
创建 ThbwikiTrack.java record 类，位置: `tongrenlu-dao/src/main/java/info/tongrenlu/model/ThbwikiTrack.java`

```java
package info.tongrenlu.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ThbwikiTrack {
    private String name;
    private String originalSource;    // 原曲出处，如 "东方Project"
    private String originalName;      // 原曲名称，如 "永夜抄"
    private String originalUrl;      // THBWiki 链接
}
```

注意：
- 使用 Lombok `@Data` (与项目其他模型一致)
- 字段命名遵循项目规范 (camelCase)
- 添加 Jackson `@JsonInclude` 避免 null 字段序列化
</action>

<verify>
```bash
mvn compile -pl tongrenlu-dao -q && echo "Compilation successful"
```
</verify>

<done>ThbwikiTrack 类已创建，包含 name, originalSource, originalName, originalUrl 四个字段</done>

---

### Task 3: 创建 ThbwikiAlbum 模型

<files>tongrenlu-dao/src/main/java/info/tongrenlu/model/ThbwikiAlbum.java</files>

<action>
创建 ThbwikiAlbum.java 类，位置: `tongrenlu-dao/src/main/java/info/tongrenlu/model/ThbwikiAlbum.java`

```java
package info.tongrenlu.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ThbwikiAlbum {
    private String name;
    private String url;
    private List<ThbwikiTrack> tracks = new ArrayList<>();

    public void addTrack(ThbwikiTrack track) {
        this.tracks.add(track);
    }
}
```

注意：
- 使用 Lombok `@Data` (与项目其他模型一致)
- tracks 初始化为 ArrayList 避免 null
- 提供 addTrack 便捷方法
</action>

<verify>
```bash
mvn compile -pl tongrenlu-dao -q && echo "Compilation successful"
```
</verify>

<done>ThbwikiAlbum 类已创建，包含 name, url, tracks 三个字段，与 ThbwikiTrack 组合</done>

---

## Plan 02: ThbwikiService 核心服务

**Wave**: 2
**Depends on**: 01-01-PLAN.md (模型和依赖)
**Files Modified**: `tongrenlu-dao/src/main/java/info/tongrenlu/service/ThbwikiService.java`, `tongrenlu-dao/src/main/java/info/tongrenlu/cache/ThbwikiCacheService.java`
**Autonomous**: true
**Requirements**: ORIGINAL-01.1

### Must-Haves

**Truths**:
- 输入专辑名可以搜索 THBWiki 并返回匹配结果
- OpenSearch API 返回的 JSON 数组正确解析
- 搜索结果包含专辑名和 THBWiki 链接

**Artifacts**:
- `tongrenlu-dao/src/main/java/info/tongrenlu/service/ThbwikiService.java` - 核心搜索服务
- `tongrenlu-dao/src/main/java/info/tongrenlu/cache/ThbwikiCacheService.java` - 缓存封装

**Key Links**:
- ThbwikiService 使用 Hutool HttpRequest 调用 OpenSearch API
- ThbwikiService 依赖 ThbwikiCacheService 进行缓存

---

### Task 1: 创建 ThbwikiCacheService

<files>tongrenlu-dao/src/main/java/info/tongrenlu/cache/ThbwikiCacheService.java</files>

<action>
创建 ThbwikiCacheService.java，位置: `tongrenlu-dao/src/main/java/info/tongrenlu/cache/ThbwikiCacheService.java`

```java
package info.tongrenlu.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import info.tongrenlu.model.ThbwikiAlbum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
@Slf4j
public class ThbwikiCacheService {

    private static final Duration CACHE_TTL = Duration.ofHours(24);
    private static final int MAX_CACHE_SIZE = 1000;

    private final Cache<String, ThbwikiAlbum> cache;

    public ThbwikiCacheService() {
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(CACHE_TTL)
                .maximumSize(MAX_CACHE_SIZE)
                .recordStats()
                .build();
    }

    public Optional<ThbwikiAlbum> get(String key) {
        ThbwikiAlbum album = cache.getIfPresent(key);
        if (album != null) {
            log.debug("Cache hit for key: {}", key);
        }
        return Optional.ofNullable(album);
    }

    public void put(String key, ThbwikiAlbum album) {
        cache.put(key, album);
        log.debug("Cached album: {} (key: {})", album.getName(), key);
    }

    public void invalidate(String key) {
        cache.invalidate(key);
        log.debug("Invalidated cache for key: {}", key);
    }

    public void clear() {
        cache.invalidateAll();
        log.info("Cache cleared");
    }

    public String getStats() {
        return cache.stats().toString();
    }
}
```

注意：
- 使用 `@Component` 便于 Spring 注入
- 配置 24 小时 TTL 和 1000 条最大缓存
- 启用 stats 记录便于监控
- 提供 get/put/invalidate/clear 方法
</action>

<verify>
```bash
mvn compile -pl tongrenlu-dao -q && echo "Compilation successful"
```
</verify>

<done>ThbwikiCacheService 已创建，支持缓存读写、失效和统计</done>

---

### Task 2: 创建 ThbwikiService 核心服务

<files>tongrenlu-dao/src/main/java/info/tongrenlu/service/ThbwikiService.java</files>

<action>
创建 ThbwikiService.java，位置: `tongrenlu-dao/src/main/java/info/tongrenlu/service/ThbwikiService.java`

```java
package info.tongrenlu.service;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import info.tongrenlu.cache.ThbwikiCacheService;
import info.tongrenlu.model.ThbwikiAlbum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ThbwikiService {

    private static final String THBWIKI_BASE_URL = "https://thbwiki.cc";
    private static final String OPENSEARCH_API = THBWIKI_BASE_URL + "/api.php?action=opensearch&search=%s&format=json&limit=10";

    private final ThbwikiCacheService cacheService;
    private final ObjectMapper objectMapper;

    /**
     * 根据专辑名搜索 THBWiki
     *
     * @param albumName 专辑名称
     * @return 匹配的专辑列表（包含名称和 URL）
     */
    public List<ThbwikiAlbum> searchAlbum(String albumName) {
        if (!StringUtils.hasText(albumName)) {
            log.warn("Search attempted with empty album name");
            return List.of();
        }

        String encodedName = URLEncoder.encode(albumName.trim(), StandardCharsets.UTF_8);
        String apiUrl = String.format(OPENSEARCH_API, encodedName);

        log.info("Searching THBWiki: {}", apiUrl);

        try (HttpResponse response = HttpRequest.get(apiUrl)
                .header("User-Agent", "tongrenlu/1.0 (同人音乐库管理)")
                .timeout(10000)
                .execute()) {

            if (!response.isOk()) {
                log.error("THBWiki API returned status: {}", response.getStatus());
                return List.of();
            }

            String body = response.body();
            return parseOpenSearchResponse(body);

        } catch (Exception e) {
            log.error("Error searching THBWiki for album: {}", albumName, e);
            return List.of();
        }
    }

    /**
     * 解析 MediaWiki OpenSearch API 返回的 JSON 数组
     *
     * 响应格式: ["AlbumName", "url1", "url2", ...]
     * 第一项是搜索词，后续是匹配的 URL 列表
     */
    private List<ThbwikiAlbum> parseOpenSearchResponse(String json) {
        List<ThbwikiAlbum> results = new ArrayList<>();

        try {
            List<?> items = objectMapper.readValue(json, List.class);
            if (items.size() < 2) {
                return results;
            }

            // items[0] = 搜索词, items[1] = URL 数组
            Object urls = items.get(1);
            if (urls instanceof List<?> urlList) {
                for (Object url : urlList) {
                    if (url instanceof String urlStr) {
                        ThbwikiAlbum album = new ThbwikiAlbum();
                        // 从 URL 提取专辑名作为显示名称
                        album.setName(extractTitleFromUrl(urlStr));
                        album.setUrl(urlStr);
                        results.add(album);
                    }
                }
            }

            log.info("Found {} results from THBWiki", results.size());

        } catch (Exception e) {
            log.error("Error parsing OpenSearch response: {}", json, e);
        }

        return results;
    }

    /**
     * 从 THBWiki URL 提取专辑标题
     * 例如: https://thbwiki.cc/Satori_Maiden -> Satori Maiden
     */
    private String extractTitleFromUrl(String url) {
        if (url == null) {
            return "";
        }
        // 移除基础 URL 和下划线
        String title = url.substring(url.lastIndexOf('/') + 1);
        return title.replace('_', ' ');
    }

    /**
     * 获取缓存的专辑
     */
    public Optional<ThbwikiAlbum> getCachedAlbum(String key) {
        return cacheService.get(key);
    }

    /**
     * 缓存专辑
     */
    public void cacheAlbum(String key, ThbwikiAlbum album) {
        cacheService.put(key, album);
    }
}
```

注意：
- 使用 `@RequiredArgsConstructor` 构造函数注入 (与项目一致)
- OpenSearch API 返回格式: `["AlbumName", ["url1", "url2"]]`
- 添加 User-Agent 请求头避免被识别为爬虫
- 10 秒超时配置
- 提取专辑名从 URL 中解析（去除下划线）
</action>

<verify>
```bash
mvn compile -pl tongrenlu-dao -q && echo "Compilation successful"
```
</verify>

<done>ThbwikiService 已创建，支持通过 OpenSearch API 搜索专辑</done>

---

## Plan 03: 管理后台接口

**Wave**: 3
**Depends on**: 01-02-PLAN.md (ThbwikiService)
**Files Modified**: `tongrenlu-tool/src/main/java/info/tongrenlu/controller/AdminThbwikiController.java`
**Autonomous**: true
**Requirements**: ORIGINAL-01.1

### Must-Haves

**Truths**:
- 管理后台可以发送专辑名到后端接口
- 系统返回 THBWiki 中匹配的专辑列表
- 搜索结果包含专辑名和 THBWiki 链接

**Artifacts**:
- `tongrenlu-tool/src/main/java/info/tongrenlu/controller/AdminThbwikiController.java` - 搜索接口

**Key Links**:
- AdminThbwikiController 依赖 ThbwikiService 进行搜索
- Controller 返回 JSON 响应给前端

---

### Task 1: 创建 AdminThbwikiController

<files>tongrenlu-tool/src/main/java/info/tongrenlu/controller/AdminThbwikiController.java</files>

<action>
创建 AdminThbwikiController.java，位置: `tongrenlu-tool/src/main/java/info/tongrenlu/controller/AdminThbwikiController.java`

```java
package info.tongrenlu.controller;

import info.tongrenlu.model.ThbwikiAlbum;
import info.tongrenlu.service.ThbwikiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/thbwiki")
@RequiredArgsConstructor
@Slf4j
public class AdminThbwikiController {

    private final ThbwikiService thbwikiService;

    /**
     * 搜索 THBWiki 专辑
     *
     * @param albumName 专辑名称（搜索关键词）
     * @return 匹配的专辑列表
     */
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> searchAlbum(
            @RequestParam("albumName") String albumName) {

        log.info("THBWiki search request: {}", albumName);

        if (albumName == null || albumName.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "success", false,
                            "message", "专辑名称不能为空"
                    ));
        }

        List<ThbwikiAlbum> results = thbwikiService.searchAlbum(albumName.trim());

        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", results,
                "count", results.size()
        ));
    }

    /**
     * 健康检查接口
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
```

注意：
- Controller 命名遵循项目规范 `Admin{Name}Controller`
- 使用构造函数注入 (与项目一致)
- 返回统一的 JSON 响应格式
- 提供健康检查端点便于测试
- 添加参数验证返回友好错误信息
</action>

<verify>
```bash
mvn compile -pl tongrenlu-tool -q && echo "Compilation successful"
```
</verify>

<done>AdminThbwikiController 已创建，提供 /admin/thbwiki/search 接口</done>

---

## Verification

### Phase 1 整体验证

1. **依赖验证**:
```bash
mvn dependency:tree -pl tongrenlu-dao | grep -E "jsoup|caffeine"
# 应显示 jsoup 1.18.1 和 caffeine 3.1.8
```

2. **编译验证**:
```bash
mvn compile -pl tongrenlu-dao,tongrenlu-tool -q && echo "All modules compiled successfully"
```

3. **接口测试** (启动应用后):
```bash
# 搜索专辑
curl "http://localhost:8080/admin/thbwiki/search?albumName=Satori%20Maiden"

# 预期返回:
# {"success":true,"data":[{"name":"Satori Maiden","url":"https://thbwiki.cc/Satori_Maiden",...}],"count":1}
```

### Success Criteria Check

- [ ] 管理后台可以输入专辑名触发 THBWiki 搜索
- [ ] 系统返回 THBWiki 中匹配的专辑列表
- [ ] 搜索结果包含专辑名和 THBWiki 链接

---

## Decision Coverage Matrix

| Decision ID | Description | Plan | Task | Coverage |
|-------------|-------------|------|------|----------|
| D-01 | 搜索方式: OpenSearch API + 页面抓取 | 01-02 | Task 2 | Full |
| D-02 | 服务位置: tongrenlu-dao | 01-01, 01-02 | All | Full |
| D-03 | 缓存策略: Caffeine 24h TTL | 01-02 | Task 1 | Full |
| D-04 | 数据模型: ThbwikiAlbum/ThbwikiTrack | 01-01 | Task 2, 3 | Full |
| D-05 | HTTP 客户端: Hutool HttpRequest | 01-02 | Task 2 | Full |

---

## Output

After completion, create:
- `.planning/phases/01-thbwiki/01-01-SUMMARY.md`
- `.planning/phases/01-thbwiki/01-02-SUMMARY.md`
- `.planning/phases/01-thbwiki/01-03-SUMMARY.md`

---

*Plan created: 2026-04-13*
