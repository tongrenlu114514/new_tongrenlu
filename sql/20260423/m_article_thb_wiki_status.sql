-- Add thb_wiki_status column to m_article
-- Ticket: refactor thb_wiki_url sentinel strings into a typed status column
-- Values:
--   PENDING      - not yet processed by OriginalUpdateJob
--   MATCHED      - thb_wiki_url was set to a real THBWiki album URL
--   NOT_FOUND    - THBWiki search returned no results
--   FETCH_FAILED - THBWiki detail page fetch failed
ALTER TABLE m_article ADD COLUMN thb_wiki_status VARCHAR(16) DEFAULT 'PENDING' COMMENT 'THBWiki匹配状态(PENDING/MATCHED/NOT_FOUND/FETCH_FAILED)' AFTER thb_wiki_url;

-- Backfill: any existing row with a sentinel string gets the matching status.
UPDATE m_article SET thb_wiki_status = 'NOT_FOUND'    WHERE thb_wiki_url = 'NOT_FOUND';
UPDATE m_article SET thb_wiki_status = 'FETCH_FAILED' WHERE thb_wiki_url = 'FETCH_FAILED';

-- A real URL still implies MATCHED (URL is non-null and not a sentinel).
UPDATE m_article SET thb_wiki_status = 'MATCHED'
WHERE thb_wiki_url IS NOT NULL
  AND thb_wiki_url <> ''
  AND thb_wiki_url <> 'NOT_FOUND'
  AND thb_wiki_url <> 'FETCH_FAILED';

-- Index for the "what's pending?" lookup used by OriginalUpdateJob.
CREATE INDEX idx_m_article_thb_wiki_status ON m_article (thb_wiki_status, id);
