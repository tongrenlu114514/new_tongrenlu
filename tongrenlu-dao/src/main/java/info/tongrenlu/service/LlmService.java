package info.tongrenlu.service;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * LLM 客户端：调用 OpenAI 兼容的 /chat/completions 接口。
 * 配置（环境变量）：
 *   LLM_API_BASE — 接口基址，默认 https://api.deepseek.com
 *   LLM_API_KEY  — API 密钥，未配置时服务不可用（generateWiki 返回 empty）
 *   LLM_MODEL    — 模型名，默认 deepseek-chat
 */
@Service
@Slf4j
public class LlmService {

    private static final int TIMEOUT_MS = 120_000;
    /**
     * 素材保险上限（超出截断）。列表页类素材（原作/原曲）已由 WikiService 按词条名
     * 截取窗口并组合（可超过单页窗口），因此这里的值只作兜底，实际大小由调用方控制。
     * 模型上下文 128K，30000 字符 ≈ 30K tokens，远低于上限。
     */
    private static final int MAX_SOURCE_CHARS = 30000;
    private static final int MAX_RETRIES = 2;
    private static final long[] BACKOFF_MS = {2000, 4000};

    private final String apiBase;
    private final String apiKey;
    private final String model;
    private final int maxTokens;
    private final ObjectMapper objectMapper;

    public LlmService(@Value("${llm.api-base:${LLM_API_BASE:https://api.deepseek.com}}") String apiBase,
            @Value("${llm.api-key:${LLM_API_KEY:}}") String apiKey,
            @Value("${llm.model:${LLM_MODEL:deepseek-chat}}") String model,
            @Value("${llm.max-tokens:${LLM_MAX_TOKENS:8192}}") int maxTokens,
            ObjectMapper objectMapper) {
        this.apiBase = apiBase;
        this.apiKey = apiKey;
        this.model = model;
        this.maxTokens = maxTokens;
        this.objectMapper = objectMapper;
    }

    /**
     * LLM 生成的 wiki 条目（artist / event / 原作 / 原曲通用）。
     *
     * @param description OKF frontmatter description：一两句话摘要（未输出时为 null）
     * @param content     markdown 正文
     * @param tags        互联标签（相关社团 / 展会 / 风格标签）
     */
    public record WikiResult(String description, String content, List<String> tags) {
    }

    /**
     * 兼容旧调用方：艺术家 wiki 结果。
     */
    public record ArtistWiki(String content, List<String> tags) {
    }

    private static final String ARTIST_SYSTEM_PROMPT = """
            你是东方Project同人音乐维基的编辑。根据提供的 THBWiki 页面文本，为同人社团『%s』撰写中文维基条目，只输出 JSON。

            要求：
            1. content 为 markdown 字符串，使用二级标题（##）分节，依次包含：简介、活动经历、音乐风格、主要作品（列出页面中提到的 3-5 张专辑或代表作品）。
            2. 严格基于页面文本撰写，不得编造页面中没有的事实；页面文本信息不足时相应分节写"暂无资料"。
            3. tags 为 3-8 个相关标签的字符串数组：优先列出与该社团活跃时期相近、风格相似的其他真实同人社团名，可补充 1-2 个风格标签（如"电子"、"摇滚"、"和风"）。
            4. 输出格式：{"description": "一两句话概括该词条", "content": "...", "tags": ["...", "..."]}
            """;

    private static final String EVENT_SYSTEM_PROMPT = """
            你是东方Project同人展会维基的编辑。根据提供的本站收录数据，为展会『%s』撰写中文维基条目，只输出 JSON。

            要求：
            1. content 为 markdown 字符串，使用二级标题（##）分节，依次包含：展会简介、收录规模、代表作品（从提供的专辑列表中挑选 3-5 张并简要说明）、活跃社团（仅当能从数据中推断时列举）。
            2. 严格基于提供的数据撰写，不得编造举办日期、地点、主办方、届次等未提供的事实；信息不足时相应分节写"暂无资料"。
            3. tags 为 3-8 个相关标签的字符串数组：优先给出同系列展会名（如"Comic Market"、"例大祭"、"M3"、"东方红楼梦"），可补充 1-2 个类型词。
            4. 输出格式：{"description": "一两句话概括该词条", "content": "...", "tags": ["...", "..."]}
            """;

    private static final String ORIGINAL_GAME_SYSTEM_PROMPT = """
            你是东方Project同人音乐维基的编辑。提供的素材是 THBWiki「官方游戏」列表页的文本片段，请为原作『%s』撰写中文维基条目，只输出 JSON。

            要求：
            1. content 为 markdown 字符串，使用二级标题（##）分节，依次包含：作品信息（TH 编号、名称、类别、贩售日期、最新版本等素材中提到的信息）、作品简介。
            2. 只提取素材中与该原作相关条目的内容，不要混入列表中其它作品的条目；严格基于素材撰写，不得编造；素材中没有该作品的信息时相应分节写"暂无资料"。
            3. tags 为 3-8 个相关标签的字符串数组：可给出该原作名、系列名（如"东方Project"）、风格词。
            4. 输出格式：{"description": "一两句话概括该词条", "content": "...", "tags": ["...", "..."]}
            """;

    private static final String ORIGINAL_TRACK_SYSTEM_PROMPT = """
            你是东方Project同人音乐维基的编辑。提供的素材包含：【THBWiki 曲目词条页】是该曲在 THBWiki 的词条正文（所属作品、BPM、乐曲说明等）；【原曲列表（片段）】与【官方音乐CD曲目列表（片段）】是索引页片段，提供曲号与归属信息。请为原曲『%s』撰写中文维基条目，只输出 JSON。

            要求：
            1. content 为 markdown 字符串，使用二级标题（##）分节，依次包含：曲目信息（所属作品/CD、曲号等）、乐曲说明（词条页正文中的介绍或评论）。
            2. 以曲目词条页内容为主撰写，索引片段仅用于补充归属与曲号；不要混入其它曲目或作品的条目；严格基于素材撰写，不得编造；素材中没有该曲信息时相应分节写"暂无资料"。
            3. tags 为 3-8 个相关标签的字符串数组：可给出所属作品/CD 名、风格词。
            4. 输出格式：{"description": "一两句话概括该词条", "content": "...", "tags": ["...", "..."]}
            """;

    /**
     * 按词条类型选择提示词。未知类型回落到 artist 提示词。
     * OKF 表的 game_track / cd_track 与 m_tag 的 original_track 共用原曲提示词。
     */
    private String systemPrompt(String type) {
        String t = type == null ? "" : type;
        return switch (t) {
            case "event" -> EVENT_SYSTEM_PROMPT;
            case "original_game" -> ORIGINAL_GAME_SYSTEM_PROMPT;
            case "original_track", "game_track", "cd_track" -> ORIGINAL_TRACK_SYSTEM_PROMPT;
            default -> ARTIST_SYSTEM_PROMPT;
        };
    }

    /**
     * 按词条类型生成 wiki 内容与互联标签。
     *
     * @param name       词条名称（社团名 / 展会名）
     * @param type       artist / event
     * @param sourceText 素材（THBWiki 页面纯文本 / 本站收录数据）
     * @return 生成结果；LLM 未配置或调用/解析失败时返回 empty
     */
    public Optional<WikiResult> generateWiki(String name, String type, String sourceText) {
        if (!StringUtils.hasText(apiKey)) {
            log.warn("LLM_API_KEY 未配置，跳过 wiki 内容生成（name: {}, type: {}）", name, type);
            return Optional.empty();
        }
        if (!StringUtils.hasText(sourceText)) {
            log.warn("源文本为空，跳过 wiki 内容生成（name: {}, type: {}）", name, type);
            return Optional.empty();
        }

        String truncatedSource = sourceText.length() > MAX_SOURCE_CHARS
                ? sourceText.substring(0, MAX_SOURCE_CHARS)
                : sourceText;

        String body = buildRequestBody(name, type, truncatedSource);
        String response = postChatCompletions(body);
        if (response == null) {
            return Optional.empty();
        }

        return parseResponse(response, name);
    }

    /**
     * 为艺术家生成 wiki 内容与互联标签（薄委托，保留旧调用签名）。
     */
    public Optional<ArtistWiki> generateArtistWiki(String artistName, String sourceText) {
        return generateWiki(artistName, "artist", sourceText)
                .map(r -> new ArtistWiki(r.content(), r.tags()));
    }

    /**
     * 组装 chat/completions 请求体（JSON mode）。
     */
    String buildRequestBody(String name, String type, String sourceText) {
        try {
            var messages = objectMapper.createArrayNode();
            var system = messages.addObject();
            system.put("role", "system");
            system.put("content", systemPrompt(type).formatted(name));
            var user = messages.addObject();
            user.put("role", "user");
            String t = type == null ? "" : type;
            String label = switch (t) {
                case "event" -> "展会名称";
                case "original_game" -> "原作名";
                case "original_track" -> "原曲名";
                default -> "社团名称";
            };
            String sourceLabel = switch (t) {
                case "event" -> "本站收录数据";
                case "original_track" -> "THBWiki 原作页面文本";
                default -> "THBWiki 页面文本";
            };
            user.put("content", label + "：" + name + "\n\n" + sourceLabel + "：\n" + sourceText);

            var root = objectMapper.createObjectNode();
            root.put("model", model);
            root.set("messages", messages);
            root.putObject("response_format").put("type", "json_object");
            root.put("temperature", 0.3);
            root.put("max_tokens", maxTokens);
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new RuntimeException("构建 LLM 请求体失败", e);
        }
    }

    /**
     * POST /chat/completions，429/5xx 时指数退避重试。
     *
     * @return 响应正文；所有尝试失败时返回 null
     */
    String postChatCompletions(String body) {
        String url = resolveEndpoint();
        Exception lastCause = null;

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try (HttpResponse response = HttpRequest.post(url)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .body(body)
                    .timeout(TIMEOUT_MS)
                    .execute()) {

                int status = response.getStatus();
                if (status == 200) {
                    return response.body();
                }
                if (status == 429 || status >= 500) {
                    log.warn("LLM 接口返回 {}（尝试 {}/{}）", status, attempt + 1, MAX_RETRIES + 1);
                    if (attempt < MAX_RETRIES) {
                        sleepQuietly(BACKOFF_MS[attempt]);
                    }
                } else {
                    log.error("LLM 接口返回 {}，不重试：{}", status, response.body());
                    return null;
                }
            } catch (Exception e) {
                lastCause = e;
                log.warn("LLM 请求异常（尝试 {}/{}）：{}", attempt + 1, MAX_RETRIES + 1, e.getMessage());
                if (attempt < MAX_RETRIES) {
                    sleepQuietly(BACKOFF_MS[attempt]);
                }
            }
        }
        log.error("LLM 请求最终失败: url={}", url, lastCause);
        return null;
    }

    /**
     * 拼接接口地址：base 不含 /v1 时自动补全为 {base}/v1/chat/completions。
     */
    String resolveEndpoint() {
        String base = apiBase.replaceAll("/+$", "");
        if (base.endsWith("/chat/completions")) {
            return base;
        }
        if (!base.endsWith("/v1")) {
            base = base + "/v1";
        }
        return base + "/chat/completions";
    }

    /**
     * 解析响应中的 choices[0].message.content（JSON 字符串）为 WikiResult。
     */
    Optional<WikiResult> parseResponse(String responseBody, String name) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String content = root.path("choices").path(0).path("message").path("content").asText(null);
            if (!StringUtils.hasText(content)) {
                log.warn("LLM 响应无正文（name: {}）", name);
                return Optional.empty();
            }

            JsonNode wiki = objectMapper.readTree(content);
            String markdown = wiki.path("content").asText(null);
            if (!StringUtils.hasText(markdown)) {
                log.warn("LLM 输出缺少 content 字段（name: {}）", name);
                return Optional.empty();
            }

            List<String> tags = new ArrayList<>();
            JsonNode tagsNode = wiki.path("tags");
            if (tagsNode.isArray()) {
                tagsNode.forEach(tag -> {
                    String value = tag.asText("").trim();
                    if (!value.isEmpty()) {
                        tags.add(value);
                    }
                });
            }
            // OKF frontmatter description：一两句话摘要，未输出时为 null
            String description = wiki.path("description").asText(null);
            if (StringUtils.hasText(description)) {
                description = description.strip();
                if (description.length() > 512) {
                    description = description.substring(0, 512);
                }
            }
            return Optional.of(new WikiResult(description, markdown, tags));
        } catch (Exception e) {
            log.error("解析 LLM 响应失败（name: {}）", name, e);
            return Optional.empty();
        }
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("LLM 重试退避被中断");
        }
    }
}
