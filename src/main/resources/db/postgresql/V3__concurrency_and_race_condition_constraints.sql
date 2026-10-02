-- ====================================================================
-- V3: Concurrency and Race Condition DB Constraints
-- 1. Anti-duplicate PENDING friend requests between any user pair
-- 2. Canonical pair key & unique constraint for DIRECT chat rooms
-- ====================================================================

-- 1. Anti-duplicate pending friend requests
CREATE UNIQUE INDEX IF NOT EXISTS uq_friend_requests_pending_pair 
ON friend_requests (LEAST(sender_id, receiver_id), GREATEST(sender_id, receiver_id)) 
WHERE status = 'PENDING';

-- 2. Add canonical_pair_key column to chat_rooms
ALTER TABLE chat_rooms ADD COLUMN IF NOT EXISTS canonical_pair_key VARCHAR(100);

-- 3. Backfill canonical_pair_key for existing DIRECT chat rooms
UPDATE chat_rooms cr SET canonical_pair_key = (
    SELECT 'direct:' || LEAST(m1.user_id, m2.user_id) || ':' || GREATEST(m1.user_id, m2.user_id)
    FROM chat_room_members m1
    JOIN chat_room_members m2 ON m1.chat_room_id = m2.chat_room_id AND m1.user_id < m2.user_id
    WHERE m1.chat_room_id = cr.id
    LIMIT 1
)
WHERE cr.type = 'DIRECT' AND cr.canonical_pair_key IS NULL;

-- 4. Deduplicate any pre-existing duplicate DIRECT rooms before applying UNIQUE index
WITH ranked AS (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY canonical_pair_key ORDER BY id ASC) as rn
    FROM chat_rooms
    WHERE canonical_pair_key IS NOT NULL
)
UPDATE chat_rooms
SET canonical_pair_key = NULL, is_active = false
WHERE id IN (SELECT id FROM ranked WHERE rn > 1);

-- 5. Enforce unique canonical_pair_key for direct rooms
CREATE UNIQUE INDEX IF NOT EXISTS uq_chat_rooms_canonical_pair_key 
ON chat_rooms (canonical_pair_key) 
WHERE canonical_pair_key IS NOT NULL;
