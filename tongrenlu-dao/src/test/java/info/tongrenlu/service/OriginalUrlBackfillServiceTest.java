package info.tongrenlu.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link OriginalUrlBackfillService}.
 *
 * Test data is sourced from real production patterns observed in m_track.original
 * (see THBWiki backfill verification report, 2026-09).
 */
class OriginalUrlBackfillServiceTest {

    private final OriginalUrlBackfillService service = new OriginalUrlBackfillService();

    @Nested
    @DisplayName("Empty / null inputs return null")
    class EmptyInputs {
        @Test void nullReturnsNull() { assertThat(service.deriveUrl(null)).isNull(); }
        @Test void emptyReturnsNull() { assertThat(service.deriveUrl("")).isNull(); }
        @Test void blankReturnsNull() { assertThat(service.deriveUrl("   ")).isNull(); }
    }

    @Nested
    @DisplayName("FW prefix '原曲：' (full-width colon)")
    class FwPrefix {
        @ParameterizedTest(name = "[{index}] {0} -> {1}")
        @CsvSource({
                "'原曲：永夜抄~Eastern Night.',                'https://thbwiki.cc/永夜抄~Eastern_Night.'",
                "'原曲：人形裁判 ～ 人の形弄びし少女',         'https://thbwiki.cc/人形裁判_～_人の形弄びし少女'",
                "'原曲：オリエンタルダークフライト',           'https://thbwiki.cc/オリエンタルダークフライト'",
                // FW colon + space after
                "'原曲： 彼岸帰航 ～ Riverside View',         'https://thbwiki.cc/彼岸帰航_～_Riverside_View'",
        })
        void fwPrefixCases(String input, String expected) {
            assertThat(service.deriveUrl(input).url()).isEqualTo(expected);
        }

        @Test
        @DisplayName("FW prefix step is tagged 'fw_prefix'")
        void fwPrefixStepName() {
            assertThat(service.deriveUrl("原曲：亡き王女の為のセプテット").step())
                    .isEqualTo("fw_prefix");
        }
    }

    @Nested
    @DisplayName("HW prefix '原曲:' (half-width colon)")
    class HwPrefix {
        @ParameterizedTest(name = "[{index}] {0} -> {1}")
        @CsvSource({
                // Colon immediately followed by content
                "'原曲:少女が見た日本の原風景',                'https://thbwiki.cc/少女が見た日本の原風景'",
                "'原曲:ラクトガール～少女密室',                'https://thbwiki.cc/ラクトガール～少女密室'",
                "'原曲:古の冥界寺',                            'https://thbwiki.cc/古の冥界寺'",
                // Colon + space
                "'原曲: メイドと血の懐中時計',                 'https://thbwiki.cc/メイドと血の懐中時計'",
                "'原曲: 亡き王女の為のセプテット',             'https://thbwiki.cc/亡き王女の為のセプテット'",
                // No colon, just space
                "'原曲 幼心地の有頂天',                       'https://thbwiki.cc/幼心地の有頂天'",
                "'原曲 幻視の夜～Ghostly Eyes',                'https://thbwiki.cc/幻視の夜～Ghostly_Eyes'",
        })
        void hwPrefixCases(String input, String expected) {
            assertThat(service.deriveUrl(input).url()).isEqualTo(expected);
        }

        @Test
        void hwPrefixStepName() {
            assertThat(service.deriveUrl("原曲:foo").step()).isEqualTo("hw_prefix");
        }
    }

    @Nested
    @DisplayName("Original (English) prefix")
    class EnglishPrefix {
        @ParameterizedTest(name = "[{index}] {0} -> {1}")
        @CsvSource({
                "'Original : 星の器',                          'https://thbwiki.cc/星の器'",
                "'Original : デザイアドライブ',                 'https://thbwiki.cc/デザイアドライブ'",
                "'Original: デザイアドライブ',                  'https://thbwiki.cc/デザイアドライブ'",
                "'Original デザイアドライブ',                  'https://thbwiki.cc/デザイアドライブ'",
        })
        void englishPrefixCases(String input, String expected) {
            assertThat(service.deriveUrl(input).url()).isEqualTo(expected);
        }

        @Test
        void englishPrefixStepName() {
            assertThat(service.deriveUrl("Original : x").step()).isEqualTo("english_prefix");
        }
    }

    @Nested
    @DisplayName("Japanese bracket prefixes 原曲《xxx》 and 原曲「xxx」")
    class BracketPrefix {
        @ParameterizedTest(name = "[{index}] {0} -> {1}")
        @CsvSource({
                // 《...》 outer — consume whole bracket
                "'原曲《幽雅に咲かせ、墨染の桜　～ Border of Life》',  'https://thbwiki.cc/幽雅に咲かせ、墨染の桜_～_Border_of_Life'",
                "'原曲《ネクロファンタジア》',                  'https://thbwiki.cc/ネクロファンタジア'",
                // 「...」 Japanese single quote
                "'原曲「不思議なお祓い棒」',                    'https://thbwiki.cc/不思議なお祓い棒'",
                "'原曲「輝く針の小人族 ～ Little Princess」',   'https://thbwiki.cc/輝く針の小人族_～_Little_Princess'",
                // 『...』 Japanese double quote
                "'原曲『竹取飛翔　～ Lunatic Princess』',      'https://thbwiki.cc/竹取飛翔_～_Lunatic_Princess'",
        })
        void bracketCases(String input, String expected) {
            assertThat(service.deriveUrl(input).url()).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("Mixed kana prefix 原曲、原詩 / 原曲・原詩")
    class MixedPrefix {
        @ParameterizedTest(name = "[{index}] {0} -> {1}")
        @CsvSource({
                "'原曲、原詩：童祭～Innocent Treasures',       'https://thbwiki.cc/童祭～Innocent_Treasures'",
                "'原曲・原詩：童祭～Innocent Treasures',       'https://thbwiki.cc/童祭～Innocent_Treasures'",
        })
        void mixedPrefixCases(String input, String expected) {
            assertThat(service.deriveUrl(input).url()).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("X (from Y) parenthetical")
    class FromX {
        @ParameterizedTest(name = "[{index}] {0} -> {1}")
        @CsvSource({
                "'おてんば恋娘 (from 東方紅魔郷)',             'https://thbwiki.cc/おてんば恋娘'",
                "'Bad Apple!! (from 東方幻想郷)',                'https://thbwiki.cc/Bad_Apple!!'",
                "'今昔幻想郷 (from 東方花映塚)',                'https://thbwiki.cc/今昔幻想郷'",
        })
        void fromXCases(String input, String expected) {
            assertThat(service.deriveUrl(input).url()).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("List separators (comma / 、) — take FIRST segment")
    class ListSeparators {
        @ParameterizedTest(name = "[{index}] {0} -> {1}")
        @CsvSource({
                // ASCII comma
                "'A, B, C',                                     'https://thbwiki.cc/A'",
                "'デザイアドライブ, 古きユアンシェン',          'https://thbwiki.cc/デザイアドライブ'",
                // Full-width comma
                "'A、B、C',                                     'https://thbwiki.cc/A'",
                // Slash variants (only when no ' / ' — ' / ' is the existing step 6 rule)
                "'デザイアドライブ / 古きユアンシェン',          'https://thbwiki.cc/デザイアドライブ'",
                "'デザイアドライブ／古きユアンシェン',          'https://thbwiki.cc/デザイアドライブ'",
        })
        void listSepCases(String input, String expected) {
            assertThat(service.deriveUrl(input).url()).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("Plain rows (no prefix, no separator)")
    class Plain {
        @ParameterizedTest(name = "[{index}] {0} -> {1}")
        @CsvSource({
                "'ネイティブフェイス',                          'https://thbwiki.cc/ネイティブフェイス'",
                "'ハルトマンの妖怪少女',                        'https://thbwiki.cc/ハルトマンの妖怪少女'",
                "'恋色マスタースパーク',                        'https://thbwiki.cc/恋色マスタースパーク'",
        })
        void plainCases(String input, String expected) {
            assertThat(service.deriveUrl(input).url()).isEqualTo(expected);
        }

        @Test
        void plainStepName() {
            assertThat(service.deriveUrl("ネイティブフェイス").step()).isEqualTo("plain");
        }
    }

    @Nested
    @DisplayName("Combined cases — prefix + list + from")
    class Combined {
        @Test
        void fwPrefixAndFromX() {
            // 原曲：xxx (from yy)
            String in = "原曲：おてんば恋娘 (from 東方紅魔郷)";
            assertThat(service.deriveUrl(in).url()).isEqualTo("https://thbwiki.cc/おてんば恋娘");
        }

        @Test
        void hwPrefixAndList() {
            String in = "原曲: デザイアドライブ, 古きユアンシェン";
            // After strip prefix: "デザイアドライブ, 古きユアンシェン"
            // First segment: "デザイアドライブ"
            assertThat(service.deriveUrl(in).url()).isEqualTo("https://thbwiki.cc/デザイアドライブ");
        }
    }

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {
        @Test
        void onlyPrefixReturnsNull() {
            assertThat(service.deriveUrl("原曲：")).isNull();
        }

        @Test
        void prefixWithOnlyFromReturnsNull() {
            assertThat(service.deriveUrl("原曲：(from nothing)")).isNull();
        }

        @Test
        void leadingAndTrailingWhitespaceStripped() {
            assertThat(service.deriveUrl("  原曲：亡き王女  ").url())
                    .isEqualTo("https://thbwiki.cc/亡き王女");
        }

        @Test
        void internalMultipleSpacesBecomeSingleUnderscore() {
            // The contract: each space becomes '_', so multiple spaces stay as multiple '_'
            assertThat(service.deriveUrl("原曲：A   B").url())
                    .isEqualTo("https://thbwiki.cc/A___B");
        }
    }
}
