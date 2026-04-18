# Codebase Concerns

**Analysis Date:** 2026-04-12

## Security Considerations

### Critical: Hardcoded Credentials

**Issue:** Database credentials and IP addresses are hardcoded in source-controlled files.

**Files:**
- `tongrenlu-web/src/main/resources/application.properties` (line 10): `spring.datasource.username= deepseek`
- `tongrenlu-web/src/main/resources/application-prd.properties` (line 1): Production IP `121.37.183.44:3306`
- `tongrenlu-tool/src/main/resources/application.properties` (line 6): `spring.datasource.username= deepseek`

**Impact:** Database credentials exposed in git history. Production IP leaked.

**Fix approach:** Move all credentials to environment variables and use `.env` files (not committed to git).

### High: No Authentication/Authorization

**Issue:** No authentication or authorization mechanisms found in the codebase.

**Evidence:**
- No `@Secured`, `@RolesAllowed`, `@PreAuthorize` annotations
- No Spring Security configuration
- Admin endpoints in `tongrenlu-tool` module are publicly accessible
- No user authentication in `ApiMusicController`, `AdminArtistController`

**Files:**
- `tongrenlu-web/src/main/java/info/tongrenlu/www/ApiMusicController.java`
- `tongrenlu-tool/src/main/java/info/tongrenlu/AdminArtistController.java`
- `tongrenlu-tool/src/main/java/info/tongrenlu/AdminUnpublishController.java`

**Impact:** Anyone can access admin functions like album deletion, artist management, and batch import operations.

**Fix approach:** Implement Spring Security with role-based access control for admin endpoints.

### Medium: Spring Boot Actuator Exposed

**Issue:** Actuator dependency is included but not configured with security.

**Files:**
- `tongrenlu-web/pom.xml` (line 75-76): Spring Boot Actuator included
- No `application.properties` security configuration for actuator

**Impact:** Potential information disclosure if actuator endpoints are not secured.

**Fix approach:** Configure actuator security or disable endpoints not needed.

---

## Performance Bottlenecks

### Critical: ORDER BY RAND() on Large Datasets

**Issue:** Uses `ORDER BY RAND()` which is inefficient for large datasets.

**Files:**
- `tongrenlu-dao/src/main/java/info/tongrenlu/service/HomeMusicService.java` (line 625):
  ```java
  queryWrapper.last("ORDER BY RAND() LIMIT " + actualCount);
  ```

**Impact:** Full table scan and sort on every request. Performance degrades exponentially as data grows.

**Fix approach:** Use application-side randomization with indexed columns, or fetch ID range and select random offsets.

### High: N+1 Query Pattern in Artist Listing

**Issue:** Artist list causes N+1 queries for album counts.

**Files:**
- `tongrenlu-dao/src/main/java/info/tongrenlu/service/ArtistService.java` (lines 56-63):
  ```java
  resultPage.getRecords().forEach(artist -> {
      if (artist.getTagId() != null) {
          long albumCount = this.getBaseMapper().countAlbumsByTagId(artist.getTagId());
          // N+1 query for each artist
      }
  });
  ```

**Impact:** 1 query for artists + N queries for counts = performance degrades with artist count.

**Fix approach:** Use JOIN in the base query (similar to `ArtistMapper.findAllArtistsWithAlbumCount()`).

### High: Missing Database Indexes

**Issue:** No visible index definitions for frequently queried columns.

**Evidence:** Tables `m_article`, `m_track`, `m_tag` lack index definitions in SQL schema files.

**Affected Queries:**
- `ArticleBean::getPublishFlg` - used in most queries
- `ArticleBean::getCloudMusicId` - used for duplicate checking
- `TrackBean::getArticleId` - used for track listing
- `r_article_tag` foreign keys

**Impact:** Full table scans for common queries.

**Fix approach:** Add indexes on:
```sql
CREATE INDEX idx_article_publish_flg ON m_article(publish_flg);
CREATE INDEX idx_article_cloud_music_id ON m_article(cloud_music_id);
CREATE INDEX idx_track_article_id ON m_track(article_id);
CREATE INDEX idx_article_tag_relations ON r_article_tag(article_id, tag_id);
```

### Medium: HikariCP Pool Size

**Issue:** Connection pool may be undersized for concurrent load.

**Files:**
- `tongrenlu-web/src/main/resources/application.properties` (line 7): `spring.datasource.hikari.maximum-pool-size=20`

**Impact:** Connection starvation under high concurrent load.

**Fix approach:** Monitor connection usage and adjust pool size accordingly. Consider sizing based on concurrent users.

### Medium: No Caching Layer

**Issue:** Every request hits the database. No Redis or in-memory caching.

**Impact:**
- Repeated album detail queries
- Artist listing recalculates counts every time
- Popular tags queried on every page load

**Fix approach:** Add Redis caching for:
- Album details (short TTL: 5-15 minutes)
- Artist counts (medium TTL: 30-60 minutes)
- Popular tags (medium TTL: 15-30 minutes)

---

## Technical Debt

### High: Stub Methods Return Zero

**Issue:** `getLikeCount()` and `getCommentCount()` are implemented but always return 0.

**Files:**
- `tongrenlu-dao/src/main/java/info/tongrenlu/service/HomeMusicService.java` (lines 131-143):
  ```java
  private int getLikeCount(Long albumId) {
      // 这里需要根据实际的数据访问逻辑来实现
      // 暂时返回0，实际项目中需要通过Mapper查询r_like表
      return 0;
  }
  ```

**Impact:** Like and comment functionality is non-functional.

**Fix approach:** Implement actual table queries for `r_like` and `m_comment` tables.

### Medium: Dead Code

**Issue:** Unused method returns null.

**Files:**
- `tongrenlu-web/src/main/java/info/tongrenlu/www/ApiMusicController.java` (lines 147-149):
  ```java
  private CloudMusicDetailResponse getMusicDetailResponseKXZ(TrackBean track) {
      return null;
  }
  ```

**Impact:** Confusing code, potential confusion for future developers.

**Fix approach:** Remove or implement the method.

### High: No Test Coverage

**Issue:** No test files found in the codebase.

**Evidence:**
```bash
find . -name "*.test.*" -o -name "*Test.java" -o -name "*Tests.java"
# No results
```

**Impact:**
- No regression protection
- Difficult to safely refactor
- Violates 80% coverage requirement

**Fix approach:** Add unit tests for:
- Service layer (HomeMusicService, ArtistService)
- Controller layer (ApiMusicController)
- Integration tests for repository layer

### Medium: File-Based Progress Storage

**Issue:** Batch job progress stored in file system.

**Files:**
- `tongrenlu-tool/src/main/java/info/tongrenlu/PlaylistImportJob.java` (line 41):
  ```java
  private static final String PROGRESS_FILE = "E:\\project\\tongrenlu\\tongrenlu-tool\\src\\main\\resources\\data\\playlist_progress.txt";
  ```

**Impact:**
- Not suitable for distributed deployments
- File path is Windows-specific (hardcoded `E:\`)
- Progress lost if server crashes

**Fix approach:** Store progress in database or use distributed cache (Redis).

---

## Known Bugs

### Like/Comment System Non-Functional

**Symptoms:** Album detail page shows 0 for like count and comment count.

**Files:**
- `tongrenlu-dao/src/main/java/info/tongrenlu/service/HomeMusicService.java` (lines 105-106, 131-143)

**Trigger:** Viewing any album detail page.

**Workaround:** None - functionality needs implementation.

---

## Fragile Areas

### External API Integration (CloudMusic)

**Files:**
- `tongrenlu-dao/src/main/java/info/tongrenlu/service/HomeMusicService.java` (multiple methods)
- `tongrenlu-tool/src/main/java/info/tongrenlu/AdminArtistController.java`

**Vulnerabilities:**
- No retry logic for transient failures
- No circuit breaker pattern
- API rate limiting not handled gracefully
- Hardcoded base URL `https://apis.netstart.cn/music`

**Safe modification:** Add Resilience4j for retry/circuit breaker patterns.

### Lazy Loading Configuration

**Files:**
- `tongrenlu-web/src/main/resources/application.properties` (lines 15-16):
  ```properties
  mybatis.configuration.lazy-loading-enabled=true
  mybatis.configuration.aggressive-lazy-loading=false
  ```

**Risk:** Potential N+1 queries if objects are accessed outside transactions. Multiple round trips to database.

---

## Dependencies at Risk

### Spring Boot 3.4.3

**Risk:** Latest stable version. Check for:
- Breaking changes in minor updates
- Security CVEs in transitive dependencies

**Recommended:** Set up Dependabot or Renovate for automated updates.

### MyBatis Plus 3.5.11

**Status:** Stable version but older than latest (3.5.12+).

**Action:** Consider upgrading to latest patch version.

### MySQL Connector 8.2.0

**Status:** Current. Monitor for updates.

---

## Monitoring & Observability

### Missing Observability Stack

**Current state:**
- Basic SLF4J logging only
- Spring Boot Actuator included but not configured
- No metrics collection
- No distributed tracing

**Files:**
- `tongrenlu-web/src/main/java/info/tongrenlu/TongrenluApplication.java`

**Recommendations:**
1. Configure Actuator health endpoint
2. Add Micrometer for metrics
3. Add request logging interceptor
4. Consider adding APM (e.g., Spring Cloud Sleuth)

---

## Backup & Recovery

### No Backup Strategy

**Current state:** No visible backup mechanism.

**Recommendations:**
1. Set up automated MySQL backups (mysqldump or Percona XtraBackup)
2. Configure off-site backup storage
3. Document recovery procedures
4. Test restore process quarterly

---

## Test Coverage Gaps

| Area | What's Not Tested | Risk | Priority |
|------|------------------|------|----------|
| HomeMusicService | All public methods | HIGH - core business logic | HIGH |
| ArtistService | Artist CRUD operations | HIGH - data integrity | HIGH |
| ApiMusicController | All endpoints | MEDIUM - API contracts | HIGH |
| HomeMusicService.getLikeCount | Stub method | LOW | MEDIUM |
| HomeMusicService.getRandomAlbums | RAND() query | MEDIUM - performance | MEDIUM |

---

*Concerns audit: 2026-04-12*
