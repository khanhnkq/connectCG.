-- =============================================================================
-- Insert PENDING Posts for Existing Group
-- Only insert posts into existing group with existing users
-- =============================================================================

-- Insert sample posts with PENDING status for Group 2 (Content)
INSERT INTO `posts` (`author_id`, `group_id`, `content`, `visibility`, `status`, `is_deleted`, `created_at`) VALUES
-- PENDING posts (waiting for approval)
(3, 2, 'Xin chào mọi người! Mình là thành viên mới của nhóm. Rất vui được tham gia cộng đồng này! 😊', 'PUBLIC', 'PENDING', FALSE, NOW() - INTERVAL 2 HOUR),
(4, 2, 'Có ai có kinh nghiệm làm việc với React và TypeScript không? Mình đang gặp vấn đề về type checking. Ai rảnh giúp mình với!', 'PUBLIC', 'PENDING', FALSE, NOW() - INTERVAL 1 HOUR),
(5, 2, 'Share khóa học lập trình miễn phí cho newbie. Mọi người tham khảo nhé!', 'PUBLIC', 'PENDING', FALSE, NOW() - INTERVAL 45 MINUTE),
(6, 2, 'Có ai biết địa chỉ quán cafe nào view đẹp ở Hà Nội không ạ? Mình muốn đi chụp ảnh cuối tuần này 📸', 'PUBLIC', 'PENDING', FALSE, NOW() - INTERVAL 30 MINUTE),
(8, 2, 'Chia sẻ kinh nghiệm du lịch Đà Lạt 3 ngày 2 đêm với budget 2 triệu. Ai cần thì inbox mình nhé!', 'PUBLIC', 'PENDING', FALSE, NOW() - INTERVAL 20 MINUTE),
(9, 2, 'Mình muốn tổ chức meetup cho các thành viên trong nhóm. Ai có hứng thú không?', 'PUBLIC', 'PENDING', FALSE, NOW() - INTERVAL 15 MINUTE),

-- APPROVED posts (for comparison)
(2, 2, 'Bài viết đã được duyệt - Hôm nay mình vừa hoàn thành dự án đầu tiên! Cảm ơn mọi người đã support 🎉', 'PUBLIC', 'APPROVED', FALSE, NOW() - INTERVAL 5 HOUR),
(3, 2, 'Bài viết đã được duyệt - Chia sẻ tips làm việc hiệu quả', 'PUBLIC', 'APPROVED', FALSE, NOW() - INTERVAL 4 HOUR),
(4, 2, 'Bài viết đã được duyệt - Review sản phẩm mới', 'PUBLIC', 'APPROVED', FALSE, NOW() - INTERVAL 3 HOUR),

-- REJECTED post (for testing)
(5, 2, 'Bài viết đã bị từ chối - [SPAM] Quảng cáo sản phẩm', 'PUBLIC', 'REJECTED', FALSE, NOW() - INTERVAL 6 HOUR);

-- Verify the inserted posts
SELECT 
    p.id,
    p.author_id,
    u.username as author_name,
    p.group_id,
    LEFT(p.content, 70) as content_preview,
    p.status,
    p.created_at
FROM posts p
JOIN users u ON p.author_id = u.id
WHERE p.group_id = 2
ORDER BY p.status, p.created_at DESC;

-- Count posts by status for group 2
SELECT 
    p.status,
    COUNT(*) as count
FROM posts p
WHERE p.group_id = 2 AND p.is_deleted = FALSE
GROUP BY p.status;

-- Tìm users có nhiều hơn 1 avatar current
SELECT user_id, COUNT(*) as count
FROM user_avatars
WHERE is_current = TRUE
GROUP BY user_id
HAVING COUNT(*) > 1;

-- Fix: Chỉ giữ lại avatar mới nhất
UPDATE user_avatars ua1
SET is_current = FALSE
WHERE is_current = TRUE
  AND EXISTS (
    SELECT 1 FROM user_avatars ua2
    WHERE ua2.user_id = ua1.user_id
      AND ua2.is_current = TRUE
      AND ua2.set_at > ua1.set_at
);