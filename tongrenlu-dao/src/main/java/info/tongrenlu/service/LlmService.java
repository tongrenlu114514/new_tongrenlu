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
     * 上下文预算（128K 模型）：source 80K + system prompt 10K + output 30K。
     * 80000 字符 ≈ 80K tokens；system prompt 实际很短，10K 仅为预算预留。
     */
    private static final int MAX_SOURCE_CHARS = 80000;
    /** 合并阶段每个草稿片段正文保留的最大字符数，避免合并请求体过大。 */
    private static final int PARTIAL_CONTENT_CHARS = 6000;
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
            @Value("${llm.max-tokens:${LLM_MAX_TOKENS:30000}}") int maxTokens,
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
            你是东方Project同人音乐维基的编辑。根据提供的素材，为同人社团『%s』撰写中文维基条目，只输出 JSON。

            要求：
            1. content 为 markdown 字符串，仅用二级标题（##）分节，不得使用一级标题（#）；依次包含：简介、活动经历、音乐风格三个章节，不要生成"主要作品"章节。
            2. 素材含【THBWiki 社团页文本】【网易云音乐艺人描述】与【本站收录的相关专辑信息】三部分：简介、活动经历以 THBWiki 社团页文本为主，网易云艺人描述可作补充（其措辞可能含宣传或主观评价，须客观转述，不得当作事实照抄）；音乐风格必须依据相关专辑信息（专辑简介、题材、曲目原作等）归纳得出，不得脱离专辑信息凭空臆测。严格基于素材，不得编造；某部分信息不足时相应分节写"暂无资料"。
            3. 页面文本可能是长页面截取的片段，并可能混入其它社团、导航或列表内容；只提取与该社团直接相关的信息，忽略无关段落。
            4. tags 为 3-8 个相关标签的字符串数组：只能从素材中明确提到的关联社团、展会或风格词中选取，宁可少给也不要编造未出现的名称；不得把该社团自身名称列为标签。可补充 1-2 个风格标签（如"电子"、"摇滚"、"和风"）。
            5. 输出格式：{"description": "一两句话概括该词条", "content": "...", "tags": ["...", "..."]}
            """;

    /** album force 生成专用提示词：素材是本站专辑资料 + 可选的网易云推荐评论。 */
    private static final String ALBUM_SYSTEM_PROMPT = """
            你是东方Project同人音乐维基的编辑。根据提供的本站专辑资料（专辑信息、收录曲目）及可选的【网易云推荐评论】，为专辑『%s』撰写中文维基条目，只输出 JSON。

            要求：
            1. content 为 markdown 字符串，仅用二级标题（##）分节，不得使用一级标题（#）；依次包含：专辑信息（艺术家、编号、发行日期等资料中给出的字段）、收录曲目（以资料列出的曲目为准）。不要生成"简介"章节。若素材含【网易云推荐评论】，可追加"听众评价"一节：从评论中提炼对读者有用的信息——反复被提到的风格/氛围、公认出色的曲目、创作或演奏特点、整体口碑倾向；用自己的话归纳，不得逐条罗列或引用评论原文，不得包含昵称、打卡、广告、纯情绪宣泄等无实质内容。
            2. 严格基于提供的素材撰写，不得编造素材中没有的事实；资料信息不足时相应分节写"暂无资料"。评论仅代表部分听众观点，须以"有听众认为"等措辞客观转述，不得当作客观事实陈述。
            3. tags 为 3-8 个相关标签的字符串数组：只能从素材中明确出现的艺术家名、风格词中选取，宁可少给也不要编造；不得把该专辑自身名称列为标签。
            4. 输出格式：{"description": "一两句话概括该词条", "content": "...", "tags": ["...", "..."]}
            """;

    /**
     * 分批提取：素材过大时，每块按此提示词产出"草稿片段"。
     * 只提取本块内与该名称相关的信息，不要求结构完整，供后续合并。
     * 占位符：name / 当前块序号 / 总块数（3 个 %s/%d，由 formatted 传入）。
     */
    private static final String CHUNK_EXTRACT_PROMPT = """
            你是东方Project同人音乐维基的编辑。提供的素材很长，已切成若干块，这是第 %d / %d 块。请根据这一块素材，提取其中与『%s』相关的全部信息，输出 JSON 草稿，只输出 JSON。

            要求：
            1. content 为 markdown 草稿，用二级标题（##）分节，分节名按素材实际内容拟定，不必强求完整结构；只写本块素材中明确出现的内容，不编造、不参考其它块。
            2. description 用一两句话概括本块中与该名称相关的信息。
            3. tags 为本块素材中明确出现的关联名称/风格词字符串数组，宁缺毋滥。
            4. 输出格式：{"description": "...", "content": "...", "tags": ["..."]}
            """;

    /**
     * 合并总结：把各块草稿拼成一份素材，按本类型常规结构产出最终词条。
     * 占位符：name（1 个 %s）。
     */
    private static final String MERGE_SYSTEM_PROMPT = """
            你是东方Project同人音乐维基的编辑。提供的素材是同一词条『%s』的长篇资料分块提取后的草稿集合，请把它们综合成一篇完整、连贯的中文维基条目，只输出 JSON。

            要求：
            1. content 为 markdown 字符串，仅用二级标题（##）分节，不得使用一级标题（#）；按该词条类型的常规结构组织，合并各草稿中重复的内容，保留全部可核实信息，消除块与块之间的重复与矛盾。
            2. 严格基于草稿集合撰写，不得编造素材中没有的事实；信息不足处写"暂无资料"。
            3. description 为一两句话概括最终词条。
            4. tags 为 3-8 个相关标签的字符串数组，汇总各草稿中明确出现的关联名称/风格词并去重；不得把该词条自身名称列为标签。
            5. 输出格式：{"description": "...", "content": "...", "tags": ["...", "..."]}
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
            case "album" -> ALBUM_SYSTEM_PROMPT;
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

        // 素材超大：分块提取草稿 → 合并总结；否则走原有单次调用
        if (sourceText.length() > MAX_SOURCE_CHARS) {
            return generateInBatches(name, type, sourceText);
        }
        return callLlm(name, type, buildRequestBody(name, type, sourceText));
    }

    /**
     * 超大素材的生成：先按窗口切成多块，逐块提取草稿，再把草稿汇总为一次合并调用。
     * 全部块都失败时回退到"仅用首块"的单次调用，避免比不分批更差。
     */
    private Optional<WikiResult> generateInBatches(String name, String type, String sourceText) {
        List<String> chunks = splitSource(sourceText, MAX_SOURCE_CHARS);
        log.info("素材超限（{} 字符），分 {} 块提取草稿（name: {}, type: {}）",
                sourceText.length(), chunks.size(), name, type);

        List<WikiResult> drafts = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            String system = CHUNK_EXTRACT_PROMPT.formatted(i + 1, chunks.size(), name);
            String body = buildBatchRequestBody(system, "素材片段", chunks.get(i));
            Optional<WikiResult> draft = callLlm(name, type, body);
            draft.ifPresent(d -> drafts.add(d));
        }

        if (drafts.isEmpty()) {
            log.warn("所有分块提取均失败，回退到首块单次生成（name: {}, type: {}）", name, type);
            return callLlm(name, type,
                    buildRequestBody(name, type, chunks.get(0)));
        }

        String mergedSource = buildMergedDraftSource(drafts);
        String mergeBody = buildBatchRequestBody(MERGE_SYSTEM_PROMPT.formatted(name), "各分块草稿", mergedSource);
        return callLlm(name, type, mergeBody);
    }

    /** 按上限切块，优先在靠近上限的换行处断开，避免切断行/句。 */
    List<String> splitSource(String source, int maxChars) {
        List<String> chunks = new ArrayList<>();
        int start = 0;
        int len = source.length();
        while (start < len) {
            int end = Math.min(start + maxChars, len);
            if (end < len) {
                int newline = source.lastIndexOf('\n', end);
                // 仅在断点不会太靠前（至少半块）时回退到换行，避免产生极小块
                if (newline > start + maxChars / 2) {
                    end = newline + 1;
                }
            }
            chunks.add(source.substring(start, end));
            start = end;
        }
        return chunks;
    }

    /** 汇总各块草稿为合并调用的输入素材，每块正文按上限截断以控制总量。 */
    private String buildMergedDraftSource(List<WikiResult> drafts) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < drafts.size(); i++) {
            WikiResult d = drafts.get(i);
            sb.append("----- 草稿 ").append(i + 1).append(" / ").append(drafts.size()).append(" -----\n");
            if (StringUtils.hasText(d.description())) {
                sb.append("摘要：").append(d.description()).append('\n');
            }
            String content = d.content() == null ? "" : d.content();
            if (content.length() > PARTIAL_CONTENT_CHARS) {
                content = content.substring(0, PARTIAL_CONTENT_CHARS);
            }
            sb.append("正文：\n").append(content).append('\n');
            if (d.tags() != null && !d.tags().isEmpty()) {
                sb.append("标签：").append(String.join("、", d.tags())).append('\n');
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /** 发起一次 LLM 调用并解析为 WikiResult。 */
    private Optional<WikiResult> callLlm(String name, String type, String body) {
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
        String t = type == null ? "" : type;
        String label = switch (t) {
            case "event" -> "展会名称";
            case "original_game" -> "原作名";
            case "original_track" -> "原曲名";
            case "album" -> "专辑名称";
            default -> "社团名称";
        };
        String sourceLabel = switch (t) {
            case "event" -> "本站收录数据";
            case "original_track" -> "THBWiki 原作页面文本";
            case "album" -> "本站专辑资料";
            case "artist" -> "素材";
            default -> "THBWiki 页面文本";
        };
        String userContent = label + "：" + name + "\n\n" + sourceLabel + "：\n" + sourceText;
        return buildRequestBody(systemPrompt(type).formatted(name), userContent);
    }

    /** 分批提取 / 合并总结用：自定义 system 内容与来源标签。 */
    private String buildBatchRequestBody(String systemContent, String sourceLabel, String sourceText) {
        return buildRequestBody(systemContent, sourceLabel + "：\n" + sourceText);
    }

    /** 组装 chat/completions 请求体（JSON mode）的公共实现。 */
    private String buildRequestBody(String systemContent, String userContent) {
        try {
            var messages = objectMapper.createArrayNode();
            var system = messages.addObject();
            system.put("role", "system");
            system.put("content", systemContent);
            var user = messages.addObject();
            user.put("role", "user");
            user.put("content", userContent);

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
