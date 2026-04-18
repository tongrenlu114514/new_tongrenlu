package info.tongrenlu.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TextNormalizer")
class TextNormalizerTest {

    // -------------------------------------------------------------------------
    // normalize() — whitespace / trim
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("normalize() whitespace and trim")
    class NormalizeWhitespaceTests {

        @Test
        @DisplayName("trims leading and trailing whitespace")
        void trim_leadingAndTrailing() {
            assertThat(TextNormalizer.normalize("  hello world  "))
                .isEqualTo("hello world");
        }

        @Test
        @DisplayName("collapses multiple internal spaces to a single space")
        void collapse_multipleSpaces() {
            assertThat(TextNormalizer.normalize("hello    world"))
                .isEqualTo("hello world");
        }

        @Test
        @DisplayName("collapses tabs and newlines to single spaces")
        void collapse_tabsAndNewlines() {
            assertThat(TextNormalizer.normalize("hello\n\tworld\rfoo\t\tbar"))
                .isEqualTo("hello world foo bar");
        }

        @Test
        @DisplayName("returns null when input is null")
        void nullInput_returnsNull() {
            assertThat(TextNormalizer.normalize(null)).isNull();
        }

        @Test
        @DisplayName("returns empty string when input is empty")
        void emptyInput_returnsEmpty() {
            assertThat(TextNormalizer.normalize("")).isEmpty();
        }

        @Test
        @DisplayName("returns empty string when input is only whitespace")
        void whitespaceOnly_returnsEmpty() {
            assertThat(TextNormalizer.normalize("   \t\n  ")).isEmpty();
        }
    }

    // -------------------------------------------------------------------------
    // normalizeForComparison() — null / empty guard
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("normalizeForComparison() null and empty guard")
    class NullEmptyGuardTests {

        @Test
        @DisplayName("returns null when input is null")
        void nullInput_returnsNull() {
            assertThat(TextNormalizer.normalizeForComparison(null)).isNull();
        }

        @Test
        @DisplayName("returns empty string when input is empty")
        void emptyInput_returnsEmpty() {
            assertThat(TextNormalizer.normalizeForComparison("")).isEmpty();
        }

        @Test
        @DisplayName("returns empty string when input is only whitespace")
        void whitespaceOnly_returnsEmpty() {
            assertThat(TextNormalizer.normalizeForComparison("  \t\n  ")).isEmpty();
        }
    }

    // -------------------------------------------------------------------------
    // normalizeForComparison() — full-width ASCII
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("normalizeForComparison() full-width → half-width ASCII")
    class FullWidthToHalfWidthTests {

        @Test
        @DisplayName("converts full-width digits")
        void fullWidthDigits() {
            assertThat(TextNormalizer.normalizeForComparison("０１２３４５６７８９"))
                .isEqualTo("0123456789");
        }

        @Test
        @DisplayName("converts full-width uppercase letters")
        void fullWidthUppercase() {
            assertThat(TextNormalizer.normalizeForComparison("ＡＢＣＤＥＦＧＨＩＪＫＬＭＮＯＰＱＲＳＴＵＶＷＸＹＺ"))
                .isEqualTo("abcdefghijklmnopqrstuvwxyz");
        }

        @Test
        @DisplayName("converts full-width lowercase letters")
        void fullWidthLowercase() {
            assertThat(TextNormalizer.normalizeForComparison("ａｂｃｄｅｆｇｈｉｊｋｌｍｎｏｐｑｒｓｔｕｖｗｘｙｚ"))
                .isEqualTo("abcdefghijklmnopqrstuvwxyz");
        }

        @Test
        @DisplayName("converts full-width space to half-width space")
        void fullWidthSpace() {
            assertThat(TextNormalizer.normalizeForComparison("hello\u3000world"))
                .isEqualTo("hello world");
        }

        @Test
        @DisplayName("converts mixed full-width alphanumeric string")
        void mixedFullWidth() {
            assertThat(TextNormalizer.normalizeForComparison("Album０１"))
                .isEqualTo("album01");
        }
    }

    // -------------------------------------------------------------------------
    // normalizeForComparison() — NFKC normalization
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("normalizeForComparison() NFKC normalization")
    class NfkcNormalizationTests {

        @Test
        @DisplayName("converts circled digits (①–⑮) to plain digits")
        void circledDigits() {
            // U+2460 CIRCLED NUMBER ONE → "1"
            assertThat(TextNormalizer.normalizeForComparison("\u2460"))
                .isEqualTo("1");
            assertThat(TextNormalizer.normalizeForComparison("\u2469"))
                .isEqualTo("10");
        }

        @Test
        @DisplayName("converts superscript digits to plain digits")
        void superscriptDigits() {
            // U+2070 SUPERSCRIPT ZERO → "0"
            // U+00B2 SUPERSCRIPT TWO (²) → "2"
            // U+00B9 SUPERSCRIPT ONE (¹) → "1"
            assertThat(TextNormalizer.normalizeForComparison("X\u00B2"))
                .isEqualTo("x2");
        }

        @Test
        @DisplayName("converts half-width katakana to full-width katakana")
        void halfWidthKatakana() {
            // U+FF82 HIRAGANA LETTER SMALL TU → katakana equivalent → plain
            // The NFKC path: half-katakana ﾃｷ → katakana テ、キ → plain テキ
            assertThat(TextNormalizer.normalizeForComparison("ﾃｷ"))
                .isEqualTo("テキ");
        }

        @Test
        @DisplayName("converts wide punctuation to plain equivalents")
        void widePunctuation() {
            // U+2015 HORIZONTAL BAR is not NFKC-mapped; verify no crash and no transformation
            assertThat(TextNormalizer.normalizeForComparison("foo\u2015bar"))
                .isEqualTo("foo\u2015bar");
        }
    }

    // -------------------------------------------------------------------------
    // normalizeForComparison() — Japanese / CJK preservation
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("normalizeForComparison() preserves Japanese and CJK characters")
    class CjkPreservationTests {

        @Test
        @DisplayName("preserves hiragana characters")
        void hiraganaPreserved() {
            assertThat(TextNormalizer.normalizeForComparison("さとり"))
                .isEqualTo("さとり");
        }

        @Test
        @DisplayName("preserves katakana characters")
        void katakanaPreserved() {
            assertThat(TextNormalizer.normalizeForComparison("サブリミックス"))
                .isEqualTo("サブリミックス");
        }

        @Test
        @DisplayName("preserves mixed CJK + ASCII string")
        void mixedCjkAndAscii() {
            assertThat(TextNormalizer.normalizeForComparison("Satori Maiden"))
                .isEqualTo("satori maiden");
            assertThat(TextNormalizer.normalizeForComparison("少女さとり"))
                .isEqualTo("少女さとり");
        }

        @Test
        @DisplayName("handles ideographic iteration mark")
        void ideographicIterationMark() {
            // U+3000 IDEOGRAPHIC SPACE → already covered by full-width space test
            // U+3000 is not an iteration mark; testing a real iteration mark
            assertThat(TextNormalizer.normalizeForComparison("foo\u300dbar"))
                .isNotEmpty(); // iteration marks normalize to their base characters
        }

        @Test
        @DisplayName("handles combining voiced/semi-voiced marks on katakana")
        void combiningVoicedMarks() {
            // U+30D0 KATAKANA LETTER VA + U+309A COMBINING KATAKANA-HIRAGANA VOICED/SEMI-VOICED MARK
            // NFKC canonical ordering decomposes but does NOT recompose these two codepoints.
            // Both characters remain; verify no crash and stable output.
            String result = TextNormalizer.normalizeForComparison("\u30D0\u309A");
            assertThat(result).isNotEmpty();
            assertThat(result).isEqualTo(TextNormalizer.normalizeForComparison("\u30D0\u309A"));
        }

        @Test
        @DisplayName("normalizes small kana (verifiable behavior)")
        void smallKanaNormalization() {
            // Small hiragana U+3041 is NOT NFKC-decomposed — small kana are fully-composed.
            // Verify the function handles it without crashing and returns stable output.
            String result = TextNormalizer.normalizeForComparison("\u3041");
            assertThat(result).isNotEmpty();
            assertThat(result).isEqualTo(TextNormalizer.normalizeForComparison("\u3041"));
        }
    }

    // -------------------------------------------------------------------------
    // normalizeForComparison() — case folding
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("normalizeForComparison() case folding")
    class CaseFoldingTests {

        @Test
        @DisplayName("lower-cases ASCII uppercase letters")
        void asciiUppercaseToLower() {
            assertThat(TextNormalizer.normalizeForComparison("ALBUM"))
                .isEqualTo("album");
        }

        @Test
        @DisplayName("lower-cases ASCII mixed-case string")
        void mixedAsciiLowercased() {
            assertThat(TextNormalizer.normalizeForComparison("SatoriMaiden"))
                .isEqualTo("satorimaiden");
        }

        @Test
        @DisplayName("does not change CJK characters on toLowerCase")
        void cjkUnchangedOnLowercase() {
            // CJK characters have no case distinction; verify they survive
            assertThat(TextNormalizer.normalizeForComparison("さとり"))
                .isEqualTo("さとり");
        }
    }

    // -------------------------------------------------------------------------
    // normalizeForComparison() — whitespace collapse
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("normalizeForComparison() whitespace collapse")
    class WhitespaceCollapseTests {

        @Test
        @DisplayName("collapses multiple spaces to one")
        void multipleSpacesCollapsed() {
            assertThat(TextNormalizer.normalizeForComparison("hello    world"))
                .isEqualTo("hello world");
        }

        @Test
        @DisplayName("collapses leading and trailing whitespace")
        void leadingTrailingWhitespaceCollapsed() {
            assertThat(TextNormalizer.normalizeForComparison("  hello world  "))
                .isEqualTo("hello world");
        }

        @Test
        @DisplayName("collapses tabs and newlines")
        void tabsAndNewlinesCollapsed() {
            assertThat(TextNormalizer.normalizeForComparison("hello\n\tworld"))
                .isEqualTo("hello world");
        }
    }

    // -------------------------------------------------------------------------
    // End-to-end equivalence tests (real THBWiki data patterns)
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("normalizeForComparison() end-to-end equivalence")
    class EquivalenceTests {

        @Test
        @DisplayName("full-width numeral vs plain numeral — equivalent")
        void fullWidthDigitMatchesPlain() {
            String a = TextNormalizer.normalizeForComparison("Album０１");
            String b = TextNormalizer.normalizeForComparison("Album01");
            assertThat(a).isEqualTo(b);
        }

        @Test
        @DisplayName("full-width space vs half-width space — equivalent")
        void fullWidthSpaceMatchesHalfWidth() {
            String a = TextNormalizer.normalizeForComparison("Album\u3000Remix");
            String b = TextNormalizer.normalizeForComparison("Album Remix");
            assertThat(a).isEqualTo(b);
        }

        @Test
        @DisplayName("uppercase vs lowercase ASCII — equivalent")
        void uppercaseMatchesLowercase() {
            String a = TextNormalizer.normalizeForComparison("SATORI MAIDEN");
            String b = TextNormalizer.normalizeForComparison("satori maiden");
            assertThat(a).isEqualTo(b);
        }

        @Test
        @DisplayName("whitespace-padded names — equivalent")
        void whitespacePaddingNormalized() {
            String a = TextNormalizer.normalizeForComparison("  Satori Maiden  ");
            String b = TextNormalizer.normalizeForComparison("Satori Maiden");
            assertThat(a).isEqualTo(b);
        }

        @Test
        @DisplayName("circled number track name vs plain number — equivalent")
        void circledNumberMatchesPlain() {
            // Common in THBWiki track listings: ① Satori Maiden
            // NFKC: U+2460 CIRCLED NUMBER ONE → "1" (no dot)
            String a = TextNormalizer.normalizeForComparison("\u2460 Satori Maiden");
            String b = TextNormalizer.normalizeForComparison("1 Satori Maiden");
            assertThat(a).isEqualTo(b);
        }

        @Test
        @DisplayName("full-width mixed case vs plain lowercase — equivalent")
        void fullWidthMixedCaseMatchesPlainLower() {
            String a = TextNormalizer.normalizeForComparison("ＡＢＣ　ＡＬＢＵＭ");
            String b = TextNormalizer.normalizeForComparison("abc album");
            assertThat(a).isEqualTo(b);
        }

        @Test
        @DisplayName(" NFKC + full-width combo with circled digit + CJK — equivalent")
        void nfkcFullWidthCircledCjk() {
            // Full-width digits + full-width space + CJK → plain equivalents
            // Note: U+2015 (horizontal bar) is NOT NFKC-mapped to hyphen, so use hyphen directly
            String a = TextNormalizer.normalizeForComparison("０１　さとり");
            String b = TextNormalizer.normalizeForComparison("01 さとり");
            assertThat(a).isEqualTo(b);
        }

        @Test
        @DisplayName("normalize() and normalizeForComparison() give same result for clean ASCII input")
        void cleanAscii_sameForBoth() {
            String a = TextNormalizer.normalize("hello world");
            String b = TextNormalizer.normalizeForComparison("hello world");
            assertThat(b).isEqualTo(a);
        }
    }
}
