-- Development-only sample data. Docker Compose includes this Flyway location;
-- tests and the default application configuration do not.
-- Password for all demo accounts: password123

INSERT INTO users (
    username, email, password_hash, role,
    is_locked, is_deleted, is_enabled, created_at
)
VALUES
    ('admin', 'admin@connect.com', '$2y$10$W.OlpYOQWzfb1qf.qCLUKO498WGwQrUzYtWQsVjB7E99ZtX6pHK8G', 'ADMIN', FALSE, FALSE, TRUE, CURRENT_TIMESTAMP - INTERVAL '30 days'),
    ('john_doe', 'john@connect.com', '$2y$10$W.OlpYOQWzfb1qf.qCLUKO498WGwQrUzYtWQsVjB7E99ZtX6pHK8G', 'USER', FALSE, FALSE, TRUE, CURRENT_TIMESTAMP - INTERVAL '20 days'),
    ('jane_smith', 'jane@connect.com', '$2y$10$W.OlpYOQWzfb1qf.qCLUKO498WGwQrUzYtWQsVjB7E99ZtX6pHK8G', 'USER', FALSE, FALSE, TRUE, CURRENT_TIMESTAMP - INTERVAL '18 days'),
    ('bob_wilson', 'bob@connect.com', '$2y$10$W.OlpYOQWzfb1qf.qCLUKO498WGwQrUzYtWQsVjB7E99ZtX6pHK8G', 'USER', FALSE, FALSE, TRUE, CURRENT_TIMESTAMP - INTERVAL '15 days'),
    ('alice_brown', 'alice@connect.com', '$2y$10$W.OlpYOQWzfb1qf.qCLUKO498WGwQrUzYtWQsVjB7E99ZtX6pHK8G', 'USER', FALSE, FALSE, TRUE, CURRENT_TIMESTAMP - INTERVAL '12 days'),
    ('charlie_davis', 'charlie@connect.com', '$2y$10$W.OlpYOQWzfb1qf.qCLUKO498WGwQrUzYtWQsVjB7E99ZtX6pHK8G', 'USER', FALSE, FALSE, TRUE, CURRENT_TIMESTAMP - INTERVAL '10 days')
ON CONFLICT (username) DO UPDATE SET
    role = EXCLUDED.role,
    is_enabled = TRUE,
    is_locked = FALSE,
    is_deleted = FALSE;

INSERT INTO user_profiles (
    user_id, city_code, city_name, full_name, date_of_birth,
    gender, bio, occupation, marital_status, looking_for
)
SELECT
    u.id, seed.city_code, seed.city_name, seed.full_name, seed.date_of_birth,
    seed.gender, seed.bio, seed.occupation, seed.marital_status, seed.looking_for
FROM (VALUES
    ('admin', '01', 'Hà Nội', 'Quản Trị Viên Hệ Thống', DATE '1990-01-01', 'OTHER', 'Tài khoản quản trị của Connect.', 'System Administrator', 'SINGLE', 'NETWORKING'),
    ('john_doe', '01', 'Hà Nội', 'John Doe', DATE '1995-05-15', 'MALE', 'Fullstack Engineer, yêu thích Spring Boot và React.', 'Software Engineer', 'SINGLE', 'NETWORKING'),
    ('jane_smith', '79', 'Hồ Chí Minh', 'Jane Smith', DATE '1997-08-22', 'FEMALE', 'Product Designer yêu sự tối giản và cà phê sữa đá.', 'Lead UI/UX Designer', 'SINGLE', 'FRIENDS'),
    ('bob_wilson', '48', 'Đà Nẵng', 'Bob Wilson', DATE '1993-03-10', 'MALE', 'Nhiếp ảnh gia và travel blogger.', 'Photographer', 'SINGLE', 'FRIENDS'),
    ('alice_brown', '01', 'Hà Nội', 'Alice Brown', DATE '1996-11-30', 'FEMALE', 'Đam mê truyền thông số, sách và công nghệ.', 'Marketing Manager', 'SINGLE', 'NETWORKING'),
    ('charlie_davis', '79', 'Hồ Chí Minh', 'Charlie Davis', DATE '1994-07-18', 'MALE', 'AI Researcher, quan tâm Machine Learning và LLM.', 'Data Analyst', 'SINGLE', 'FRIENDS')
) AS seed(username, city_code, city_name, full_name, date_of_birth, gender, bio, occupation, marital_status, looking_for)
JOIN users u ON u.username = seed.username
ON CONFLICT (user_id) DO UPDATE SET
    city_code = EXCLUDED.city_code,
    city_name = EXCLUDED.city_name,
    full_name = EXCLUDED.full_name,
    date_of_birth = EXCLUDED.date_of_birth,
    gender = EXCLUDED.gender,
    bio = EXCLUDED.bio,
    occupation = EXCLUDED.occupation,
    marital_status = EXCLUDED.marital_status,
    looking_for = EXCLUDED.looking_for;

INSERT INTO media (
    uploader_id, url, type, storage_provider, storage_bucket,
    object_key, content_type, category, uploaded_at
)
SELECT
    u.id, seed.url, 'IMAGE', 'external', 'demo',
    seed.username || '-avatar', 'image/jpeg', 'AVATAR', CURRENT_TIMESTAMP
FROM (VALUES
    ('admin', 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=300'),
    ('john_doe', 'https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=300'),
    ('jane_smith', 'https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=300'),
    ('bob_wilson', 'https://images.unsplash.com/photo-1570295999919-56ceb5ecca61?w=300'),
    ('alice_brown', 'https://images.unsplash.com/photo-1580489944761-15a19d654956?w=300'),
    ('charlie_davis', 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=300')
) AS seed(username, url)
JOIN users u ON u.username = seed.username
ON CONFLICT (storage_provider, storage_bucket, object_key) DO UPDATE SET
    url = EXCLUDED.url,
    uploader_id = EXCLUDED.uploader_id;

INSERT INTO user_avatars (user_id, media_id, is_current, set_at)
SELECT u.id, m.id, TRUE, CURRENT_TIMESTAMP
FROM users u
JOIN media m
  ON m.storage_provider = 'external'
 AND m.storage_bucket = 'demo'
 AND m.object_key = u.username || '-avatar'
WHERE u.username IN ('admin', 'john_doe', 'jane_smith', 'bob_wilson', 'alice_brown', 'charlie_davis')
  AND NOT EXISTS (
      SELECT 1 FROM user_avatars avatar
      WHERE avatar.user_id = u.id AND avatar.is_current = TRUE
  );

INSERT INTO user_hobbies (user_id, hobby_id)
SELECT u.id, h.id
FROM (VALUES
    ('john_doe', 'technology'), ('john_doe', 'gaming'), ('john_doe', 'reading'),
    ('jane_smith', 'art'), ('jane_smith', 'music'), ('jane_smith', 'travel'),
    ('bob_wilson', 'photography'), ('bob_wilson', 'travel'), ('bob_wilson', 'sports'),
    ('alice_brown', 'reading'), ('alice_brown', 'cooking'), ('alice_brown', 'technology'),
    ('charlie_davis', 'technology'), ('charlie_davis', 'gaming'), ('charlie_davis', 'fitness')
) AS seed(username, hobby_code)
JOIN users u ON u.username = seed.username
JOIN hobbies h ON h.code = seed.hobby_code
ON CONFLICT (user_id, hobby_id) DO NOTHING;

INSERT INTO friends (user_id, friend_id, created_at)
SELECT source_user.id, target_user.id, CURRENT_TIMESTAMP - INTERVAL '5 days'
FROM (VALUES
    ('john_doe', 'jane_smith'), ('jane_smith', 'john_doe'),
    ('john_doe', 'bob_wilson'), ('bob_wilson', 'john_doe'),
    ('john_doe', 'alice_brown'), ('alice_brown', 'john_doe'),
    ('jane_smith', 'alice_brown'), ('alice_brown', 'jane_smith')
) AS seed(source_username, target_username)
JOIN users source_user ON source_user.username = seed.source_username
JOIN users target_user ON target_user.username = seed.target_username
ON CONFLICT (user_id, friend_id) DO NOTHING;

INSERT INTO friend_requests (sender_id, receiver_id, status, created_at)
SELECT sender.id, receiver.id, 'PENDING', CURRENT_TIMESTAMP - INTERVAL '1 day'
FROM users sender
JOIN users receiver ON receiver.username = 'john_doe'
WHERE sender.username = 'charlie_davis'
  AND NOT EXISTS (
      SELECT 1 FROM friend_requests request
      WHERE request.sender_id = sender.id
        AND request.receiver_id = receiver.id
        AND request.status = 'PENDING'
  );

INSERT INTO groups (owner_id, name, description, privacy, created_at)
SELECT owner_user.id, seed.name, seed.description, 'PUBLIC', CURRENT_TIMESTAMP - INTERVAL '7 days'
FROM (VALUES
    ('john_doe', 'Cộng Đồng Lập Trình Viên Việt Nam', 'Trao đổi về Spring Boot, React, DevOps và kiến trúc phần mềm.'),
    ('jane_smith', 'Hội Thiết Kế UI/UX', 'Chia sẻ tài nguyên thiết kế, Design System và case study UX.'),
    ('bob_wilson', 'Nhiếp Ảnh & Khám Phá Việt Nam', 'Giao lưu ảnh đẹp, mẹo chụp ảnh và địa điểm du lịch.')
) AS seed(owner_username, name, description)
JOIN users owner_user ON owner_user.username = seed.owner_username
WHERE NOT EXISTS (SELECT 1 FROM groups existing_group WHERE existing_group.name = seed.name);

INSERT INTO group_members (group_id, user_id, role, status, joined_at)
SELECT g.id, u.id, seed.member_role, 'ACCEPTED', CURRENT_TIMESTAMP - INTERVAL '6 days'
FROM (VALUES
    ('Cộng Đồng Lập Trình Viên Việt Nam', 'john_doe', 'ADMIN'),
    ('Cộng Đồng Lập Trình Viên Việt Nam', 'jane_smith', 'MEMBER'),
    ('Cộng Đồng Lập Trình Viên Việt Nam', 'bob_wilson', 'MEMBER'),
    ('Cộng Đồng Lập Trình Viên Việt Nam', 'alice_brown', 'MEMBER'),
    ('Cộng Đồng Lập Trình Viên Việt Nam', 'charlie_davis', 'MEMBER'),
    ('Hội Thiết Kế UI/UX', 'jane_smith', 'ADMIN'),
    ('Hội Thiết Kế UI/UX', 'john_doe', 'MEMBER'),
    ('Hội Thiết Kế UI/UX', 'alice_brown', 'MEMBER'),
    ('Nhiếp Ảnh & Khám Phá Việt Nam', 'bob_wilson', 'ADMIN'),
    ('Nhiếp Ảnh & Khám Phá Việt Nam', 'john_doe', 'MEMBER'),
    ('Nhiếp Ảnh & Khám Phá Việt Nam', 'jane_smith', 'MEMBER')
) AS seed(group_name, username, member_role)
JOIN groups g ON g.name = seed.group_name
JOIN users u ON u.username = seed.username
ON CONFLICT (group_id, user_id) DO UPDATE SET
    role = EXCLUDED.role,
    status = EXCLUDED.status;

INSERT INTO posts (
    author_id, group_id, content, visibility, status, ai_status,
    is_pinned, comment_count, react_count, share_count, created_at
)
SELECT
    author_user.id, g.id, seed.content, 'PUBLIC', 'APPROVED', 'CLEAN',
    seed.is_pinned, seed.comment_count, seed.react_count, seed.share_count,
    CURRENT_TIMESTAMP - seed.age
FROM (VALUES
    ('admin', NULL::VARCHAR, '[DEMO] Chào mừng bạn đến với Connect! Hãy khám phá bảng tin, nhóm và kết nối với những người bạn mới.', TRUE, 2, 5, 1, INTERVAL '2 days'),
    ('john_doe', NULL::VARCHAR, '[DEMO] Vừa hoàn thành module Realtime Chat bằng WebSocket và Spring Boot. Có ai muốn xem bài chia sẻ chi tiết không?', FALSE, 3, 5, 2, INTERVAL '1 day'),
    ('jane_smith', NULL::VARCHAR, '[DEMO] Design System mới đã hỗ trợ Dark Mode và accessibility. Trải nghiệm tốt luôn bắt đầu từ những chi tiết nhỏ.', FALSE, 1, 3, 0, INTERVAL '12 hours'),
    ('bob_wilson', NULL::VARCHAR, '[DEMO] Hoàng hôn tại biển Mỹ Khê hôm nay thật rực rỡ. Một buổi chiều rất đáng nhớ!', FALSE, 2, 3, 1, INTERVAL '6 hours'),
    ('alice_brown', 'Cộng Đồng Lập Trình Viên Việt Nam', '[DEMO] Nhóm mình đang tìm Java/Spring Boot Developer và Frontend React cho một dự án mới.', FALSE, 1, 0, 0, INTERVAL '3 hours')
) AS seed(author_username, group_name, content, is_pinned, comment_count, react_count, share_count, age)
JOIN users author_user ON author_user.username = seed.author_username
LEFT JOIN groups g ON g.name = seed.group_name
WHERE NOT EXISTS (SELECT 1 FROM posts existing_post WHERE existing_post.content = seed.content);

INSERT INTO comments (post_id, author_id, parent_id, content, created_at)
SELECT p.id, author_user.id, NULL, seed.comment, CURRENT_TIMESTAMP - seed.age
FROM (VALUES
    ('[DEMO] Chào mừng bạn đến với Connect! Hãy khám phá bảng tin, nhóm và kết nối với những người bạn mới.', 'john_doe', 'Giao diện rất đẹp và mượt, cảm ơn đội ngũ admin!', INTERVAL '40 hours'),
    ('[DEMO] Chào mừng bạn đến với Connect! Hãy khám phá bảng tin, nhóm và kết nối với những người bạn mới.', 'jane_smith', 'Chúc Connect ngày càng phát triển!', INTERVAL '38 hours'),
    ('[DEMO] Vừa hoàn thành module Realtime Chat bằng WebSocket và Spring Boot. Có ai muốn xem bài chia sẻ chi tiết không?', 'jane_smith', 'Mình rất muốn xem phần authentication cho WebSocket.', INTERVAL '20 hours'),
    ('[DEMO] Vừa hoàn thành module Realtime Chat bằng WebSocket và Spring Boot. Có ai muốn xem bài chia sẻ chi tiết không?', 'charlie_davis', 'Đúng chủ đề mình đang tìm hiểu, mong bài chia sẻ!', INTERVAL '15 hours'),
    ('[DEMO] Vừa hoàn thành module Realtime Chat bằng WebSocket và Spring Boot. Có ai muốn xem bài chia sẻ chi tiết không?', 'bob_wilson', 'Nhớ chia sẻ cả phần reconnect nhé John.', INTERVAL '12 hours'),
    ('[DEMO] Design System mới đã hỗ trợ Dark Mode và accessibility. Trải nghiệm tốt luôn bắt đầu từ những chi tiết nhỏ.', 'alice_brown', 'Bộ màu và typography trông rất nhất quán.', INTERVAL '8 hours'),
    ('[DEMO] Hoàng hôn tại biển Mỹ Khê hôm nay thật rực rỡ. Một buổi chiều rất đáng nhớ!', 'jane_smith', 'Góc chụp đẹp quá Bob!', INTERVAL '4 hours'),
    ('[DEMO] Hoàng hôn tại biển Mỹ Khê hôm nay thật rực rỡ. Một buổi chiều rất đáng nhớ!', 'john_doe', 'Nhìn ảnh là muốn đi Đà Nẵng ngay.', INTERVAL '3 hours'),
    ('[DEMO] Nhóm mình đang tìm Java/Spring Boot Developer và Frontend React cho một dự án mới.', 'charlie_davis', 'Mình đã gửi thông tin qua email rồi nhé.', INTERVAL '2 hours')
) AS seed(post_content, author_username, comment, age)
JOIN posts p ON p.content = seed.post_content
JOIN users author_user ON author_user.username = seed.author_username
WHERE NOT EXISTS (
    SELECT 1 FROM comments existing_comment
    WHERE existing_comment.post_id = p.id AND existing_comment.content = seed.comment
);

INSERT INTO reactions (user_id, post_id, type, created_at)
SELECT u.id, p.id, seed.reaction_type, CURRENT_TIMESTAMP
FROM (VALUES
    ('john_doe', '[DEMO] Chào mừng bạn đến với Connect! Hãy khám phá bảng tin, nhóm và kết nối với những người bạn mới.', 'LOVE'),
    ('jane_smith', '[DEMO] Chào mừng bạn đến với Connect! Hãy khám phá bảng tin, nhóm và kết nối với những người bạn mới.', 'LIKE'),
    ('bob_wilson', '[DEMO] Chào mừng bạn đến với Connect! Hãy khám phá bảng tin, nhóm và kết nối với những người bạn mới.', 'LIKE'),
    ('alice_brown', '[DEMO] Chào mừng bạn đến với Connect! Hãy khám phá bảng tin, nhóm và kết nối với những người bạn mới.', 'LIKE'),
    ('charlie_davis', '[DEMO] Chào mừng bạn đến với Connect! Hãy khám phá bảng tin, nhóm và kết nối với những người bạn mới.', 'LOVE'),
    ('jane_smith', '[DEMO] Vừa hoàn thành module Realtime Chat bằng WebSocket và Spring Boot. Có ai muốn xem bài chia sẻ chi tiết không?', 'LOVE'),
    ('bob_wilson', '[DEMO] Vừa hoàn thành module Realtime Chat bằng WebSocket và Spring Boot. Có ai muốn xem bài chia sẻ chi tiết không?', 'LIKE'),
    ('alice_brown', '[DEMO] Vừa hoàn thành module Realtime Chat bằng WebSocket và Spring Boot. Có ai muốn xem bài chia sẻ chi tiết không?', 'LOVE'),
    ('charlie_davis', '[DEMO] Vừa hoàn thành module Realtime Chat bằng WebSocket và Spring Boot. Có ai muốn xem bài chia sẻ chi tiết không?', 'LIKE'),
    ('john_doe', '[DEMO] Design System mới đã hỗ trợ Dark Mode và accessibility. Trải nghiệm tốt luôn bắt đầu từ những chi tiết nhỏ.', 'LOVE'),
    ('bob_wilson', '[DEMO] Design System mới đã hỗ trợ Dark Mode và accessibility. Trải nghiệm tốt luôn bắt đầu từ những chi tiết nhỏ.', 'LIKE'),
    ('alice_brown', '[DEMO] Design System mới đã hỗ trợ Dark Mode và accessibility. Trải nghiệm tốt luôn bắt đầu từ những chi tiết nhỏ.', 'LOVE'),
    ('john_doe', '[DEMO] Hoàng hôn tại biển Mỹ Khê hôm nay thật rực rỡ. Một buổi chiều rất đáng nhớ!', 'LOVE'),
    ('jane_smith', '[DEMO] Hoàng hôn tại biển Mỹ Khê hôm nay thật rực rỡ. Một buổi chiều rất đáng nhớ!', 'LOVE'),
    ('alice_brown', '[DEMO] Hoàng hôn tại biển Mỹ Khê hôm nay thật rực rỡ. Một buổi chiều rất đáng nhớ!', 'LIKE')
) AS seed(username, post_content, reaction_type)
JOIN users u ON u.username = seed.username
JOIN posts p ON p.content = seed.post_content
ON CONFLICT (user_id, post_id) DO UPDATE SET type = EXCLUDED.type;

INSERT INTO chat_rooms (
    type, name, created_by, firebase_room_key,
    last_message_at, is_active, created_at
)
SELECT
    seed.room_type, seed.room_name, creator.id, seed.firebase_key,
    CURRENT_TIMESTAMP - seed.last_message_age, TRUE, CURRENT_TIMESTAMP - seed.created_age
FROM (VALUES
    ('DIRECT', 'John Doe - Jane Smith', 'john_doe', 'demo_direct_john_jane', INTERVAL '1 hour', INTERVAL '1 day'),
    ('GROUP', 'DevVN - Thảo Luận Công Nghệ', 'john_doe', 'demo_group_devvn', INTERVAL '30 minutes', INTERVAL '2 days')
) AS seed(room_type, room_name, creator_username, firebase_key, last_message_age, created_age)
JOIN users creator ON creator.username = seed.creator_username
ON CONFLICT (firebase_room_key) DO UPDATE SET
    name = EXCLUDED.name,
    last_message_at = EXCLUDED.last_message_at,
    is_active = TRUE;

INSERT INTO chat_room_members (chat_room_id, user_id, role, joined_at)
SELECT room.id, u.id, seed.member_role, CURRENT_TIMESTAMP - INTERVAL '1 day'
FROM (VALUES
    ('demo_direct_john_jane', 'john_doe', 'MEMBER'),
    ('demo_direct_john_jane', 'jane_smith', 'MEMBER'),
    ('demo_group_devvn', 'john_doe', 'ADMIN'),
    ('demo_group_devvn', 'jane_smith', 'MEMBER'),
    ('demo_group_devvn', 'alice_brown', 'MEMBER'),
    ('demo_group_devvn', 'charlie_davis', 'MEMBER')
) AS seed(firebase_key, username, member_role)
JOIN chat_rooms room ON room.firebase_room_key = seed.firebase_key
JOIN users u ON u.username = seed.username
ON CONFLICT (chat_room_id, user_id) DO UPDATE SET
    role = EXCLUDED.role,
    left_at = NULL;
