package info.tongrenlu.support;

import java.text.Normalizer;

/**
 * CJK-aware text normalizer for matching THBWiki-extracted names against the local database.
 * <p>
 * Applies full-width → half-width conversion, Unicode NFKC normalization, and whitespace
 * collapse. Two strings that should be considered the same artist/track name will produce
 * equal {@link #normalizeForComparison(String)} results.
 */
public final class TextNormalizer {

    private TextNormalizer() {
        // utility class — prevent instantiation
    }

    /**
     * Returns a cleaned, display-safe version of the input string.
     * Applies whitespace trim/collapse but does NOT alter characters used for
     * visual presentation.
     *
     * @param input the raw string (may be null)
     * @return trimmed and whitespace-collapsed string, or {@code null} if input is null
     */
    public static String normalize(final String input) {
        if (input == null) {
            return null;
        }
        // Collapse both standard whitespace AND ideographic space (U+3000) to a single
        // regular space, then trim the result. This ensures consistent spacing for display
        // and also for comparison purposes.
        String s = input.replace('\u3000', ' '); // ideographic space → regular space
        return s.trim().replaceAll("[ \\t\\n\\r\\f\\v]+", " ");
    }

    /**
     * Returns a comparison-key version of the input string.
     * <p>
     * Applied transformations (in order):
     * <ol>
     *   <li>Full-width ASCII → half-width ASCII (digits ０１２３４５６７８９, letters ＡＢＣＤ…)
     *   <li>Unicode NFKC normalization (composes compatibility characters, e.g. ② → 2,
     *       ﾃｷ → テ, ﹏ → underscore equivalents)</li>
     *   <li>Trim + collapse internal whitespace</li>
     *   <li>Lowercase (ASCII only — CJK characters are unaffected by case-folding)</li>
     * </ol>
     *
     * @param input the raw string (may be null)
     * @return a normalized, lowercase comparison key, or {@code null} if input is null
     */
    public static String normalizeForComparison(final String input) {
        if (input == null) {
            return null;
        }

        // 1. Full-width → half-width ASCII
        String s = convertFullWidthAsciiToHalfWidth(input);

        // 2. NFKC normalization
        s = Normalizer.normalize(s, Normalizer.Form.NFKC);

        // 3. Trim + collapse whitespace
        s = s.trim().replaceAll("\\s+", " ");

        // 4. Lowercase ASCII letters only
        s = s.toLowerCase(java.util.Locale.ENGLISH);

        return s;
    }

    /**
     * Converts full-width ASCII characters to their half-width equivalents.
     * Covers:
     * <ul>
     *   <li>Full-width digits: U+FF10–U+FF19 → U+0030–U+0039</li>
     *   <li>Full-width uppercase letters: U+FF21–U+FF3A → U+0041–U+005A</li>
     *   <li>Full-width lowercase letters: U+FF41–U+FF5A → U+0061–U+007A</li>
     *   <li>Full-width space: U+3000 → U+0020</li>
     * </ul>
     *
     * @param input the input string
     * @return the string with full-width ASCII characters replaced by half-width equivalents
     */
    static String convertFullWidthAsciiToHalfWidth(final String input) {
        if (input == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c >= 0xFF10 && c <= 0xFF19) {
                // Full-width digit → half-width digit
                sb.append((char) (c - 0xFF10 + '0'));
            } else if (c >= 0xFF21 && c <= 0xFF3A) {
                // Full-width uppercase letter → half-width uppercase
                sb.append((char) (c - 0xFF21 + 'A'));
            } else if (c >= 0xFF41 && c <= 0xFF5A) {
                // Full-width lowercase letter → half-width lowercase
                sb.append((char) (c - 0xFF41 + 'a'));
            } else if (c == 0x3000) {
                // Full-width space → half-width space
                sb.append(' ');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
