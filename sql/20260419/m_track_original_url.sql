-- Add original_url column for THBWiki track links
-- Ticket: feat: Add originalUrl field to TrackBean and ThbwikiService matchAndSave
ALTER TABLE m_track ADD COLUMN original_url VARCHAR(1024) DEFAULT NULL COMMENT 'THBWiki原曲链接' AFTER original;
