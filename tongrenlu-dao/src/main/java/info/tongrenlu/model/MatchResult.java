package info.tongrenlu.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of fuzzy matching between a local track and a THBWiki track.
 * Contains the matched THBWiki track, confidence score, and match metadata.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchResult {

    /**
     * The matched THBWiki track, or null if no match was found.
     */
    private ThbwikiTrack thbwikiTrack;

    /**
     * Confidence score between 0.0 and 1.0.
     * A score >= 0.85 is considered a strong match.
     * A score < 0.85 means the match was not saved.
     */
    private double confidence;

    /**
     * The normalized input string used for matching.
     */
    private String normalizedInput;

    /**
     * The normalized THBWiki track name that was matched.
     */
    private String normalizedMatch;

    /**
     * True if a match was found and the confidence score meets the threshold.
     */
    private boolean matched;

    /**
     * Creates a "no match" result.
     */
    public static MatchResult noMatch(String normalizedInput) {
        return MatchResult.builder()
                .normalizedInput(normalizedInput)
                .matched(false)
                .confidence(0.0)
                .build();
    }

    /**
     * Creates a successful match result.
     */
    public static MatchResult matched(String normalizedInput, String normalizedMatch,
            ThbwikiTrack thbwikiTrack, double confidence) {
        return MatchResult.builder()
                .thbwikiTrack(thbwikiTrack)
                .confidence(confidence)
                .normalizedInput(normalizedInput)
                .normalizedMatch(normalizedMatch)
                .matched(true)
                .build();
    }
}
