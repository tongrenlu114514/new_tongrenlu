package info.tongrenlu.service;

import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service that derives a best-effort THBWiki URL from m_track.original text.
 *
 * Replaces the previous SQL-only migration (sql/20260423/m_track_original_url_backfill.sql)
 * with Java-side regex parsing so we can handle the full variety of prefix/separator
 * variants found in production data.
 *
 * The {@link OriginalUrlBackfillService.BackfillResult} return value gives callers
 * visibility into which step matched (useful for stats / admin UI), and {@code null}
 * if no rule matched (caller should leave original_url untouched).
 *
 * Rules (first matching wins — most specific to most general):
 *   1. Strip leading prefixes (multiple, in priority order)
 *   2. If 'X (from Y)' present, keep part before ' (from '
 *   3. If a list separator present, take the FIRST segment
 *   4. Otherwise use the whole text as the song name
 *   5. Spaces become '_' (MediaWiki URL convention); other chars kept as-is
 *      and percent-encoded at render time by the frontend.
 */
@Service
public class OriginalUrlBackfillService {

    private static final String THBWIKI_BASE_URL = "https://thbwiki.cc/";

    /**
     * Prefix patterns, evaluated in order. Each pattern consumes the prefix marker
     * at the start of the text (NOT the song name) and the rest is passed on.
     */
    private static final PrefixRule[] PREFIX_RULES = new PrefixRule[] {
            // ----- Longest / most specific matches first -----
            // 原曲、新詩:xxx (mixed kana, FW or HW comma + 原詩)
            new PrefixRule(Pattern.compile("^原曲[,、]原詩[:：]\\s*"), "mixed_prefix"),
            // 原曲・原詩:xxx (middle dot + 原詩)
            new PrefixRule(Pattern.compile("^原曲・原詩[:：]\\s*"), "mixed_prefix_dot"),
            // Original : xxx or Original: xxx or Original xxx
            new PrefixRule(Pattern.compile("^(?i)original\\s*[:\\s]?\\s*"), "english_prefix"),
            // 原曲：xxx (full-width colon)
            new PrefixRule(Pattern.compile("^原曲：\\s*"), "fw_prefix"),
            // 原曲:xxx or 原曲 xxx or 原曲\txxx (half-width colon / space / tab)
            new PrefixRule(Pattern.compile("^原曲\\s*[:\\s\\t]\\s*"), "hw_prefix"),
            // 原曲, xxx (comma — HW or FW)
            new PrefixRule(Pattern.compile("^原曲[,，]\\s*"), "hw_prefix_comma"),
            // 原曲. xxx (ASCII dot)
            new PrefixRule(Pattern.compile("^原曲\\.\\s*"), "hw_prefix_dot"),
            // 原曲·xxx (Japanese middle dot alone)
            new PrefixRule(Pattern.compile("^原曲[・]\\s*"), "hw_prefix_dot_kana"),
            // 原曲 (bare, no separator) — fallback
            new PrefixRule(Pattern.compile("^原曲(?=\\S)"), "hw_prefix_bare"),
    };

    /**
     * Bracket/quote wrappers that surround a song name when preceded by '原曲' or 'Original'.
     * Each rule has the form "open:close" and applies AFTER a prefix strip. The inner
     * content is preserved (it IS the song name).
     */
    private static final char[][] BRACKET_PAIRS = new char[][] {
            {'《', '》'},
            {'「', '」'},
            {'『', '』'},
            {'(', ')'},
            {'（', '）'},
            {'[', ']'},
    };

    /**
     * List separators used to split a multi-song list into individual song names.
     * We take the FIRST segment when one of these is present.
     */
    private static final Pattern LIST_SEPARATOR = Pattern.compile(
            "\\s*(?:、|,|，|／|/)\\s*"
    );

    /**
     * "from X" parenthetical: X (from Y) → take part before ' (from '
     */
    private static final Pattern FROM_PATTERN = Pattern.compile("\\s*[(（]from\\s+[^)）]*[)）]\\s*$");

    /**
     * Derive a THBWiki URL from the given original text.
     *
     * @param original the raw original text (may be null/empty)
     * @return a {@link BackfillResult} with the URL and matched step, or {@code null}
     *         if no rule matched (caller should leave the row alone).
     */
    public BackfillResult deriveUrl(String original) {
        if (original == null || original.isBlank()) {
            return null;
        }

        String text = original.strip();
        if (text.isEmpty()) {
            return null;
        }

        // Strip "X (from Y)" suffix first
        Matcher fromM = FROM_PATTERN.matcher(text);
        if (fromM.find()) {
            text = text.substring(0, fromM.start()).strip();
        }
        if (text.isEmpty()) {
            return null;
        }

        // Strip leading prefix
        String prefixMatched = null;
        for (PrefixRule rule : PREFIX_RULES) {
            Matcher m = rule.pattern.matcher(text);
            if (m.find()) {
                text = m.replaceFirst("").stripLeading();
                prefixMatched = rule.name;
                break;
            }
        }
        if (text.isEmpty()) {
            return null;
        }

        // Strip surrounding bracket/quote wrappers (preserving inner content)
        // e.g. 《ネクロファンタジア》 → ネクロファンタジア
        //      「不思議なお祓い棒」 → 不思議なお祓い棒
        // If brackets are stripped, we do NOT also apply LIST_SEPARATOR — the content
        // inside a 《》 pair is treated as the song title verbatim.
        boolean strippedBracket = false;
        for (char[] pair : BRACKET_PAIRS) {
            if (text.length() >= 2
                    && text.charAt(0) == pair[0]
                    && text.charAt(text.length() - 1) == pair[1]) {
                text = text.substring(1, text.length() - 1).strip();
                strippedBracket = true;
                break;
            }
        }
        if (text.isEmpty()) {
            return null;
        }

        // Strip trailing "原曲" or "原曲：" or similar leftover prefix marker
        // (handles nested-prefix cases like '原曲：原曲:幽霊楽団　～ Phantom Ensemble')
        while (true) {
            String stripped = stripRepeatedPrefix(text);
            if (stripped.equals(text)) break;
            text = stripped.strip();
            if (text.isEmpty()) return null;
        }

        // Take first segment if list separator present AND brackets were not stripped.
        // (When brackets were stripped, the user marked the entire inner content as the
        // song name, so we trust it as-is.)
        String primary = text;
        if (!strippedBracket) {
            String[] segments = LIST_SEPARATOR.split(text, 2);
            primary = segments[0].strip();
        }
        if (primary.isEmpty()) {
            return null;
        }

        // MediaWiki URL convention: spaces become underscores. We replace both ASCII
        // space (U+0020) and full-width space (U+3000) so that '翔　～' becomes '翔_～'.
        String pageName = primary
                .replace(' ', '_')
                .replace('　', '_');
        String url = THBWIKI_BASE_URL + pageName;
        return new BackfillResult(url, prefixMatched != null ? prefixMatched : "plain");
    }

    /**
     * Strip leading 原曲... style prefix in the middle of the text (for nested cases).
     * Returns the input unchanged if no further prefix matches.
     */
    private String stripRepeatedPrefix(String text) {
        for (PrefixRule rule : PREFIX_RULES) {
            Matcher m = rule.pattern.matcher(text);
            if (m.find()) {
                return m.replaceFirst("").stripLeading();
            }
        }
        return text;
    }

    /**
     * A prefix rule: a regex that consumes the leading prefix and a name for diagnostics.
     */
    private record PrefixRule(Pattern pattern, String name) {}

    /**
     * Result of a successful URL derivation. {@code step} records which rule matched so
     * callers can build stats / admin UI.
     */
    public record BackfillResult(String url, String step) {}
}
