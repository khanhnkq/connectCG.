-- V5: Add composite index for notification sorting and update allowed notification types constraint

-- 1. Create composite index on (user_id, created_at DESC) for fast notification retrieval
CREATE INDEX IF NOT EXISTS idx_notifications_user_created ON notifications (user_id, created_at DESC);

-- 2. Drop existing check constraint and replace with comprehensive list of supported notification types
ALTER TABLE notifications DROP CONSTRAINT IF EXISTS chk_notifications_type;

ALTER TABLE notifications 
ADD CONSTRAINT chk_notifications_type 
CHECK (type IN (
    -- Friend & User
    'FRIEND_REQUEST', 'FRIEND_ACCEPT', 'ROLE_CHANGE',

    -- Post & Content (Comment, Reply, Reaction, Moderation)
    'POST_COMMENT', 'COMMENT_REPLY', 'POST_REACTION',
    'POST_APPROVED', 'POST_REJECTED', 'POST_PENDING',

    -- Group Management
    'GROUP_INVITE', 'GROUP_INVITATION', 'GROUP_INVITE_ACCEPTED',
    'GROUP_JOIN_REQUEST', 'GROUP_JOIN_APPROVED', 'GROUP_JOIN_REJECTED',
    'GROUP_MEMBER_JOINED', 'GROUP_MEMBER_LEFT',
    'GROUP_BANNED', 'GROUP_UNBAN', 'GROUP_DELETED',
    'GROUP_OWNER_CHANGE', 'GROUP_ROLE_CHANGED', 'GROUP_KICK', 'GROUP_REJECTED',

    -- Reports & Moderation / Warnings
    'REPORT_SUBMITTED', 'REPORT_UPDATED',
    'WARNING', 'AI_STRIKE_WARNING', 'AI_STRIKE_BANNED',

    -- Chat, Legacy & General
    'MESSAGE', 'LIKE', 'COMMENT', 'SYSTEM', 'OTHER'
));
