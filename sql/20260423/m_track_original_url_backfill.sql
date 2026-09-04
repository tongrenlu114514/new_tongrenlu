-- Backfill m_track.original_url from existing m_track.original values.
-- v3 — covers prefix variants and trims leading whitespace after prefix strip.
--
-- Background:
-- m_track.original stores free-text descriptions of the source song. Examples:
--   '原曲：永夜抄~Eastern Night.'                   ← full-width colon (FW)
--   '原曲:少女が見た日本の原風景'                  ← half-width colon (HW)
--   '原曲: メイドと血の懐中時計'                  ← HW with space after colon
--   '原曲:デザイアドライブ / 古きユアンシェン'     ← HW prefix + ' / ' separator
--   'Original : 星の器'                          ← English prefix
--   'Original : デザイアドライブ / 古きユアンシェン' ← English + slash
--   '[永]少女綺想曲 〜 Dream Battle'              ← bracketed prefix + ' 〜 '
--   'おてんば恋娘 (from 東方紅魔郷)'              ← parenthetical "from X"
--   '妖々跋扈/東方妖々夢'                          ← slash without spaces
--
-- After sql/20260419/m_track_original_url.sql added original_url, this migration
-- seeds it with a best-effort THBWiki URL derived from the original song name.
-- OriginalUpdateJob will overwrite these with verified URLs where matchAndSave
-- succeeds.
--
-- Format rules (applied in order; first matching step wins):
--   1. Strip prefix markers: 原曲： / 原曲: / Original / [xxx]
--   2. TRIM leading whitespace from the remainder (so '原曲: xxx' → 'xxx').
--   3. If 'X (from Y)' present, take part before ' (from '.
--   4. If ' / ' or ' 〜 ' or ' ～ ' separator present, take the LAST segment.
--   5. Otherwise use the whole text as the song name.
--   6. Spaces become '_' (MediaWiki URL convention); other chars kept as-is
--      and percent-encoded at render time by the frontend.

-- Step 1: '原曲：xxx' (full-width colon) — strip prefix, no leading whitespace
UPDATE m_track
SET original_url = CONCAT('https://thbwiki.cc/', REPLACE(SUBSTRING(SUBSTRING_INDEX(original, '：', -1), 1, 255), ' ', '_'))
WHERE original_url IS NULL
  AND original IS NOT NULL AND original <> ''
  AND original LIKE '原曲：%';

-- Step 2: '原曲:xxx' or '原曲 xxx' (half-width) — strip prefix AND any leading spaces
UPDATE m_track
SET original_url = CONCAT('https://thbwiki.cc/', REPLACE(LTRIM(SUBSTRING(SUBSTRING_INDEX(original, ':', -1), 1, 255)), ' ', '_'))
WHERE original_url IS NULL
  AND original IS NOT NULL AND original <> ''
  AND (original LIKE '原曲:%' OR original LIKE '原曲 %');

-- Step 3: 'Original' / 'Original :' prefix (case-insensitive)
--         Strip 'Original' and any leading ':' / ' ' from the remainder.
UPDATE m_track
SET original_url = CONCAT('https://thbwiki.cc/', REPLACE(LTRIM(SUBSTRING(original, 9)), ' ', '_'))
WHERE original_url IS NULL
  AND original IS NOT NULL AND original <> ''
  AND LOWER(LEFT(original, 8)) = 'original';

-- Step 4: '[永]少女綺想曲 〜 Dream Battle' — strip bracket prefix, then tilde rule
--         Take the part AFTER the first ']' (with leading space trimmed),
--         then split on ' 〜 ' and take the FIRST segment (the main song name).
UPDATE m_track
SET original_url = CONCAT(
    'https://thbwiki.cc/',
    REPLACE(
        SUBSTRING_INDEX(
            LTRIM(SUBSTRING(original, LOCATE(']', original) + 1)),
            ' 〜 ', 1
        ),
        ' ', '_'
    )
)
WHERE original_url IS NULL
  AND original IS NOT NULL AND original <> ''
  AND original LIKE '[%' AND original LIKE '%]%'
  AND original LIKE '% 〜 %';

-- Step 5: 'X (from Y)' — take the part before ' (from '
UPDATE m_track
SET original_url = CONCAT('https://thbwiki.cc/', REPLACE(SUBSTRING_INDEX(original, ' (from ', 1), ' ', '_'))
WHERE original_url IS NULL
  AND original IS NOT NULL AND original <> ''
  AND original LIKE '% (from %';

-- Step 6: ' / ' separator — take the LAST segment
UPDATE m_track
SET original_url = CONCAT(
    'https://thbwiki.cc/',
    REPLACE(
        SUBSTRING_INDEX(SUBSTRING_INDEX(original, ' / ', -1), ' / ', 1),
        ' ', '_'
    )
)
WHERE original_url IS NULL
  AND original IS NOT NULL AND original <> ''
  AND original LIKE '% / %';

-- Step 7: ' 〜 ' separator — take the LAST segment
UPDATE m_track
SET original_url = CONCAT(
    'https://thbwiki.cc/',
    REPLACE(
        SUBSTRING_INDEX(SUBSTRING_INDEX(original, ' 〜 ', -1), ' 〜 ', 1),
        ' ', '_'
    )
)
WHERE original_url IS NULL
  AND original IS NOT NULL AND original <> ''
  AND original LIKE '% 〜 %';

-- Step 8: ' ～ ' separator — take the LAST segment
UPDATE m_track
SET original_url = CONCAT(
    'https://thbwiki.cc/',
    REPLACE(
        SUBSTRING_INDEX(SUBSTRING_INDEX(original, ' ～ ', -1), ' ～ ', 1),
        ' ', '_'
    )
)
WHERE original_url IS NULL
  AND original IS NOT NULL AND original <> ''
  AND original LIKE '% ～ %';

-- Step 9: '/' separator without surrounding spaces
UPDATE m_track
SET original_url = CONCAT(
    'https://thbwiki.cc/',
    REPLACE(
        SUBSTRING_INDEX(original, '/', -1),
        ' ', '_'
    )
)
WHERE original_url IS NULL
  AND original IS NOT NULL AND original <> ''
  AND original NOT LIKE '% / %'
  AND original LIKE '%/%'
  AND original NOT LIKE '% // %';

-- Step 10: plain rows — use the whole text as the song name
UPDATE m_track
SET original_url = CONCAT('https://thbwiki.cc/', REPLACE(original, ' ', '_'))
WHERE original_url IS NULL
  AND original IS NOT NULL AND original <> '';

-- IMPORTANT: rows where original_url has been set by this migration should be
-- re-verified by OriginalUpdateJob before being shown to end users. The frontend
-- currently displays the link unconditionally; clicking a 404 is no worse than
-- the current "no link" state.
