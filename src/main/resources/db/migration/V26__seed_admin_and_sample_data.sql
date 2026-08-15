-- =============================================================================
-- V26__seed_admin_and_sample_data.sql
-- Seed Admin account and comprehensive sample data for Connect Social Platform
-- Password for all accounts: password123
-- BCrypt Hash: $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy
-- =============================================================================

SET FOREIGN_KEY_CHECKS = 0;

-- -----------------------------------------------------------------------------
-- 1. SEED USERS (1 Admin + 5 Regular Users)
-- -----------------------------------------------------------------------------
INSERT INTO `users` (`id`, `username`, `email`, `password_hash`, `role`, `is_locked`, `is_deleted`, `is_enabled`, `created_at`)
VALUES
(1, 'admin', 'admin@connect.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'ADMIN', FALSE, FALSE, TRUE, NOW()),
(2, 'john_doe', 'john@connect.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'USER', FALSE, FALSE, TRUE, NOW()),
(3, 'jane_smith', 'jane@connect.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'USER', FALSE, FALSE, TRUE, NOW()),
(4, 'bob_wilson', 'bob@connect.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'USER', FALSE, FALSE, TRUE, NOW()),
(5, 'alice_brown', 'alice@connect.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'USER', FALSE, FALSE, TRUE, NOW()),
(6, 'charlie_davis', 'charlie@connect.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'USER', FALSE, FALSE, TRUE, NOW())
ON DUPLICATE KEY UPDATE 
    `role` = VALUES(`role`),
    `is_enabled` = VALUES(`is_enabled`),
    `password_hash` = VALUES(`password_hash`);

-- -----------------------------------------------------------------------------
-- 2. SEED USER PROFILES
-- -----------------------------------------------------------------------------
INSERT INTO `user_profiles` (`id`, `user_id`, `city_code`, `city_name`, `full_name`, `date_of_birth`, `gender`, `bio`, `occupation`, `marital_status`, `looking_for`)
VALUES
(1, 1, '01', 'Hà Nội', 'Quản Trị Viên Hệ Thống', '1990-01-01', 'OTHER', 'Tài khoản quản trị chính thức của mạng xã hội Connect.', 'System Administrator', 'SINGLE', 'NETWORKING'),
(2, 2, '01', 'Hà Nội', 'John Doe', '1995-05-15', 'MALE', 'Senior Fullstack Engineer. Đam mê Spring Boot, React và chia sẻ kiến thức công nghệ.', 'Software Engineer', 'SINGLE', 'NETWORKING'),
(3, 3, '79', 'Hồ Chí Minh', 'Jane Smith', '1997-08-22', 'FEMALE', 'Product Designer yêu cái đẹp, sự tối giản và cà phê sữa đá.', 'Lead UI/UX Designer', 'SINGLE', 'FRIENDS'),
(4, 4, '48', 'Đà Nẵng', 'Bob Wilson', '1993-03-10', 'MALE', 'Nhiếp ảnh gia và travel blogger. Thích lưu giữ những khoảnh khắc đẹp của cuộc sống.', 'Photographer', 'SINGLE', 'FRIENDS'),
(5, 5, '01', 'Hà Nội', 'Alice Brown', '1996-11-30', 'FEMALE', 'Marketing Lead. Đam mê truyền thông số, sách và công nghệ mới.', 'Marketing Manager', 'SINGLE', 'NETWORKING'),
(6, 6, '79', 'Hồ Chí Minh', 'Charlie Davis', '1994-07-18', 'MALE', 'AI Researcher & Data Analyst. Thích nghiên cứu Machine Learning và Large Language Models.', 'Data Analyst', 'SINGLE', 'FRIENDS')
ON DUPLICATE KEY UPDATE 
    `full_name` = VALUES(`full_name`),
    `city_name` = VALUES(`city_name`),
    `bio` = VALUES(`bio`),
    `occupation` = VALUES(`occupation`);

-- -----------------------------------------------------------------------------
-- 3. SEED MEDIA & AVATARS
-- -----------------------------------------------------------------------------
INSERT INTO `media` (`id`, `uploader_id`, `url`, `type`, `uploaded_at`)
VALUES
(1, 1, 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=300', 'IMAGE', NOW()),
(2, 2, 'https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=300', 'IMAGE', NOW()),
(3, 3, 'https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=300', 'IMAGE', NOW()),
(4, 4, 'https://images.unsplash.com/photo-1570295999919-56ceb5ecca61?w=300', 'IMAGE', NOW()),
(5, 5, 'https://images.unsplash.com/photo-1580489944761-15a19d654956?w=300', 'IMAGE', NOW()),
(6, 6, 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=300', 'IMAGE', NOW()),
(10, 3, 'https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=800', 'IMAGE', NOW()),
(11, 4, 'https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=800', 'IMAGE', NOW())
ON DUPLICATE KEY UPDATE `url` = VALUES(`url`);

INSERT INTO `user_avatars` (`id`, `user_id`, `media_id`, `is_current`, `set_at`)
VALUES
(1, 1, 1, TRUE, NOW()),
(2, 2, 2, TRUE, NOW()),
(3, 3, 3, TRUE, NOW()),
(4, 4, 4, TRUE, NOW()),
(5, 5, 5, TRUE, NOW()),
(6, 6, 6, TRUE, NOW())
ON DUPLICATE KEY UPDATE `media_id` = VALUES(`media_id`);

-- -----------------------------------------------------------------------------
-- 4. SEED FRIENDSHIPS (Hai chiều) & FRIEND REQUESTS
-- -----------------------------------------------------------------------------
-- John Doe (2) kết bạn với Jane (3), Bob (4), Alice (5)
INSERT IGNORE INTO `friends` (`user_id`, `friend_id`, `created_at`)
VALUES
(2, 3, NOW()), (3, 2, NOW()),
(2, 4, NOW()), (4, 2, NOW()),
(2, 5, NOW()), (5, 2, NOW()),
(3, 5, NOW()), (5, 3, NOW());

-- Lời mời kết bạn đang chờ: Charlie (6) gửi cho John Doe (2)
INSERT IGNORE INTO `friend_requests` (`id`, `sender_id`, `receiver_id`, `status`, `created_at`)
VALUES
(1, 6, 2, 'PENDING', NOW());

-- -----------------------------------------------------------------------------
-- 5. SEED GROUPS & GROUP MEMBERS
-- -----------------------------------------------------------------------------
INSERT INTO `groups` (`id`, `owner_id`, `name`, `description`, `privacy`, `created_at`)
VALUES
(1, 2, 'Cộng Đồng Lập Trình Viên Việt Nam (DevVN)', 'Nơi giao lưu, trao đổi kiến thức về Spring Boot, React, DevOps, Cloud và kiến trúc phần mềm.', 'PUBLIC', NOW()),
(2, 3, 'Hội Thiết Kế UI/UX & Design Systems', 'Chia sẻ tài nguyên Figma, các case study thiết kế trải nghiệm người dùng và xu hướng UI hiện đại.', 'PUBLIC', NOW()),
(3, 4, 'Nhiếp Ảnh & Khám Phá Việt Nam', 'Giao lưu ảnh phong cảnh, chân dung, chia sẻ mẹo chụp ảnh và địa điểm du lịch tuyệt đẹp.', 'PUBLIC', NOW())
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `description` = VALUES(`description`);

INSERT IGNORE INTO `group_members` (`group_id`, `user_id`, `role`, `status`, `joined_at`)
VALUES
-- Group 1
(1, 2, 'ADMIN', 'ACCEPTED', NOW()),
(1, 3, 'MEMBER', 'ACCEPTED', NOW()),
(1, 4, 'MEMBER', 'ACCEPTED', NOW()),
(1, 5, 'MEMBER', 'ACCEPTED', NOW()),
(1, 6, 'MEMBER', 'ACCEPTED', NOW()),
-- Group 2
(2, 3, 'ADMIN', 'ACCEPTED', NOW()),
(2, 2, 'MEMBER', 'ACCEPTED', NOW()),
(2, 5, 'MEMBER', 'ACCEPTED', NOW()),
-- Group 3
(3, 4, 'ADMIN', 'ACCEPTED', NOW()),
(3, 2, 'MEMBER', 'ACCEPTED', NOW()),
(3, 3, 'MEMBER', 'ACCEPTED', NOW());

-- -----------------------------------------------------------------------------
-- 6. SEED POSTS (Newsfeed & Groups)
-- -----------------------------------------------------------------------------
INSERT INTO `posts` (`id`, `author_id`, `group_id`, `content`, `visibility`, `status`, `ai_status`, `is_pinned`, `comment_count`, `react_count`, `share_count`, `created_at`)
VALUES
(1, 1, NULL, '📢 [THÔNG BÁO QUẢN TRỊ] Chào mừng các thành viên đến với mạng xã hội Connect! Hệ thống đã được nâng cấp toàn diện với kiến trúc Microservices, Realtime WebSocket và Kiểm duyệt AI tự động. Chúc mọi người có trải nghiệm tuyệt vời!', 'PUBLIC', 'APPROVED', 'CLEAN', TRUE, 2, 5, 1, NOW() - INTERVAL 2 DAY),

(2, 2, NULL, 'Hôm nay mình vừa tối ưu xong module Realtime Chat bằng WebSocket (STOMP + SockJS) kết hợp Spring Boot 3. Tốc độ phản hồi dưới 20ms cực mượt! Có anh em nào quan tâm đến kiến trúc này không, mình sẽ viết bài chia sẻ chi tiết nhé 🚀🔥', 'PUBLIC', 'APPROVED', 'CLEAN', FALSE, 3, 8, 2, NOW() - INTERVAL 1 DAY),

(3, 3, NULL, 'Vừa hoàn thành bộ Design System mới với tông màu hiện đại, hỗ trợ chuẩn Dark Mode và Accessible WCAG. Thiết kế giao diện không chỉ là làm cho đẹp mà là tạo ra trải nghiệm mượt mà nhất cho người dùng 🎨✨', 'PUBLIC', 'APPROVED', 'CLEAN', FALSE, 1, 6, 0, NOW() - INTERVAL 12 HOUR),

(4, 4, NULL, 'Hoàng hôn chiều nay tại bãi biển Mỹ Khê - Đà Nẵng. Ánh nắng rực rỡ cuối ngày luôn mang lại cảm giác bình yên đến lạ 📸🌊', 'PUBLIC', 'APPROVED', 'CLEAN', FALSE, 2, 9, 3, NOW() - INTERVAL 6 HOUR),

(5, 5, 1, '🔥 [DevVN Tuyển Dụng] Team mình đang tìm kiếm 02 Senior Java/Spring Boot Developer & 01 Frontend React/Vite cho dự án FinTech thế hệ mới. Môi trường hybrid, đãi ngộ hấp dẫn. Các bạn quan tâm comment hoặc inbox mình nhé!', 'PUBLIC', 'APPROVED', 'CLEAN', FALSE, 1, 4, 1, NOW() - INTERVAL 3 HOUR)
ON DUPLICATE KEY UPDATE `content` = VALUES(`content`), `status` = VALUES(`status`);

-- Gắn ảnh cho bài viết số 3 và số 4
INSERT IGNORE INTO `post_media` (`post_id`, `media_id`, `display_order`)
VALUES
(3, 10, 0),
(4, 11, 0);

-- -----------------------------------------------------------------------------
-- 7. SEED COMMENTS & REPLIES
-- -----------------------------------------------------------------------------
INSERT INTO `comments` (`id`, `post_id`, `author_id`, `parent_id`, `content`, `created_at`)
VALUES
-- Comment cho bài viết số 1 (Admin)
(1, 1, 2, NULL, 'Giao diện mới rất đẹp và mượt mà! Cảm ơn đội ngũ admin 👏', NOW() - INTERVAL 40 HOUR),
(2, 1, 3, NULL, 'Ủng hộ Connect phát triển mạnh mẽ hơn nữa 🎉', NOW() - INTERVAL 38 HOUR),

-- Comment cho bài viết số 2 (John Doe)
(3, 2, 3, NULL, 'Bài viết rất hữu ích John ơi, chia sẻ luôn phần xử lý authentication token qua WebSocket handshake nhé!', NOW() - INTERVAL 20 HOUR),
(4, 2, 2, 3, '@Jane Smith Nhất trí luôn Jane, mình sẽ đưa cả code mẫu interceptor vào bài viết nhé!', NOW() - INTERVAL 18 HOUR),
(5, 2, 6, NULL, 'Tuyệt vời anh John, em đang rất cần tài liệu này để tham khảo cho dự án trường!', NOW() - INTERVAL 15 HOUR),

-- Comment cho bài viết số 4 (Bob Wilson)
(6, 4, 3, NULL, 'Góc chụp đỉnh quá anh Bob ơi! Màu hoàng hôn tuyệt đẹp 🌅', NOW() - INTERVAL 4 HOUR),
(7, 4, 2, NULL, 'Nhìn ảnh lại muốn bay vào Đà Nẵng ngay và luôn ✈️', NOW() - INTERVAL 3 HOUR),

-- Comment cho bài viết tuyển dụng số 5 (Alice Brown)
(8, 5, 6, NULL, 'Em đã gửi CV qua email rồi chị Alice check giúp em nhé!', NOW() - INTERVAL 2 HOUR)
ON DUPLICATE KEY UPDATE `content` = VALUES(`content`);

-- -----------------------------------------------------------------------------
-- 8. SEED REACTIONS (LIKE, LOVE)
-- -----------------------------------------------------------------------------
INSERT IGNORE INTO `reactions` (`user_id`, `post_id`, `type`, `created_at`)
VALUES
(2, 1, 'LOVE', NOW()),
(3, 1, 'LIKE', NOW()),
(4, 1, 'LIKE', NOW()),
(5, 1, 'LIKE', NOW()),
(6, 1, 'LOVE', NOW()),

(1, 2, 'LIKE', NOW()),
(3, 2, 'LOVE', NOW()),
(4, 2, 'LIKE', NOW()),
(5, 2, 'LOVE', NOW()),
(6, 2, 'LIKE', NOW()),

(2, 3, 'LOVE', NOW()),
(4, 3, 'LIKE', NOW()),
(5, 3, 'LOVE', NOW()),

(2, 4, 'LOVE', NOW()),
(3, 4, 'LOVE', NOW()),
(5, 4, 'LIKE', NOW());

-- -----------------------------------------------------------------------------
-- 9. SEED CHAT ROOMS & MEMBERS
-- -----------------------------------------------------------------------------
INSERT INTO `chat_rooms` (`id`, `type`, `name`, `created_by`, `firebase_room_key`, `last_message_at`, `is_active`, `created_at`)
VALUES
(1, 'DIRECT', 'John Doe - Jane Smith', 2, 'direct_room_user_2_3', NOW() - INTERVAL 1 HOUR, TRUE, NOW() - INTERVAL 1 DAY),
(2, 'GROUP', 'DevVN - Thảo Luận Công Nghệ', 2, 'group_room_devvn_community', NOW() - INTERVAL 30 MINUTE, TRUE, NOW() - INTERVAL 2 DAY)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

INSERT IGNORE INTO `chat_room_members` (`chat_room_id`, `user_id`, `role`, `joined_at`)
VALUES
-- Room 1 (Direct)
(1, 2, 'MEMBER', NOW() - INTERVAL 1 DAY),
(1, 3, 'MEMBER', NOW() - INTERVAL 1 DAY),
-- Room 2 (Group)
(2, 2, 'ADMIN', NOW() - INTERVAL 2 DAY),
(2, 3, 'MEMBER', NOW() - INTERVAL 2 DAY),
(2, 5, 'MEMBER', NOW() - INTERVAL 2 DAY),
(2, 6, 'MEMBER', NOW() - INTERVAL 2 DAY);

-- Bật lại kiểm tra khóa ngoại
SET FOREIGN_KEY_CHECKS = 1;
