package info.tongrenlu.enums;

/**
 * Status of THBWiki matching for an m_article row.
 * Decoupled from {@code thb_wiki_url} so the URL field only stores real URLs.
 */
public enum ThbWikiStatus {
    /** Not yet processed by OriginalUpdateJob. */
    PENDING,
    /** Matched successfully; thb_wiki_url points to a THBWiki album page. */
    MATCHED,
    /** THBWiki opensearch returned no results for this album. */
    NOT_FOUND,
    /** THBWiki detail page fetch failed (network / parse error). */
    FETCH_FAILED
}
