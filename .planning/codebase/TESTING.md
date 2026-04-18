# Testing Patterns

**Analysis Date:** 2026-04-12

## Testing Status

**IMPORTANT:** The project currently has limited test coverage. Test directories exist but are empty:

```
tongrenlu-web/src/test/java/
tongrenlu-dao/src/test/java/
tongrenlu-tool/src/test/java/
```

According to `AGENTS.md`, the test strategy is planned but not yet implemented:

> **TODO:** 当前项目缺少完整的测试覆盖

## Test Infrastructure

### Maven Dependencies

The project does not currently include test dependencies in the `pom.xml` files. No explicit testing dependencies (JUnit, Mockito, AssertJ) are declared.

### Module POM Dependencies

**tongrenlu-web/pom.xml:**
- Spring Boot Web starter
- MyBatis Plus
- Lombok
- Hutool
- Guava
- Apache Commons

**tongrenlu-dao/pom.xml:**
- MyBatis Plus Spring Boot 3 Starter (3.5.11)
- MyBatis Plus JSQLParser
- MySQL Connector
- Lombok
- Jackson

**tongrenlu-tool/pom.xml:**
- Spring Boot Web
- Spring Boot WebFlux (for reactive SSE)
- Lombok
- Hutool

### Spring Boot Test Dependencies (Implicit)

Spring Boot Starter Parent (3.4.3) provides:
- JUnit 5
- Mockito
- AssertJ
- Spring Boot Test

However, these are not explicitly declared in the POMs.

## Recommended Test Framework

Based on the project's tech stack (Spring Boot 3.4.3, Java 21), the following test framework is recommended:

### Dependencies to Add

```xml
<!-- In parent pom.xml -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>

<!-- For Spring WebFlux testing (tongrenlu-tool) -->
<dependency>
    <groupId>io.projectreactor</groupId>
    <artifactId>reactor-test</artifactId>
    <scope>test</scope>
</dependency>

<!-- For integration tests with real database -->
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>test</scope>
</dependency>
```

## Test Organization

### Recommended Directory Structure

```
src/test/java/info/tongrenlu/
├── service/           # Unit tests for service layer
├── controller/        # Integration tests for REST controllers
├── mapper/           # Integration tests for MyBatis mappers
└── integration/       # Cross-module integration tests
```

Mirror the `src/main/java` package structure.

### Test File Naming

| Type | Pattern | Example |
|------|---------|---------|
| Unit tests | `{ClassName}Test` | `HomeMusicServiceTest` |
| Integration tests | `{ClassName}IT` | `ArticleMapperIT` |

## Test Patterns

### Service Layer Unit Tests

```java
@ExtendWith(MockitoExtension.class)
class HomeMusicServiceTest {

    @Mock
    private ArticleMapper articleMapper;

    @Mock
    private TrackMapper trackMapper;

    private HomeMusicService homeMusicService;

    @BeforeEach
    void setUp() {
        homeMusicService = new HomeMusicService(
            articleMapper,
            trackMapper,
            tagMapper,
            articleTagMapper,
            artistService
        );
    }

    @Test
    @DisplayName("getAlbumDetail returns null when album not found")
    void getAlbumDetail_albumNotFound_returnsNull() {
        // Given
        when(articleMapper.selectById(999L)).thenReturn(null);

        // When
        AlbumDetailBean result = homeMusicService.getAlbumDetail(999L);

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("searchMusic filters by tag correctly")
    void searchMusic_withTag_filtersResults() {
        // Given
        String tag = "event";
        PageDTO<ArticleBean> page = new PageDTO<>(1, 30);

        // When
        Page<ArticleBean> result = homeMusicService.searchMusic(
            null, 1, 30, "publishDate", tag
        );

        // Then
        assertThat(result).isNotNull();
    }
}
```

### Controller Integration Tests

```java
@WebMvcTest(ApiMusicController.class)
@DisplayName("ApiMusicController Tests")
class ApiMusicControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private HomeMusicService musicService;

    @MockBean
    private ArtistService artistService;

    @Test
    @DisplayName("GET /api/music/search returns paginated results")
    void search_returnsPagedResults() throws Exception {
        // Given
        Page<ArticleBean> mockPage = new Page<>(1, 30);
        when(musicService.searchMusic(any(), anyInt(), anyInt(), any(), any()))
            .thenReturn(mockPage);

        // When/Then
        mockMvc.perform(get("/api/music/search")
                .param("pageNumber", "1")
                .param("pageSize", "30"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.records").isArray());
    }

    @Test
    @DisplayName("GET /api/music/track returns 404 when track not found")
    void getTrackById_notFound_returns404() throws Exception {
        // Given
        when(musicService.getTrackById(999L)).thenReturn(null);

        // When/Then
        mockMvc.perform(get("/api/music/track")
                .param("id", "999"))
            .andExpect(status().isNotFound());
    }
}
```

### Mapper Integration Tests

For MyBatis Plus mappers, integration tests with H2 database:

```java
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ArticleMapperTest {

    @Autowired
    private ArticleMapper articleMapper;

    @Test
    @DisplayName("selectById returns correct article")
    void selectById_existingArticle_returnsArticle() {
        // Given
        ArticleBean article = new ArticleBean();
        article.setTitle("Test Album");
        articleMapper.insert(article);

        // When
        ArticleBean result = articleMapper.selectById(article.getId());

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getTitle()).isEqualTo("Test Album");
    }
}
```

### Test Configuration

Create `src/test/resources/application-test.properties`:

```properties
# Test profile configuration
spring.datasource.url=jdbc:h2:mem:testdb;MODE=MySQL
spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=

mybatis.mapper-locations=classpath:info/tongrenlu/mapper/*.xml
mybatis.type-aliases-package=info.tongrenlu.domain
```

## Coverage Requirements

According to project guidelines:

- **Target:** 80%+ line coverage
- **Focus areas:**
  - Service layer (business logic)
  - Controller layer (API endpoints)
  - Complex utility methods

### Run Coverage

```bash
# Generate coverage report
mvn test jacoco:report

# View report
open target/site/jacoco/index.html
```

## CI/CD Testing Integration

### Maven Test Command

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=HomeMusicServiceTest

# Run with coverage
mvn test verify jacoco:report

# Skip tests
mvn clean package -DskipTests
```

### Recommended CI Pipeline

```yaml
# .github/workflows/test.yml (if using GitHub Actions)
name: Test

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
      - name: Run tests
        run: mvn test
      - name: Upload coverage
        uses: codecov/codecov-action@v3
```

## Mocking Patterns

### Mockito Usage

```java
@ExtendWith(MockitoExtension.class)
class ExampleTest {

    @Mock
    private ArticleMapper articleMapper;

    @InjectMocks
    private HomeMusicService service;

    @Test
    void testMethod() {
        // Configure mock behavior
        when(articleMapper.selectById(1L)).thenReturn(mockArticle);

        // Execute
        ArticleBean result = service.getAlbumDetail(1L);

        // Verify
        assertThat(result).isNotNull();
        verify(articleMapper).selectById(1L);
    }
}
```

### MockBean for Spring Context

```java
@SpringBootTest
class IntegrationTest {

    @MockBean
    private ExternalMusicApiClient externalApiClient;

    @Test
    void testWithMockedExternalService() {
        when(externalApiClient.getAlbumDetails(anyLong()))
            .thenReturn(mockAlbumResponse);

        // Test logic that calls externalApiClient
    }
}
```

## Test Data Fixtures

### Factory Pattern

```java
class TestFixtures {

    public static ArticleBean createArticleBean() {
        ArticleBean bean = new ArticleBean();
        bean.setTitle("Test Album");
        bean.setPublishFlg("1");
        bean.setAccessCount(0);
        return bean;
    }

    public static AlbumDetailBean createAlbumDetailBean(Long id) {
        AlbumDetailBean detail = new AlbumDetailBean();
        detail.setId(id);
        detail.setTitle("Test Album Detail");
        return detail;
    }

    public static List<TrackBean> createTrackBeans(int count) {
        return IntStream.range(1, count + 1)
            .mapToObj(i -> {
                TrackBean track = new TrackBean();
                track.setName("Track " + i);
                track.setTrackNumber(i);
                return track;
            })
            .toList();
    }
}
```

## Async Testing (WebFlux)

For `PlaylistImportJob` which uses Spring WebFlux:

```java
@WebFluxTest(PlaylistImportJob.class)
class PlaylistImportJobTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private HomeMusicService homeMusicService;

    @MockBean
    private ArticleService articleService;

    @Test
    @DisplayName("SSE endpoint returns ServerSentEvents")
    void importPlaylist_returnsSSEStream() {
        when(homeMusicService.getAllPlaylistAlbumIds(anyLong()))
            .thenReturn(List.of(1L, 2L, 3L));

        webTestClient.get()
            .uri("/playlist/import?playlistId=123")
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus().isOk()
            .expectBodyList(ServerSentEvent.class);
    }
}
```

## Known Testing Gaps

Based on codebase analysis:

| Area | Status | Priority |
|------|--------|----------|
| Service layer tests | Not implemented | High |
| Controller tests | Not implemented | High |
| Mapper tests | Not implemented | Medium |
| Integration tests | Not implemented | Medium |
| E2E tests | Not used | Low |

## Testing Checklist

Before implementing tests, ensure:

- [ ] Add test dependencies to POM files
- [ ] Configure H2 test database
- [ ] Create test application properties
- [ ] Set up JaCoCo coverage plugin
- [ ] Write unit tests for `HomeMusicService`
- [ ] Write integration tests for `ApiMusicController`
- [ ] Write tests for utility methods
- [ ] Verify 80%+ coverage for service layer

---

*Testing analysis: 2026-04-12*
