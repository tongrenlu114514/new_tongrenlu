package info.tongrenlu.service;

import info.tongrenlu.cache.ThbwikiCacheService;
import info.tongrenlu.domain.TrackBean;
import info.tongrenlu.model.MatchResult;
import info.tongrenlu.model.ThbwikiTrack;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ThbwikiService matching algorithm.
 * Tests cover 8 match cases for track-to-THBWiki fuzzy matching:
 * 1. Exact match
 * 2. Full-width vs half-width ASCII
 * 3. Circled numbers vs regular numbers
 * 4. Whitespace normalization
 * 5. Case insensitivity
 * 6. NFKC normalization (compatibility characters)
 * 7. Low confidence (no match)
 * 8. Mixed variations
 */
class ThbwikiServiceMatchTest {

    private ThbwikiService service;
    private ThbwikiCacheService cacheService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        cacheService = new ThbwikiCacheService();
        objectMapper = new ObjectMapper();
        service = new ThbwikiService(cacheService, objectMapper);
    }

    private List<ThbwikiTrack> createTrackList(ThbwikiTrack... tracks) {
        List<ThbwikiTrack> list = new ArrayList<>();
        for (ThbwikiTrack track : tracks) {
            list.add(track);
        }
        return list;
    }

    private ThbwikiTrack createTrack(String name, String originalSource, String originalName) {
        ThbwikiTrack track = new ThbwikiTrack();
        track.setName(name);
        track.setOriginalSource(originalSource);
        track.setOriginalName(originalName);
        return track;
    }

    @Nested
    @DisplayName("matchTrack - Case 1: Exact match")
    class ExactMatchTests {

        @Test
        @DisplayName("returns perfect 1.0 confidence for identical strings")
        void exactMatch_returnsFullConfidence() {
            ThbwikiTrack thbwikiTrack = createTrack("Satori Maiden", "少女さとり", "3rd eye");
            List<ThbwikiTrack> tracks = createTrackList(thbwikiTrack);

            MatchResult result = service.matchTrack("Satori Maiden", tracks);

            assertThat(result.isMatched()).isTrue();
            assertThat(result.getConfidence()).isEqualTo(1.0);
            assertThat(result.getThbwikiTrack()).isEqualTo(thbwikiTrack);
            assertThat(result.getNormalizedMatch()).isEqualTo("satori maiden");
        }

        @Test
        @DisplayName("returns perfect 1.0 confidence for identical CJK strings")
        void exactMatchCJK_returnsFullConfidence() {
            ThbwikiTrack thbwikiTrack = createTrack("少女さとり　～ 3rd eye", "少女さとり", "3rd eye");
            List<ThbwikiTrack> tracks = createTrackList(thbwikiTrack);

            MatchResult result = service.matchTrack("少女さとり　～ 3rd eye", tracks);

            assertThat(result.isMatched()).isTrue();
            assertThat(result.getConfidence()).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("matchTrack - Case 2: Full-width vs half-width ASCII")
    class FullWidthTests {

        @Test
        @DisplayName("matches full-width digits to half-width digits")
        void fullWidthDigits_matched() {
            ThbwikiTrack thbwikiTrack = createTrack("Album01 Remix", "Original", "Song");
            List<ThbwikiTrack> tracks = createTrackList(thbwikiTrack);

            // Database has full-width, THBWiki has half-width
            MatchResult result = service.matchTrack("Album０１ Remix", tracks);

            assertThat(result.isMatched()).isTrue();
            assertThat(result.getConfidence()).isGreaterThanOrEqualTo(0.85);
        }

        @Test
        @DisplayName("matches full-width uppercase letters to lowercase")
        void fullWidthUppercase_matched() {
            ThbwikiTrack thbwikiTrack = createTrack("Album Remix", "Original", "Song");
            List<ThbwikiTrack> tracks = createTrackList(thbwikiTrack);

            MatchResult result = service.matchTrack("ＡＬＢＵＭ Remix", tracks);

            assertThat(result.isMatched()).isTrue();
            assertThat(result.getConfidence()).isGreaterThanOrEqualTo(0.85);
        }

        @Test
        @DisplayName("matches full-width lowercase letters")
        void fullWidthLowercase_matched() {
            ThbwikiTrack thbwikiTrack = createTrack("Album Remix", "Original", "Song");
            List<ThbwikiTrack> tracks = createTrackList(thbwikiTrack);

            MatchResult result = service.matchTrack("ａｌｂｕｍ  remix", tracks);

            assertThat(result.isMatched()).isTrue();
            assertThat(result.getConfidence()).isGreaterThanOrEqualTo(0.85);
        }
    }

    @Nested
    @DisplayName("matchTrack - Case 3: Circled numbers vs regular numbers")
    class CircledNumberTests {

        @Test
        @DisplayName("matches circled number ① to regular number 1")
        void circledOne_matchedToOne() {
            ThbwikiTrack thbwikiTrack = createTrack("1. Satori Maiden", "少女さとり", "3rd eye");
            List<ThbwikiTrack> tracks = createTrackList(thbwikiTrack);

            MatchResult result = service.matchTrack("① Satori Maiden", tracks);

            assertThat(result.isMatched()).isTrue();
            assertThat(result.getConfidence()).isGreaterThanOrEqualTo(0.85);
        }

        @Test
        @DisplayName("matches circled number ② to regular number 2")
        void circledTwo_matchedToTwo() {
            ThbwikiTrack thbwikiTrack = createTrack("2. Lost Place", "幽閉少女", "Silent Flower");
            List<ThbwikiTrack> tracks = createTrackList(thbwikiTrack);

            MatchResult result = service.matchTrack("② Lost Place", tracks);

            assertThat(result.isMatched()).isTrue();
            assertThat(result.getConfidence()).isGreaterThanOrEqualTo(0.85);
        }

        @Test
        @DisplayName("matches circled number ③ to regular number 3")
        void circledThree_matchedToThree() {
            ThbwikiTrack thbwikiTrack = createTrack("3. Unknown Track", "未知", "Source");
            List<ThbwikiTrack> tracks = createTrackList(thbwikiTrack);

            MatchResult result = service.matchTrack("③ Unknown Track", tracks);

            assertThat(result.isMatched()).isTrue();
            assertThat(result.getConfidence()).isGreaterThanOrEqualTo(0.85);
        }
    }

    @Nested
    @DisplayName("matchTrack - Case 4: Whitespace normalization")
    class WhitespaceNormalizationTests {

        @Test
        @DisplayName("matches with extra spaces collapsed")
        void extraSpaces_collapsed() {
            ThbwikiTrack thbwikiTrack = createTrack("Satori Maiden", "少女さとり", "3rd eye");
            List<ThbwikiTrack> tracks = createTrackList(thbwikiTrack);

            MatchResult result = service.matchTrack("Satori    Maiden", tracks);

            assertThat(result.isMatched()).isTrue();
            assertThat(result.getConfidence()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("matches with leading/trailing spaces trimmed")
        void leadingTrailingSpaces_trimmed() {
            ThbwikiTrack thbwikiTrack = createTrack("Satori Maiden", "少女さとり", "3rd eye");
            List<ThbwikiTrack> tracks = createTrackList(thbwikiTrack);

            MatchResult result = service.matchTrack("   Satori Maiden   ", tracks);

            assertThat(result.isMatched()).isTrue();
            assertThat(result.getConfidence()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("matches ideographic space to regular space")
        void ideographicSpace_converted() {
            ThbwikiTrack thbwikiTrack = createTrack("Satori Maiden", "少女さとり", "3rd eye");
            List<ThbwikiTrack> tracks = createTrackList(thbwikiTrack);

            // U+3000 (ideographic space) between words
            MatchResult result = service.matchTrack("Satori\u3000Maiden", tracks);

            assertThat(result.isMatched()).isTrue();
            assertThat(result.getConfidence()).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("matchTrack - Case 5: Case insensitivity")
    class CaseInsensitivityTests {

        @Test
        @DisplayName("matches uppercase to lowercase")
        void uppercaseToLowercase_matched() {
            ThbwikiTrack thbwikiTrack = createTrack("Satori Maiden", "少女さとり", "3rd eye");
            List<ThbwikiTrack> tracks = createTrackList(thbwikiTrack);

            MatchResult result = service.matchTrack("SATORI MAIDEN", tracks);

            assertThat(result.isMatched()).isTrue();
            assertThat(result.getConfidence()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("matches mixed case")
        void mixedCase_matched() {
            ThbwikiTrack thbwikiTrack = createTrack("Satori Maiden", "少女さとり", "3rd eye");
            List<ThbwikiTrack> tracks = createTrackList(thbwikiTrack);

            MatchResult result = service.matchTrack("SaToRi MaIdEn", tracks);

            assertThat(result.isMatched()).isTrue();
            assertThat(result.getConfidence()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("CJK characters unaffected by case folding")
        void cjkUnaffectedByCase() {
            ThbwikiTrack thbwikiTrack = createTrack("少女さとり", "Source", "Name");
            List<ThbwikiTrack> tracks = createTrackList(thbwikiTrack);

            // CJK characters don't have case, so same string should match
            MatchResult result = service.matchTrack("少女さとり", tracks);

            assertThat(result.isMatched()).isTrue();
            assertThat(result.getConfidence()).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("matchTrack - Case 6: NFKC normalization")
    class NfkcNormalizationTests {

        @Test
        @DisplayName("matches compatibility digit to regular digit")
        void compatibilityDigit_normalized() {
            ThbwikiTrack thbwikiTrack = createTrack("Track 2", "Source", "Name");
            List<ThbwikiTrack> tracks = createTrackList(thbwikiTrack);

            // U+2461 (CIRCLED DIGIT TWO) NFKC → '2'
            MatchResult result = service.matchTrack("Track \u2461", tracks);

            assertThat(result.isMatched()).isTrue();
            assertThat(result.getConfidence()).isGreaterThanOrEqualTo(0.85);
        }

        @Test
        @DisplayName("matches full-width alpha numeric to half-width")
        void fullWidthAlphaNumeric_matched() {
            // Full-width alphanumeric characters (U+FF01 - U+FF5E) map to ASCII via NFKC
            ThbwikiTrack thbwikiTrack = createTrack("ABC", "Source", "Name");
            List<ThbwikiTrack> tracks = createTrackList(thbwikiTrack);

            // Full-width A(U+FF01), B(U+FF02), C(U+FF03) NFKC → abc
            MatchResult result = service.matchTrack("\uFF21\uFF22\uFF23", tracks);

            assertThat(result.isMatched()).isTrue();
            assertThat(result.getConfidence()).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("matchTrack - Case 7: Low confidence (no match)")
    class NoMatchTests {

        @Test
        @DisplayName("returns no match for completely different strings")
        void completelyDifferent_noMatch() {
            ThbwikiTrack thbwikiTrack = createTrack("Satori Maiden", "少女さとり", "3rd eye");
            List<ThbwikiTrack> tracks = createTrackList(thbwikiTrack);

            MatchResult result = service.matchTrack("Completely Different Track", tracks);

            assertThat(result.isMatched()).isFalse();
            assertThat(result.getConfidence()).isLessThan(0.85);
        }

        @Test
        @DisplayName("returns no match for short strings with small differences")
        void shortStrings_lowConfidence() {
            ThbwikiTrack thbwikiTrack = createTrack("ABC", "Source", "Name");
            List<ThbwikiTrack> tracks = createTrackList(thbwikiTrack);

            // Two character difference in 3-character string = 33% diff = 0.67 confidence
            MatchResult result = service.matchTrack("XYZ", tracks);

            assertThat(result.isMatched()).isFalse();
            assertThat(result.getConfidence()).isLessThan(0.85);
        }

        @Test
        @DisplayName("returns no match for empty track name")
        void emptyTrackName_noMatch() {
            List<ThbwikiTrack> tracks = createTrackList(
                    createTrack("Satori Maiden", "少女さとり", "3rd eye")
            );

            MatchResult result = service.matchTrack("", tracks);

            assertThat(result.isMatched()).isFalse();
        }

        @Test
        @DisplayName("returns no match for null track name")
        void nullTrackName_noMatch() {
            List<ThbwikiTrack> tracks = createTrackList(
                    createTrack("Satori Maiden", "少女さとり", "3rd eye")
            );

            MatchResult result = service.matchTrack(null, tracks);

            assertThat(result.isMatched()).isFalse();
        }

        @Test
        @DisplayName("returns no match for empty track list")
        void emptyTrackList_noMatch() {
            MatchResult result = service.matchTrack("Satori Maiden", List.of());

            assertThat(result.isMatched()).isFalse();
        }

        @Test
        @DisplayName("returns no match for null track list")
        void nullTrackList_noMatch() {
            MatchResult result = service.matchTrack("Satori Maiden", null);

            assertThat(result.isMatched()).isFalse();
        }
    }

    @Nested
    @DisplayName("matchTrack - Case 8: Mixed variations")
    class MixedVariationTests {

        @Test
        @DisplayName("matches with full-width, circled number, and extra spaces")
        void fullMixedVariations_matched() {
            ThbwikiTrack thbwikiTrack = createTrack("1. Satori Maiden", "少女さとり", "3rd eye");
            List<ThbwikiTrack> tracks = createTrackList(thbwikiTrack);

            // Full-width circled number + ideographic space + uppercase
            MatchResult result = service.matchTrack("①　SATORI　MAIDEN", tracks);

            assertThat(result.isMatched()).isTrue();
            assertThat(result.getConfidence()).isGreaterThanOrEqualTo(0.85);
        }

        @Test
        @DisplayName("matches album with remix suffix")
        void remixSuffix_matched() {
            ThbwikiTrack thbwikiTrack = createTrack("Satori Maiden (Remix)", "少女さとり", "3rd eye");
            List<ThbwikiTrack> tracks = createTrackList(thbwikiTrack);

            MatchResult result = service.matchTrack("Satori Maiden Remix", tracks);

            // Parentheses don't match, so confidence is lower but should still match
            assertThat(result.isMatched()).isTrue();
            assertThat(result.getConfidence()).isGreaterThanOrEqualTo(0.85);
        }

        @Test
        @DisplayName("selects best match from multiple candidates")
        void multipleCandidates_selectsBest() {
            ThbwikiTrack track1 = createTrack("Satori Maiden", "Source1", "Name1");
            ThbwikiTrack track2 = createTrack("Lost Place", "Source2", "Name2");
            ThbwikiTrack track3 = createTrack("Satori Theme", "Source3", "Name3");
            List<ThbwikiTrack> tracks = createTrackList(track1, track2, track3);

            MatchResult result = service.matchTrack("Satori Maiden", tracks);

            assertThat(result.isMatched()).isTrue();
            // Should match "Satori Maiden" (exact), not "Satori Theme"
            assertThat(result.getThbwikiTrack()).isEqualTo(track1);
            assertThat(result.getConfidence()).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("matchAndSave tests")
    class MatchAndSaveTests {

        @Test
        @DisplayName("matchAndSave saves original info for matched track")
        void matchAndSave_savesOriginalInfo() {
            ThbwikiTrack thbwikiTrack = createTrack("Satori Maiden", "少女さとり", "3rd eye");
            List<ThbwikiTrack> tracks = createTrackList(thbwikiTrack);

            TrackBean track = new TrackBean();
            track.setId(1L);
            track.setName("Satori Maiden");

            boolean result = service.matchAndSave(track, tracks);

            assertThat(result).isTrue();
            assertThat(track.getOriginal()).isEqualTo("少女さとり - 3rd eye");
        }

        @Test
        @DisplayName("matchAndSave handles track without original name")
        void matchAndSave_noOriginalName() {
            ThbwikiTrack thbwikiTrack = createTrack("Satori Maiden", "少女さとり", null);
            List<ThbwikiTrack> tracks = createTrackList(thbwikiTrack);

            TrackBean track = new TrackBean();
            track.setId(1L);
            track.setName("Satori Maiden");

            boolean result = service.matchAndSave(track, tracks);

            assertThat(result).isTrue();
            assertThat(track.getOriginal()).isEqualTo("少女さとり");
        }

        @Test
        @DisplayName("matchAndSave returns false for no match")
        void matchAndSave_noMatch_returnsFalse() {
            ThbwikiTrack thbwikiTrack = createTrack("Satori Maiden", "少女さとり", "3rd eye");
            List<ThbwikiTrack> tracks = createTrackList(thbwikiTrack);

            TrackBean track = new TrackBean();
            track.setId(1L);
            track.setName("Completely Different");

            boolean result = service.matchAndSave(track, tracks);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("matchAndSave returns false for null track")
        void matchAndSave_nullTrack_returnsFalse() {
            boolean result = service.matchAndSave(null, List.of());

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("calculateConfidence tests")
    class CalculateConfidenceTests {

        @Test
        @DisplayName("returns 1.0 for identical strings")
        void identicalStrings_returnsOne() {
            double confidence = service.calculateConfidence("satori maiden", "satori maiden");

            assertThat(confidence).isEqualTo(1.0);
        }

        @Test
        @DisplayName("returns 0.0 for null input")
        void nullInput_returnsZero() {
            double confidence = service.calculateConfidence(null, "test");

            assertThat(confidence).isEqualTo(0.0);
        }

        @Test
        @DisplayName("returns 0.0 for null candidate")
        void nullCandidate_returnsZero() {
            double confidence = service.calculateConfidence("test", null);

            assertThat(confidence).isEqualTo(0.0);
        }

        @Test
        @DisplayName("returns 0.0 for both null")
        void bothNull_returnsZero() {
            double confidence = service.calculateConfidence(null, null);

            assertThat(confidence).isEqualTo(0.0);
        }

        @Test
        @DisplayName("returns 1.0 for both empty strings")
        void bothEmpty_returnsOne() {
            double confidence = service.calculateConfidence("", "");

            assertThat(confidence).isEqualTo(1.0);
        }

        @Test
        @DisplayName("returns correct confidence for partial match")
        void partialMatch_correctConfidence() {
            // "abc" vs "abd" - 1 edit out of 3 chars = 1 - 1/3 = 0.667
            double confidence = service.calculateConfidence("abc", "abd");

            assertThat(confidence).isGreaterThan(0.6);
            assertThat(confidence).isLessThan(0.7);
        }
    }
}
