package info.tongrenlu.enums;

/**
 * m_tag 词条（artist / event）的生成状态。
 * 与 {@link ThbWikiStatus} 区分：后者服务于 m_article 专辑匹配，不能混用。
 */
public enum WikiStatus {
    /** 尚未生成，等待 WikiGenerateJob 处理。 */
    PENDING,
    /** 已生成正文。 */
    GENERATED,
    /** artist 词条在 THBWiki 无匹配页面。 */
    NO_SOURCE,
    /** 抓取或 LLM 生成失败，可通过管理接口 force 重试。 */
    FAILED
}
