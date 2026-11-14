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
    private static final int MAX_SOURCE_CHARS = 6000;
    private static final int MAX_RETRIES = 2;
    private static final long[] BACKOFF_MS = {2000, 4000};

    private final String apiBase;
    private final String apiKey;
    private final String model;
    private final ObjectMapper objectMapper;

    public LlmService(@Value("${llm.api-base:${LLM_API_BASE:https://api.deepseek.com}}") String apiBase,
            @Value("${llm.api-key:${LLM_API_KEY:}}") String apiKey,
            @Value("${llm.model:${LLM_MODEL:deepseek-chat}}") String model,
            ObjectMapper objectMapper) {
        this.apiBase = apiBase;
        this.apiKey = apiKey;
        this.model = model;
        this.objectMapper = objectMapper;
    }

    /**
     * LLM 生成的 wiki 条目（artist / event 通用）。
     *
     * @param content markdown 正文
     * @param tags    互联标签（相关社团 / 展会 / 风格标签）
     */
    public record WikiResult(String content, List<String> tags) {
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
            4. 输出格式：{"content": "...", "tags": ["...", "..."]}
            """;

    private static final String EVENT_SYSTEM_PROMPT = """
            你是东方Project同人展会维基的编辑。根据提供的本站收录数据，为展会『%s』撰写中文维基条目，只输出 JSON。

            要求：
            1. content 为 markdown 字符串，使用二级标题（##）分节，依次包含：展会简介、收录规模、代表作品（从提供的专辑列表中挑选 3-5 张并简要说明）、活跃社团（仅当能从数据中推断时列举）。
            2. 严格基于提供的数据撰写，不得编造举办日期、地点、主办方、届次等未提供的事实；信息不足时相应分节写"暂无资料"。
            3. tags 为 3-8 个相关标签的字符串数组：优先给出同系列展会名（如"Comic Market"、"例大祭"、"M3"、"东方红楼梦"），可补充 1-2 个类型词。
            4. 输出格式：{"content": "...", "tags": ["...", "..."]}
            """;

    /**
     * 按词条类型选择提示词。未知类型回落到 artist 提示词。
     */
    private String systemPrompt(String type) {
        return "event".equals(type) ? EVENT_SYSTEM_PROMPT : ARTIST_SYSTEM_PROMPT;
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
            String label = "event".equals(type) ? "展会名称" : "社团名称";
            String sourceLabel = "event".equals(type) ? "本站收录数据" : "THBWiki 页面文本";
            user.put("content", label + "：" + name + "\n\n" + sourceLabel + "：\n" + sourceText);

            var root = objectMapper.createObjectNode();
            root.put("model", model);
            root.set("messages", messages);
            root.putObject("response_format").put("type", "json_object");
            root.put("temperature", 0.3);
            root.put("max_tokens", 4000);
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
            return Optional.of(new WikiResult(markdown, tags));
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
