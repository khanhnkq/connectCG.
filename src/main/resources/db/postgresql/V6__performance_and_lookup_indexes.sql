-- V6: Performance and lookup indexes for reports, posts, group_members, and friends

-- 1. Reports filtering & pagination indexes
CREATE INDEX IF NOT EXISTS idx_reports_created ON reports (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_reports_status_created ON reports (status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_reports_target_status_created ON reports (target_type, status, created_at DESC);

-- 2. Author posts feed & profile count index
CREATE INDEX IF NOT EXISTS idx_posts_author_status_created ON posts (author_id, status, is_deleted, created_at DESC);

-- 3. Group membership user lookup index (for user's joined groups)
CREATE INDEX IF NOT EXISTS idx_group_members_user_status ON group_members (user_id, status);

-- 4. Reverse friendship lookup index
CREATE INDEX IF NOT EXISTS idx_friends_friend_user ON friends (friend_id, user_id);
