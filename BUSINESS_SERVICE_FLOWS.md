# TÀI LIỆU LUỒNG NGHIỆP VỤ CÁC BUSINESS SERVICE (BUSINESS SERVICE FLOWS)
**Dự án:** Mạng xã hội Connect (ConnectCG)  
**Công nghệ:** Spring Boot 3, Spring Security (JWT & OAuth2), Spring Data JPA, WebSocket (STOMP), Gemini AI Moderation, Firebase Realtime Database, MySQL/PostgreSQL.

---

## MỤC LỤC
1. [Sơ Đồ Kiến Trúc Hệ Thống Tổng Thể](#1-sơ-đồ-kiến-trúc-hệ-thống-tổng-thể)
2. [Module 1: Xác Thực & Quản Lý Tài Khoản (Authentication Flows)](#2-module-1-xác-thực--quản-lý-tài-khoản-authentication-flows)
3. [Module 2: Quản Lý Bài Viết & Kiểm Duyệt AI (Post & AI Moderation Flows)](#3-module-2-quản-lý-bài-viết--kiểm-duyệt-ai-post--ai-moderation-flows)
4. [Module 3: Bình Luận & Cảm Xúc (Comment & Reaction Flows)](#4-module-3-bình-luận--cảm-xúc-comment--reaction-flows)
5. [Module 4: Quản Lý Hội Nhóm (Group & Community Flows)](#5-module-4-quản-lý-hội-nhóm-group--community-flows)
6. [Module 5: Bạn Bè & Thuật Toán Gợi Ý (Friend System & Suggestion Flows)](#6-module-5-bạn-bè--thuật-toán-gợi-ý-friend-system--suggestion-flows)
7. [Module 6: Nhắn Tin Thời Gian Thực & Mã Hóa E2EE (Chat & E2EE Flows)](#7-module-6-nhắn-tin-thời-gian-thực--mã-hóa-e2ee-chat--e2ee-flows)
8. [Module 7: Trạng Thái Hoạt Động (Presence Flows)](#8-module-7-trạng-thái-hoạt-động-presence-flows)
9. [Module 8: Hồ Sơ Cá Nhân & Tìm Kiếm Thành Viên (Profile & Discovery Flows)](#9-module-8-hồ-sơ-cá-nhân--tìm-kiếm-thành-viên-profile--discovery-flows)
10. [Module 9: Hệ Thống Thông Báo Thời Gian Thực (Notification Flows)](#10-module-9-hệ-thống-thông-báo-thời-gian-thực-notification-flows)
11. [Module 10: Báo Cáo Vi Phạm & Quản Trị Hệ Thống (Report & Admin Flows)](#11-module-10-báo-cáo-vi-phạm--quản-trị-hệ-thống-report--admin-flows)
12. [Bảng Kênh STOMP WebSocket Đầy Đủ](#12-bảng-kênh-stomp-websocket-đầy-đủ)

---

## 1. SƠ ĐỒ KIẾN TRÚC HỆ THỐNG TỔNG THỂ

```mermaid
graph TD
    Client["Client (Web Browser / React / Vite)"]

    subgraph Security_Layer ["Security & Interceptors Layer"]
        JwtFilter["JwtAuthenticationFilter"]
        OAuth2Filter["CustomOAuth2UserService / SuccessHandler"]
        WsInterceptor["WebSocketAuthInterceptor / JwtHandshakeInterceptor"]
    end

    subgraph Controller_Layer ["REST & WebSocket Controller Layer"]
        AuthController["AuthController"]
        PostController["PostController"]
        CommentController["CommentController"]
        GroupController["GroupController"]
        FriendController["FriendRestController / FriendRequestController / FriendSuggestionController"]
        ChatController["ChatController / ChatWebSocketController"]
        ProfileController["UserProfileController / HobbyController"]
        NotiController["TungNotificationController"]
        ReportController["ReportController"]
        AdminController["AdminUserManagerController"]
        OnlineController["OnlineStatusController"]
    end

    subgraph Service_Layer ["Core Business Service Layer"]
        AuthSvc["AuthService / EmailService"]
        PostSvc["PostService / PostMediaService"]
        AiSvc["AiModerationService (GeminiAiServiceImpl)"]
        CommentSvc["CommentService"]
        ReactSvc["ReactionService"]
        GroupSvc["GroupService / GroupMemberService"]
        FriendSvc["FriendService / FriendRequestService"]
        SuggestionSvc["FriendSuggestionService / FriendSuggestionScheduler"]
        ChatSvc["ChatRoomService / UserPublicKeyService"]
        OnlineSvc["OnlineUserService / WebSocketEventListener"]
        ProfileSvc["UserProfileService / UserAvatarService / UserCoverService / UserHobbyService"]
        NotiSvc["NotificationService"]
        ReportSvc["ReportService"]
        AdminSvc["AdminUserService / UserService"]
    end

    subgraph Infrastructure ["Data & Messaging Infrastructure"]
        RDBMS[(Database MySQL / PostgreSQL)]
        Firebase[(Firebase Realtime Database)]
        GeminiAPI["Google Gemini 1.5 Flash API"]
        SMTPMail["SMTP Mail Server"]
        STOMPBroker["STOMP WebSocket Message Broker"]
    end

    Client --> Security_Layer
    Security_Layer --> Controller_Layer
    Controller_Layer --> Service_Layer
    Service_Layer --> RDBMS
    Service_Layer --> Firebase
    Service_Layer --> GeminiAPI
    Service_Layer --> SMTPMail
    Service_Layer --> STOMPBroker
    STOMPBroker --> Client
```

---

## 2. MODULE 1: XÁC THỰC & QUẢN LÝ TÀI KHOẢN (AUTHENTICATION FLOWS)

### 2.1 Luồng Đăng Ký & Kích Hoạt Tài Khoản Qua Email (Registration & Verification)
```mermaid
sequenceDiagram
    autonumber
    actor User as Người dùng
    participant AuthCtrl as AuthController
    participant AuthSvc as AuthServiceImpl
    participant EmailSvc as EmailService
    participant DB as Database
    participant SMTP as Mail Server

    User->>AuthCtrl: POST /api/v1/auth/register (username, email, password)
    AuthCtrl->>AuthSvc: register(RegisterRequest)
    AuthSvc->>DB: Kiểm tra existsByUsername & existsByEmail
    alt Tên đăng nhập hoặc Email đã tồn tại
        AuthSvc-->>AuthCtrl: Throw RuntimeException
        AuthCtrl-->>User: 400 Bad Request
    else Dữ liệu hợp lệ
        AuthSvc->>AuthSvc: PasswordEncoder.encode(password)
        AuthSvc->>DB: Lưu User (isEnabled = false, isLocked = false, role = 'USER')
        AuthSvc->>DB: Tạo VerificationToken (UUID, expiry = 15 phút)
        AuthSvc->>EmailSvc: sendHtmlMessage(email, subject, htmlBody)
        EmailSvc->>SMTP: Gửi email chứa link: /verify-email?token={token}
        AuthSvc-->>AuthCtrl: Trả về User
        AuthCtrl-->>User: 200 OK ("User registered successfully!")
    end

    Note over User, DB: Người dùng bấm vào link xác thực trong hộp thư
    User->>AuthCtrl: GET /api/v1/auth/verify-email?token={token}
    AuthCtrl->>AuthSvc: verifyEmail(token)
    AuthSvc->>DB: Tìm VerificationToken theo token
    alt Token không hợp lệ hoặc hết hạn (>15 phút)
        AuthSvc-->>AuthCtrl: Throw RuntimeException ("Token không hợp lệ hoặc đã hết hạn")
        AuthCtrl-->>User: 400 Bad Request
    else Token hợp lệ
        AuthSvc->>DB: Cập nhật user.isEnabled = true
        AuthSvc->>DB: Xóa VerificationToken
        AuthCtrl-->>User: 200 OK ("Xác thực email thành công! Bạn có thể đăng nhập ngay.")
    end
```

### 2.2 Luồng Đăng Nhập & Tạo JWT Token (Login & Token Generation)
```mermaid
sequenceDiagram
    autonumber
    actor User as Người dùng
    participant AuthCtrl as AuthController
    participant AuthMgr as AuthenticationManager
    participant JwtProv as JwtTokenProvider
    participant ProfileRepo as UserProfileRepository
    participant DB as Database

    User->>AuthCtrl: POST /api/v1/auth/login (username, password)
    AuthCtrl->>AuthMgr: authenticate(UsernamePasswordAuthenticationToken)
    AuthMgr->>DB: Load User by username & Verify password hash
    
    alt Tài khoản bị Disable (isEnabled = false)
        AuthMgr-->>AuthCtrl: Throw DisabledException
        AuthCtrl-->>User: 401 Unauthorized ("Tài khoản chưa được kích hoạt")
    else Sai thông tin đăng nhập
        AuthMgr-->>AuthCtrl: Throw BadCredentialsException
        AuthCtrl-->>User: 401 Unauthorized ("Sai tên đăng nhập hoặc mật khẩu")
    else Xác thực thành công
        AuthMgr-->>AuthCtrl: Trả về Authentication (UserPrincipal)
        AuthCtrl->>JwtProv: generateToken(userPrincipal)
        JwtProv-->>AuthCtrl: Chuỗi JWT Access Token (hạn 24h)
        AuthCtrl->>ProfileRepo: existsByUserId(userPrincipal.getId())
        ProfileRepo-->>AuthCtrl: hasProfile (true/false), fullName
        AuthCtrl-->>User: 200 OK (JwtResponse: token, role, hasProfile, fullName)
    end
```

### 2.3 Luồng Đăng Nhập Mạng Xã Hội (OAuth2 Login - Google)
```mermaid
sequenceDiagram
    autonumber
    actor User as Người dùng
    participant OAuthFilter as CustomOAuth2UserService
    participant Google as Google OAuth2 Provider
    participant SuccessHandler as OAuth2LoginSuccessHandler
    participant JwtProv as JwtTokenProvider
    participant DB as Database

    User->>Google: Đăng nhập & Ủy quyền tài khoản Google
    Google-->>OAuthFilter: Trả về OAuth2User Profile (email, name, sub)
    OAuthFilter->>DB: Tìm User theo email
    alt User chưa tồn tại
        OAuthFilter->>DB: Tạo User mới (email, username tự sinh, isEnabled = true, role = 'USER')
        OAuthFilter->>DB: Tạo UserProfile mặc định (fullName)
    end
    OAuthFilter-->>SuccessHandler: onAuthenticationSuccess()
    SuccessHandler->>JwtProv: generateToken(userPrincipal)
    SuccessHandler-->>User: Redirect về Frontend: /oauth2/redirect?token={jwt}
```

### 2.4 Luồng Khởi Tạo Hồ Sơ Sau Đăng Ký (Onboarding Profile Creation)
```mermaid
sequenceDiagram
    autonumber
    actor User as Người dùng mới
    participant AuthCtrl as AuthController
    participant AuthSvc as AuthServiceImpl
    participant ProfileRepo as UserProfileRepository
    participant HobbyRepo as UserHobbyRepository
    participant MediaRepo as MediaRepository
    participant AvatarRepo as UserAvatarRepository

    User->>AuthCtrl: POST /api/v1/auth/profile (fullName, gender, bio, occupation, dob, cityCode, cityName, hobbyIds, avatarUrl)
    AuthCtrl->>AuthSvc: createProfile(request, currentUsername)
    AuthSvc->>ProfileRepo: existsByUserId(userId)
    alt Đã có profile
        AuthSvc-->>AuthCtrl: Throw Exception ("Người dùng đã có thông tin cá nhân!")
    else Chưa có profile
        AuthSvc->>ProfileRepo: Lưu UserProfile (fullName, gender, bio, occupation, dob, cityCode, cityName, lookingFor)
        opt Có danh sách hobbyIds
            loop Từng hobbyId
                AuthSvc->>HobbyRepo: Lưu UserHobby (userId, hobbyId)
            end
        end
        opt Có avatarUrl
            AuthSvc->>MediaRepo: Tạo Media (type = 'IMAGE', url = avatarUrl)
            AuthSvc->>AvatarRepo: Tạo UserAvatar (isCurrent = true, media, user)
        end
        AuthSvc-->>AuthCtrl: Trả về UserProfile đã lưu
        AuthCtrl-->>User: 200 OK ("Profile created successfully!")
    end
```

### 2.5 Luồng Quên Mật Khẩu & Đặt Lại Mật Khẩu (Forgot & Reset Password)
```mermaid
sequenceDiagram
    autonumber
    actor User as Người dùng
    participant AuthCtrl as AuthController
    participant AuthSvc as AuthServiceImpl
    participant EmailSvc as EmailService
    participant DB as Database
    participant SMTP as Mail Server

    User->>AuthCtrl: POST /api/v1/auth/forgot-password?email={email}
    AuthCtrl->>AuthSvc: forgotPassword(email)
    AuthSvc->>DB: Tìm User theo email & kiểm tra isEnabled == true
    AuthSvc->>DB: Tạo PasswordResetToken (UUID, expiry = 10 phút)
    AuthSvc->>EmailSvc: sendHtmlMessage(email, templateHtml chứa link /reset-password?token=...)
    EmailSvc->>SMTP: Gửi email đặt lại mật khẩu
    AuthCtrl-->>User: 200 OK ("Email sent")

    Note over User, DB: Người dùng nhập mật khẩu mới
    User->>AuthCtrl: POST /api/v1/auth/reset-password (token, newPassword)
    AuthCtrl->>AuthSvc: resetPassword(token, newPassword)
    AuthSvc->>DB: Tìm PasswordResetToken & kiểm tra còn hạn
    AuthSvc->>AuthSvc: PasswordEncoder.encode(newPassword)
    AuthSvc->>DB: Cập nhật password_hash của User
    AuthSvc->>DB: Xóa PasswordResetToken (ngăn tái sử dụng)
    AuthCtrl-->>User: 200 OK ("Password updated")
```

---

## 3. MODULE 2: QUẢN LÝ BÀI VIẾT & KIỂM DUYỆT AI (POST & AI MODERATION FLOWS)

### 3.1 Cây Quyết Định Kiểm Duyệt Nội Dung (AI Moderation Decision Flowchart)
```mermaid
flowchart TD
    Start(["Bắt đầu tạo/sửa bài viết"]) --> CheckPrivileged{"Tác giả là Admin hệ thống,<br>Owner nhóm, hoặc Admin nhóm?"}
    
    CheckPrivileged -- Có (Privileged) --> AutoApprove["Trạng thái: <b>APPROVED</b><br>aiStatus: <b>SAFE</b><br>aiScore: <b>0.0</b>"]
    
    CheckPrivileged -- Không --> CheckScope{"Phạm vi bài viết là gì?"}
    
    CheckScope -- "Bài viết cá nhân PRIVATE/FRIENDS" --> AutoApproveUnchecked["Trạng thái: <b>APPROVED</b><br>aiStatus: <b>NOT_CHECKED</b><br>aiScore: <b>0.0</b>"]
    
    CheckScope -- "Bài viết trong NHÓM (Group) hoặc PUBLIC" --> CallGemini["Gửi nội dung tới Gemini 1.5 Flash AI API<br>(Prompt phân tích từ ngữ thô tục/độc hại tiếng Việt)"]
    
    CallGemini --> CheckAPI{"Gemini API phản hồi thành công?"}
    
    CheckAPI -- Có --> ParseResult["Đọc JSON: label (SAFE/TOXIC), reason"]
    ParseResult --> CheckScore{"Score < 0.6?<br>(SAFE = 0.1, TOXIC = 0.9)"}
    
    CheckScore -- "Score < 0.6 (SAFE)" --> SetApproved["Trạng thái: <b>APPROVED</b><br>aiStatus: <b>SAFE</b><br>Phát STOMP: /topic/posts (CREATED)"]
    CheckScore -- "Score >= 0.6 (TOXIC)" --> SetPending["Trạng thái: <b>PENDING</b><br>aiStatus: <b>TOXIC</b><br>Gửi thông báo POST_PENDING cho tác giả<br>Đưa vào hàng đợi duyệt của Admin"]
    
    CheckAPI -- "Lỗi mạng / Timeout" --> FailSafe["Fail-safe kích hoạt:<br>aiStatus: <b>AI_ERROR</b><br>aiScore: <b>0.9</b><br>Trạng thái: <b>PENDING</b> (Chờ duyệt thủ công)"]
    FailSafe --> SetPending
```

### 3.2 Luồng Tạo Bài Viết Chi Tiết (Create Post Sequence)
```mermaid
sequenceDiagram
    autonumber
    actor User as Tác giả
    participant PostCtrl as PostController
    participant PostSvc as PostServiceImpl
    participant GroupRepo as GroupRepository
    participant MemberRepo as GroupMemberRepository
    participant AiSvc as GeminiAiServiceImpl
    participant PostRepo as PostRepository
    participant MediaRepo as MediaRepository
    participant PostMediaRepo as PostMediaRepository
    participant Ws as SimpMessagingTemplate (/topic/posts)
    participant NotiSvc as NotificationService

    User->>PostCtrl: POST /api/posts (content, visibility, mediaUrls, groupId)
    PostCtrl->>PostSvc: createPost(request, skipAiCheck, userId)

    opt Đăng bài vào Nhóm (groupId != null)
        PostSvc->>GroupRepo: Tìm Group theo groupId
        PostSvc->>MemberRepo: Kiểm tra status = 'ACCEPTED' hoặc userId == group.ownerId
        alt Chưa tham gia nhóm
            PostSvc-->>PostCtrl: Throw Exception ("Bạn phải tham gia nhóm mới có thể đăng bài.")
            PostCtrl-->>User: 400 Bad Request
        end
    end

    PostSvc->>PostSvc: Quyết định kiểm duyệt AI (Privileged / Scope)
    alt Phải kiểm duyệt AI
        PostSvc->>AiSvc: checkPostContent(content)
        AiSvc-->>PostSvc: AiModerationResult (score, label, reason)
    end

    PostSvc->>PostRepo: Lưu Post (status = APPROVED / PENDING, aiStatus, aiScore, aiReason)
    
    opt Có danh sách mediaUrls
        loop Từng mediaUrl
            PostSvc->>MediaRepo: Lưu Media (url, type = detectMediaType: IMAGE/VIDEO)
            PostSvc->>PostMediaRepo: Lưu PostMedia (postId, mediaId, displayOrder)
        end
    end

    alt Trạng thái APPROVED
        PostSvc->>Ws: convertAndSend("/topic/posts", PostEventDTO: action="CREATED", GroupPostDTO)
    else Trạng thái PENDING
        PostSvc->>Ws: convertAndSend("/topic/posts", PostEventDTO: action="CREATED") (Admin UI)
        PostSvc->>NotiSvc: Gửi thông báo 'POST_PENDING' cho tác giả
    end

    PostSvc-->>PostCtrl: Trả về GroupPostDTO
    PostCtrl-->>User: 200 OK (Post DTO)
```

### 3.3 Luồng Duyệt / Từ Chối Bài Viết của Quản Trị Viên (Post Moderation Flow)
```mermaid
sequenceDiagram
    autonumber
    actor Admin as Quản trị viên (Admin)
    participant PostCtrl as PostController
    participant PostSvc as PostServiceImpl
    participant PostRepo as PostRepository
    participant NotiSvc as NotificationService
    participant Ws as SimpMessagingTemplate (/topic/posts)

    alt Admin Phê Duyệt Bài Viết
        Admin->>PostCtrl: POST /api/posts/{postId}/approve
        PostCtrl->>PostSvc: approvePost(postId, adminId)
        PostSvc->>PostRepo: Cập nhật status = 'APPROVED', approvedBy = admin, updatedAt = now()
        PostSvc->>NotiSvc: Gửi thông báo 'POST_APPROVED' cho tác giả
        PostSvc->>Ws: convertAndSend("/topic/posts", PostEventDTO: action="CREATED", postDTO)
        PostCtrl-->>Admin: 200 OK ("Post approved successfully")
    else Admin Từ Chối Bài Viết
        Admin->>PostCtrl: POST /api/posts/{postId}/reject
        PostCtrl->>PostSvc: rejectPost(postId, adminId)
        PostSvc->>NotiSvc: Gửi thông báo 'POST_REJECTED' cho tác giả
        PostSvc->>Ws: convertAndSend("/topic/posts", PostEventDTO: action="DELETED", postId)
        PostSvc->>PostRepo: Hard delete Post khỏi Database
        PostCtrl-->>Admin: 200 OK ("Post rejected successfully")
    end
```

### 3.4 Luồng Chia Sẻ Bài Viết (Share Post Flow)
```mermaid
sequenceDiagram
    autonumber
    actor Sharer as Người chia sẻ
    participant PostCtrl as PostController
    participant PostSvc as PostServiceImpl
    participant PostRepo as PostRepository
    participant AiSvc as GeminiAiServiceImpl
    participant Ws as SimpMessagingTemplate (/topic/posts)
    participant NotiSvc as NotificationService

    Sharer->>PostCtrl: POST /api/posts/{postId}/share (content, visibility, groupId)
    PostCtrl->>PostSvc: sharePost(originalPostId, request, userId)
    PostSvc->>PostRepo: Tìm bài viết gốc & Duyệt đệ quy tìm Root Post thật sự
    PostSvc->>AiSvc: Kiểm duyệt caption chia sẻ qua AI
    PostSvc->>PostRepo: Tạo Post mới (originalPost = rootPost, content = caption)
    PostSvc->>PostRepo: Tăng originalPost.shareCount += 1
    
    Note over PostSvc, Ws: Broadcast cập nhật lượt share cho bài viết gốc
    PostSvc->>Ws: convertAndSend("/topic/posts", PostEventDTO: action="UPDATED", originalPostDTO)

    opt Người chia sẻ khác tác giả bài gốc
        PostSvc->>NotiSvc: Gửi thông báo 'POST_SHARED' cho tác giả bài viết gốc
    end

    PostSvc-->>PostCtrl: Trả về GroupPostDTO bài chia sẻ mới
    PostCtrl-->>Sharer: 200 OK
```

---

## 4. MODULE 3: BÌNH LUẬN & CẢM XÚC (COMMENT & REACTION FLOWS)

### 4.1 Luồng Tạo Bình Luận Cây Phân Cấp (Nested Tree Comments Flow)
```mermaid
sequenceDiagram
    autonumber
    actor Commenter as Người bình luận
    participant CommentCtrl as CommentController
    participant CommentSvc as CommentServiceImpl
    participant CommentRepo as CommentRepository
    participant PostRepo as PostRepository
    participant MediaRepo as MediaRepository
    participant NotiSvc as NotificationService
    participant Ws as SimpMessagingTemplate (/topic/comments)

    Commenter->>CommentCtrl: POST /api/posts/{postId}/comments (content, parentId, imageUrl)
    CommentCtrl->>CommentSvc: createComment(postId, userId, request)
    CommentSvc->>PostRepo: Tìm Post theo postId
    
    opt Có parentId (Trả lời bình luận)
        CommentSvc->>CommentRepo: Tìm comment cha
        CommentSvc->>CommentSvc: Tính getCommentDepth(parent)
        alt Độ sâu >= 2 (Đã đủ 3 cấp: Gốc -> Con -> Cháu)
            CommentSvc-->>CommentCtrl: Throw Exception ("Đã đạt giới hạn comment 3 cấp")
            CommentCtrl-->>Commenter: 400 Bad Request
        end
        CommentSvc->>CommentSvc: Gán comment.parent = parent
        opt Người reply khác chủ comment cha
            CommentSvc->>NotiSvc: Gửi thông báo 'COMMENT_REPLY' cho chủ comment cha
        end
    end

    opt Có đính kèm ảnh
        CommentSvc->>MediaRepo: Lưu Media (type = 'IMAGE', url = imageUrl)
        CommentSvc->>CommentSvc: Gán comment.media = media
    end

    CommentSvc->>CommentRepo: Lưu Comment mới
    
    opt Người bình luận khác chủ bài viết
        CommentSvc->>NotiSvc: Gửi thông báo 'POST_COMMENT' cho chủ bài viết
    end

    CommentSvc->>PostRepo: incrementCommentCount(postId) [Atomic Update chống Deadlock]

    Note over CommentSvc, Ws: Broadcast WebSocket sau khi Transaction đã Commit thành công (afterCommit)
    CommentSvc->>Ws: convertAndSend("/topic/comments", CommentEventDTO: "CREATED", newCommentCount)
    CommentSvc-->>CommentCtrl: Trả về CommentDTO
    CommentCtrl-->>Commenter: 200 OK (CommentDTO)
```

### 4.2 Luồng Thả Cảm Xúc / Đổi Cảm Xúc / Bỏ Cảm Xúc (Reaction Lifecycle)
```mermaid
sequenceDiagram
    autonumber
    actor User as Người dùng
    participant PostCtrl as PostController
    participant ReactSvc as ReactionServiceImpl
    participant ReactRepo as ReactionRepository
    participant PostRepo as PostRepository
    participant NotiSvc as NotificationService
    participant Ws as SimpMessagingTemplate (/topic/reactions)

    alt Thả cảm xúc / Thay đổi cảm xúc (reactToPost)
        User->>PostCtrl: POST /api/posts/{postId}/react?type={LIKE|LOVE|...}
        PostCtrl->>ReactSvc: reactToPost(postId, userId, type)
        ReactSvc->>ReactRepo: findById(ReactionId(userId, postId))
        alt Đã có Reaction trước đó
            ReactSvc->>ReactRepo: Cập nhật reaction.setType(type)
        else Chưa có Reaction
            ReactSvc->>ReactRepo: Lưu Reaction mới (post, user, type)
            ReactSvc->>PostRepo: incrementReactCount(postId) [Atomic Update]
            opt Người thả khác chủ bài viết
                ReactSvc->>NotiSvc: Gửi thông báo 'POST_REACTION' cho chủ bài viết
            end
        end
        ReactSvc->>Ws: convertAndSend("/topic/reactions", ReactionEventDTO: action="REACTED", newCount)
        PostCtrl-->>User: 200 OK
    else Bỏ cảm xúc (unreactToPost)
        User->>PostCtrl: DELETE /api/posts/{postId}/react
        PostCtrl->>ReactSvc: unreactToPost(postId, userId)
        ReactSvc->>ReactRepo: deleteById(ReactionId(userId, postId))
        ReactSvc->>PostRepo: decrementReactCount(postId) [Atomic Update]
        ReactSvc->>Ws: convertAndSend("/topic/reactions", ReactionEventDTO: action="UNREACTED", newCount)
        PostCtrl-->>User: 200 OK
    end
```

---

## 5. MODULE 4: QUẢN LÝ HỘI NHÓM (GROUP & COMMUNITY FLOWS)

### 5.1 Máy Trạng Thái Thành Viên Nhóm (Group Membership State Machine)
```mermaid
stateDiagram-v2
    [*] --> NONE: Chưa tham gia

    NONE --> ACCEPTED: Tham gia nhóm PUBLIC
    NONE --> REQUESTED: Gửi yêu cầu vào nhóm PRIVATE
    NONE --> PENDING: Được thành viên mời vào nhóm

    REQUESTED --> ACCEPTED: Admin duyệt yêu cầu (approveJoinRequest)
    REQUESTED --> [*]: Admin từ chối yêu cầu (rejectJoinRequest)

    PENDING --> ACCEPTED: Chấp nhận lời mời (Nhóm PUBLIC hoặc mời bởi Admin)
    PENDING --> REQUESTED: Chấp nhận lời mời (Nhóm PRIVATE mời bởi Member thường)
    PENDING --> [*]: Từ chối lời mời (declineInvitation)

    ACCEPTED --> NONE: Tự rời nhóm (leaveGroup)
    ACCEPTED --> BANNED: Admin ban khỏi nhóm (banMember)
    
    BANNED --> NONE: Admin gỡ cấm (unbanMember)
```

### 5.2 Luồng Tham Gia Nhóm Public vs Private (Join Group Flow)
```mermaid
sequenceDiagram
    autonumber
    actor User as Người dùng
    participant GroupCtrl as GroupController
    participant GroupSvc as GroupServiceImpl
    participant MemberRepo as GroupMemberRepository
    participant NotiSvc as NotificationService
    participant Ws as SimpMessagingTemplate (/topic/groups/membership)

    User->>GroupCtrl: POST /api/groups/{groupId}/join
    GroupCtrl->>GroupSvc: joinGroup(groupId, userId)
    GroupSvc->>MemberRepo: Kiểm tra trạng thái hiện tại trong nhóm

    alt Người dùng đang bị BANNED
        GroupSvc-->>GroupCtrl: Throw Exception ("Bạn đã bị cấm khỏi nhóm này.")
    else Đang ở trạng thái PENDING (Đã có lời mời trước đó)
        alt Nhóm PUBLIC
            GroupSvc->>MemberRepo: Cập nhật status = 'ACCEPTED'
            GroupSvc->>NotiSvc: Thông báo 'GROUP_MEMBER_JOINED' cho Chủ nhóm
            GroupSvc->>Ws: Broadcast MembershipEventDTO ("JOINED")
        else Nhóm PRIVATE
            GroupSvc-->>GroupCtrl: Yêu cầu chấp nhận lời mời thay vì gửi yêu cầu mới
        end
    else Chưa có bản ghi thành viên
        alt Nhóm PUBLIC
            GroupSvc->>MemberRepo: Lưu GroupMember (status = 'ACCEPTED', role = 'MEMBER')
            GroupSvc->>NotiSvc: Gửi thông báo 'GROUP_MEMBER_JOINED' cho Chủ nhóm & Admin
            GroupSvc->>Ws: Broadcast MembershipEventDTO ("JOINED")
        else Nhóm PRIVATE
            GroupSvc->>MemberRepo: Lưu GroupMember (status = 'REQUESTED', role = 'MEMBER')
            GroupSvc->>NotiSvc: Gửi thông báo 'GROUP_JOIN_REQUEST' cho toàn bộ Admin nhóm
            GroupSvc->>Ws: Broadcast MembershipEventDTO ("REQUESTED")
        end
    end
    GroupCtrl-->>User: 200 OK
```

### 5.3 Luồng Mời Thành Viên & Phê Duyệt 2 Bước (Invitation & 2-Step Approval)
```mermaid
sequenceDiagram
    autonumber
    actor Inviter as Người mời
    actor Invitee as Người được mời
    actor Admin as Admin nhóm
    participant GroupSvc as GroupServiceImpl
    participant MemberRepo as GroupMemberRepository
    participant NotiSvc as NotificationService
    participant Ws as SimpMessagingTemplate (/topic/groups/membership)

    Inviter->>GroupSvc: inviteMembers(groupId, userIds, actorId = Inviter)
    GroupSvc->>MemberRepo: Lưu GroupMember (status = 'PENDING', invitedById = Inviter)
    GroupSvc->>NotiSvc: Gửi thông báo 'GROUP_INVITE' cho Invitee
    GroupSvc->>Ws: Broadcast MembershipEventDTO ("INVITED")

    Note over Invitee, GroupSvc: Invitee chấp nhận lời mời
    Invitee->>GroupSvc: acceptInvitation(groupId, userId = Invitee)
    
    alt Nhóm PUBLIC HOẶC Người mời là Admin/Owner
        GroupSvc->>MemberRepo: Cập nhật status = 'ACCEPTED'
        GroupSvc->>NotiSvc: Gửi thông báo 'GROUP_INVITE_ACCEPTED' cho Inviter
        GroupSvc->>Ws: Broadcast MembershipEventDTO ("APPROVED")
    else Nhóm PRIVATE VÀ Người mời là Member thường
        GroupSvc->>MemberRepo: Cập nhật status = 'REQUESTED' (Chuyển tiếp duyệt bước 2)
        GroupSvc->>Ws: Broadcast MembershipEventDTO ("REQUESTED")
        Note over Admin, GroupSvc: Admin duyệt bước 2
        Admin->>GroupSvc: approveJoinRequest(groupId, targetUserId = Invitee, adminId)
        GroupSvc->>MemberRepo: Cập nhật status = 'ACCEPTED'
        GroupSvc->>NotiSvc: Gửi thông báo 'GROUP_JOIN_APPROVED' cho Invitee
        GroupSvc->>Ws: Broadcast MembershipEventDTO ("APPROVED")
    end
```

### 5.4 Luồng Cấm Thành Viên & Tự Động Quét Dọn Bài Viết Vi Phạm (Ban & Auto-cleanup)
```mermaid
sequenceDiagram
    autonumber
    actor Admin as Admin nhóm
    participant GroupSvc as GroupServiceImpl
    participant MemberRepo as GroupMemberRepository
    participant PostRepo as PostRepository
    participant NotiSvc as NotificationService
    participant WsMem as STOMP (/topic/groups/membership)
    participant WsPosts as STOMP (/topic/posts)

    Admin->>GroupSvc: banMember(groupId, targetUserId, adminId)
    GroupSvc->>MemberRepo: Cập nhật GroupMember.status = 'BANNED'
    GroupSvc->>NotiSvc: Gửi thông báo 'GROUP_BANNED' cho targetUser
    GroupSvc->>WsMem: Broadcast MembershipEventDTO ("BANNED")

    Note over GroupSvc, PostRepo: Tự động xóa vĩnh viễn toàn bộ bài viết PENDING của user bị ban trong nhóm
    GroupSvc->>PostRepo: findAllByAuthorIdAndGroupIdAndStatusAndIsDeletedFalse(targetUserId, groupId, "PENDING")
    loop Từng bài viết PENDING tìm thấy
        GroupSvc->>WsPosts: Broadcast PostEventDTO (action = "DELETED", postId)
        GroupSvc->>PostRepo: delete(post) [Hard delete]
    end
    GroupSvc->>PostRepo: flush()
    GroupSvc->>NotiSvc: Gửi thông báo 'POST_REJECTED' báo đã gỡ bài viết do bị cấm
```

---

## 6. MODULE 5: BẠN BÈ & THUẬT TOÁN GỢI Ý (FRIEND SYSTEM & SUGGESTION FLOWS)

### 6.1 Luồng Vòng Đời Lời Mời Kết Bạn (Friend Request Lifecycle)
```mermaid
sequenceDiagram
    autonumber
    actor A as Người gửi (User A)
    actor B as Người nhận (User B)
    participant FReqSvc as FriendRequestServiceImpl
    participant FReqRepo as FriendRequestRepository
    participant FriendRepo as FriendRepository
    participant SuggestRepo as FriendSuggestionRepository
    participant NotiSvc as NotificationService

    A->>FReqSvc: sendFriendRequest(senderId = A, receiverId = B)
    FReqSvc->>FReqRepo: Kiểm tra điều kiện an toàn (không tự kết bạn, chưa là bạn, chưa có pending)
    FReqSvc->>FReqRepo: Tạo FriendRequest (sender = A, receiver = B, status = 'PENDING')
    FReqSvc->>SuggestRepo: Xóa gợi ý kết bạn giữa A và B trong friend_suggestions
    FReqSvc->>NotiSvc: Gửi thông báo 'FRIEND_REQUEST' cho User B

    alt B Chấp nhận kết bạn
        B->>FReqSvc: acceptFriendRequest(requestId, userId = B)
        FReqSvc->>FReqRepo: Cập nhật status = 'ACCEPTED', respondedAt = now()
        Note over FReqSvc, FriendRepo: Tạo quan hệ bạn bè 2 chiều (Mutual Friendship)
        FReqSvc->>FriendRepo: INSERT INTO friends (user_id = B, friend_id = A)
        FReqSvc->>FriendRepo: INSERT INTO friends (user_id = A, friend_id = B)
        FReqSvc->>SuggestRepo: Xóa gợi ý 2 chiều giữa A và B
        FReqSvc->>NotiSvc: Gửi thông báo 'FRIEND_ACCEPT' cho User A
    else B Từ chối kết bạn
        B->>FReqSvc: rejectFriendRequest(requestId, userId = B)
        FReqSvc->>FReqRepo: Cập nhật status = 'REJECTED', respondedAt = now()
    else A Hủy lời mời
        A->>FReqSvc: cancelFriendRequest(senderId = A, receiverId = B)
        FReqSvc->>FReqRepo: Xóa bản ghi FriendRequest PENDING
    end
```

### 6.2 Thuật Toán Gợi Ý Bạn Bè SQL CTE Đa Tiêu Chí (Scoring Engine)
```mermaid
flowchart TD
    User(["Người dùng A"]) --> Calc["calculateSuggestions(userId)"]

    subgraph CTE_SQL ["Thuật toán SQL CTE (Common Table Expressions)"]
        CTE1["<b>MutualFriends CTE:</b><br>Đếm số bạn chung qua friends f1 & f2<br><b>Score = COUNT(f2.friend_id) * 10</b><br>Reason: 'X bạn chung'"]
        CTE2["<b>SameCity CTE:</b><br>Khớp tỉnh/thành phố up.city_name == my_profile.city_name<br><b>Score = 5</b><br>Reason: 'Cùng sống tại...'"]
        CTE3["<b>SameHobby CTE:</b><br>Khớp sở thích chung qua user_hobbies uh1 & uh2<br><b>Score = COUNT(uh2.hobby_id) * 7</b><br>Reason: 'Y sở thích chung'"]
    end

    Calc --> CTE1
    Calc --> CTE2
    Calc --> CTE3

    subgraph Filter_Pipeline ["Bộ Lọc Loại Trừ Nghiêm Ngặt"]
        F1["Loại trừ: Chính bản thân người dùng"]
        F2["Loại trừ: Đã là bạn bè trong bảng 'friends'"]
        F3["Loại trừ: Đang có lời mời kết bạn PENDING (cả 2 chiều)"]
        F4["Loại trừ: Đã bị người dùng bấm bỏ qua trong 'dismissed_suggestions'"]
        F5["Loại trừ: Tài khoản bị khóa (is_locked) hoặc bị xóa (is_deleted)"]
    end

    CTE1 --> Filter_Pipeline
    CTE2 --> Filter_Pipeline
    CTE3 --> Filter_Pipeline

    Filter_Pipeline --> UnionAll["Tổng hợp kết quả FinalCandidates:<br><b>Total Score = SUM(scores)</b><br>Gộp lý do: GROUP_CONCAT(reasons)<br>Điều kiện: <b>Total Score >= 5</b>"]
    UnionAll --> Limit["ORDER BY total_score DESC<br>LIMIT 10 ứng viên hàng đầu"]
    Limit --> SaveCache["Lưu vào bảng 'friend_suggestions'<br>expires_at = NOW() + 24 giờ"]
```

### 6.3 Lịch Trình Tự Động Định Kỳ (Friend Suggestion Scheduler)
```mermaid
sequenceDiagram
    autonumber
    participant Sched as FriendSuggestionScheduler (@Scheduled)
    participant Svc as FriendSuggestionServiceImpl
    participant DB as Database

    Note over Sched, DB: Tác vụ 1: 02:00 AM mỗi ngày (0 0 2 * * *)
    Sched->>Svc: refreshAllSuggestions()
    Svc->>DB: Lấy tất cả User đang hoạt động (active)
    loop Từng Active User
        alt Chưa có cache hợp lệ (expiresAt < now())
            Svc->>Svc: calculateSuggestions(userId)
            Svc->>DB: Xóa gợi ý cũ & Chạy thuật toán CTE tính 10 gợi ý mới
        end
    end

    Note over Sched, DB: Tác vụ 2: 03:00 AM mỗi ngày (0 0 3 * * *)
    Sched->>Svc: cleanupExpiredSuggestions()
    Svc->>DB: DELETE FROM friend_suggestions WHERE expires_at < NOW()
```

---

## 7. MODULE 6: NHẮN TIN THỜI GIAN THỰC & MÃ HÓA E2EE (CHAT & E2EE FLOWS)

### 7.1 Mô Hình Phối Hợp Backend - Firebase - Client E2EE
```mermaid
graph TD
    ClientA["Client A (Trình duyệt)"]
    ClientB["Client B (Trình duyệt)"]

    subgraph Spring_Backend ["Spring Boot Backend"]
        ChatSvc["ChatRoomService (Quản lý Phòng, Thành viên, LastMessage, UnreadCount)"]
        KeySvc["UserPublicKeyService (Lưu & Cung cấp Public Key E2EE)"]
        STOMP["STOMP Broker (/topic/chat/... & /user/.../queue/chat)"]
    end

    subgraph Firebase_Cloud ["Firebase Realtime Database"]
        FirebaseMsgs["Encrypted Messages Node: /messages/{firebaseRoomKey}"]
    end

    ClientA -- "1. Lấy Public Key của B" --> KeySvc
    ClientA -- "2. Mã hóa tin nhắn bằng PublicKey của B" --> ClientA
    ClientA -- "3. Ghi Encrypted Payload trực tiếp" --> FirebaseMsgs
    FirebaseMsgs -- "4. Realtime Sync Payload" --> ClientB
    ClientB -- "5. Giải mã bằng Private Key cục bộ của B" --> ClientB

    ClientA -- "6. Cập nhật lastMessageAt" --> ChatSvc
    ChatSvc -- "7. Bắn tín hiệu CHAT_UPDATE" --> STOMP
    STOMP -- "8. Cập nhật Sidebar & Unread Badge" --> ClientA
    STOMP -- "8. Cập nhật Sidebar & Unread Badge" --> ClientB
```

### 7.2 Luồng Nhắn Tin E2EE & Cập Nhật Unread Count (End-to-End Encrypted Message Flow)
```mermaid
sequenceDiagram
    autonumber
    actor A as User A
    actor B as User B
    participant ChatCtrl as ChatController
    participant KeyCtrl as UserPublicKeyController
    participant ChatSvc as ChatRoomServiceImpl
    participant Firebase as Firebase Realtime DB
    participant Ws as SimpMessagingTemplate (/user/{username}/queue/chat)

    Note over A, KeyCtrl: Bước 1: Lấy Public Key của đối phương
    A->>KeyCtrl: GET /api/keys/{userId_B}
    KeyCtrl-->>A: publicKey của B

    Note over A, Firebase: Bước 2: Mã hóa & Lưu tin nhắn vào Firebase
    A->>A: Mã hóa nội dung tin nhắn bằng RSA/ECDH Public Key của B
    A->>Firebase: Ghi tin nhắn mã hóa vào node /messages/{firebaseRoomKey}

    Note over A, ChatSvc: Bước 3: Báo Backend cập nhật thời gian tin nhắn cuối
    A->>ChatCtrl: POST /api/chat/last-message (firebaseRoomKey)
    ChatCtrl->>ChatSvc: updateLastMessageAt(firebaseRoomKey, userA)
    ChatSvc->>ChatSvc: Cập nhật room.lastMessageAt = now() & memberA.lastReadAt = now()
    ChatSvc->>Ws: Gửi CHAT_UPDATE cho A (unreadCount = 0)
    ChatSvc->>Ws: Gửi CHAT_UPDATE cho B (unreadCount = 1)

    Note over B, Firebase: Bước 4: Nhận và giải mã tại Client B
    Firebase-->>B: Nhận tin nhắn mã hóa từ Firebase Realtime Listener
    B->>B: Giải mã tin nhắn bằng Private Key lưu trong LocalStorage/IndexedDB của B
```

### 7.3 Luồng Đánh Dấu Đã Đọc & Tín Hiệu Seen (Read Receipt Flow)
```mermaid
sequenceDiagram
    autonumber
    actor B as User B (Người đọc)
    actor A as User A (Người gửi)
    participant ChatCtrl as ChatController
    participant ChatSvc as ChatRoomServiceImpl
    participant MemberRepo as ChatRoomMemberRepository
    participant WsSeen as STOMP (/topic/chat/{key}/seen)
    participant WsQueue as STOMP (/user/{username}/queue/chat)

    B->>ChatCtrl: PUT /api/chat/{roomId}/read
    ChatCtrl->>ChatSvc: markAsRead(roomId, userB)
    ChatSvc->>MemberRepo: Cập nhật memberB.lastReadAt = now()
    
    Note over ChatSvc, WsSeen: Phát tín hiệu "Đã xem" tới phòng chat
    ChatSvc->>WsSeen: convertAndSend(ReadReceiptDTO: roomId, userId = B, lastReadAt = now())
    WsSeen-->>A: User A nhận tín hiệu và hiển thị avatar User B đã xem đến tin nhắn này

    Note over ChatSvc, WsQueue: Cập nhật lại sidebar của User B (unreadCount = 0)
    ChatSvc->>WsQueue: Gửi CHAT_UPDATE cho B (unreadCount = 0)
```

---

## 8. MODULE 7: TRẠNG THÁI HOẠT ĐỘNG (PRESENCE FLOWS)

```mermaid
sequenceDiagram
    autonumber
    actor Client as Người dùng
    participant WsListener as WebSocketEventListener
    participant OnlineSvc as OnlineUserService (ConcurrentHashMap)
    participant WsBroker as STOMP (/topic/public/status)

    Note over Client, WsListener: 1. Khi Client kết nối WebSocket STOMP
    Client->>WsListener: SessionConnectedEvent (Principal: UserPrincipal)
    WsListener->>OnlineSvc: addUser(userId)
    WsListener->>WsBroker: convertAndSend("/topic/public/status", UserStatusDTO: userId, status="ONLINE")
    WsBroker-->>Client: Tất cả người dùng đang online nhận thông báo User này ONLINE

    Note over Client, OnlineSvc: 2. Khi Client tải trang (Lấy danh sách online ban đầu)
    Client->>OnlineSvc: GET /api/users/online
    OnlineSvc-->>Client: 200 OK (Set<Integer> onlineUserIds)

    Note over Client, WsListener: 3. Khi Client ngắt kết nối WebSocket (Đóng trình duyệt / Mất mạng)
    Client-xWsListener: SessionDisconnectEvent (Principal: UserPrincipal)
    WsListener->>OnlineSvc: removeUser(userId)
    WsListener->>WsBroker: convertAndSend("/topic/public/status", UserStatusDTO: userId, status="OFFLINE")
    WsBroker-->>Client: Tất cả người dùng nhận thông báo User này OFFLINE
```

---

## 9. MODULE 8: HỒ SƠ CÁ NHÂN & TÌM KIẾM THÀNH VIÊN (PROFILE & DISCOVERY FLOWS)

### 9.1 Sơ Đồ Quyết Định Hiển Thị Quyền Riêng Tư (Profile Privacy Flowchart)
```mermaid
flowchart TD
    Req(["Yêu cầu xem Profile: getUserProfile(targetUserId, currentUserId)"]) --> Rel{"Xác định mối quan hệ<br>(determineRelationship)"}

    Rel -- "targetUserId == currentUserId" --> Self["Mối quan hệ: <b>SELF</b>"]
    Rel -- "Tồn tại trong bảng 'friends'" --> Friend["Mối quan hệ: <b>FRIEND</b>"]
    Rel -- "Tồn tại trong 'friend_requests' (PENDING)" --> Pending["Mối quan hệ: <b>PENDING</b>"]
    Rel -- "Không tồn tại quan hệ" --> Stranger["Mối quan hệ: <b>STRANGER</b>"]

    Self --> FullView["<b>HIỂN THỊ ĐẦY ĐỦ (Full Profile):</b><br>+ Họ tên, Giới tính, Bio, Nghề nghiệp<br>+ <b>Email, Ngày sinh, Hôn nhân, Mục đích kết nối (LookingFor)</b><br>+ Tỉnh/Thành phố, Avatar, Cover, Gallery, Hobbies<br>+ Thống kê số bạn bè, số bài viết"]
    Friend --> FullView

    Pending --> MaskedView["<b>HIỂN THỊ CÔNG KHAI (Public Profile Only):</b><br>+ Họ tên, Giới tính, Bio, Nghề nghiệp<br>+ Tỉnh/Thành phố, Avatar, Cover, Gallery, Hobbies<br>+ Thống kê số bạn bè, số bài viết<br><br><b>ẨN THÔNG TIN NHẠY CẢM:</b><br>- Email (null)<br>- Ngày sinh (null)<br>- Tình trạng hôn nhân (null)<br>- Mục đích kết nối (null)"]
    Stranger --> MaskedView
```

### 9.2 Luồng Tìm Kiếm Thành Viên Ưu Tiên Theo Vị Trí & Mục Đích (Member Search Flow)
```mermaid
flowchart TD
    UserQuery(["Người dùng tìm kiếm: searchMembers(keyword, gender, cityCode, maritalStatus, lookingFor)"]) --> FetchCurrent["Lấy thông tin của người tìm kiếm:<br>- currentUserCityCode<br>- currentUserLookingFor"]

    FetchCurrent --> QueryDB["Thực thi userProfileRepository.searchMembers()"]

    subgraph Scoring_Order ["Thuật Toán Sắp Xếp Ưu Tiên (Order Priority)"]
        O1["<b>Ưu tiên 1:</b> Cùng Tỉnh/Thành phố (city_code == currentUserCityCode)"]
        O2["<b>Ưu tiên 2:</b> Cùng Mục đích kết nối (looking_for == currentUserLookingFor)"]
        O3["<b>Ưu tiên 3:</b> Thời gian cập nhật gần nhất (updated_at DESC)"]
    end

    QueryDB --> Scoring_Order
    Scoring_Order --> EnrichAvatar["Lấy avatar hiện tại của từng ứng viên (getCurrentAvatar)"]
    EnrichAvatar --> RetPage["Trả về Page&lt;MemberSearchResponse&gt;"]
```

---

## 10. MODULE 9: HỆ THỐNG THÔNG BÁO THỜI GIAN THỰC (NOTIFICATION FLOWS)

```mermaid
sequenceDiagram
    autonumber
    participant EventSource as Các Service (Post/Friend/Group/Report...)
    participant NotiSvc as NotificationServiceImpl
    participant NotiRepo as NotificationRepository
    participant ProfileRepo as UserProfileRepository
    participant AvatarRepo as UserAvatarRepository
    participant Ws as SimpMessagingTemplate (/user/{username}/queue/notifications)
    actor Receiver as Người nhận

    EventSource->>NotiSvc: sendNotification(TungNotificationDTO, receiver, actor)
    NotiSvc->>NotiRepo: Lưu Notification entity vào Database (isRead = false, createdAt = now())
    
    opt Có tác nhân hành động (actor != null)
        NotiSvc->>ProfileRepo: Lấy fullName của actor
        NotiSvc->>AvatarRepo: Lấy avatarUrl hiện tại của actor
    end

    NotiSvc->>NotiSvc: Đóng gói TungNotificationDTO đầy đủ thông tin
    NotiSvc->>Ws: convertAndSendToUser(receiver.getUsername(), "/queue/notifications", dto)
    Ws-->>Receiver: Nhận thông báo Popup / Toast tức thì trên giao diện
```

---

## 11. MODULE 10: BÁO CÁO VI PHẠM & QUẢN TRỊ HỆ THỐNG (REPORT & ADMIN FLOWS)

### 11.1 Luồng Báo Cáo Vi Phạm & Xử Lý (Report Submission & Resolution)
```mermaid
sequenceDiagram
    autonumber
    actor User as Người báo cáo
    actor Admin as Quản trị viên
    participant ReportCtrl as ReportController
    participant ReportSvc as ReportServiceImpl
    participant ReportRepo as ReportRepository
    participant UserRepo as UserRepository
    participant NotiSvc as NotificationService

    User->>ReportCtrl: POST /api/reports (targetType: POST/GROUP/USER, targetId, reason)
    ReportCtrl->>ReportSvc: createReport(request, username)
    ReportSvc->>ReportRepo: Lưu Report (status = 'PENDING', reporter = User, createdAt = now())
    ReportSvc->>UserRepo: findByRole("ADMIN")
    loop Từng Admin hệ thống
        ReportSvc->>NotiSvc: Gửi thông báo 'REPORT_SUBMITTED' qua WebSocket
    end
    ReportCtrl-->>User: 200 OK ("Report submitted successfully")

    Note over Admin, ReportSvc: Admin xử lý báo cáo vi phạm
    Admin->>ReportCtrl: PUT /api/reports/{id} (status: 'RESOLVED' / 'REVIEWING')
    ReportCtrl->>ReportSvc: updateReport(id, request, adminUsername)
    ReportSvc->>ReportRepo: Cập nhật report.status, reviewer = Admin
    ReportSvc->>NotiSvc: Gửi thông báo 'REPORT_UPDATED' cho User đã báo cáo
    ReportCtrl-->>Admin: 200 OK
```

### 11.2 Luồng Quản Trị Người Dùng & Rào Chắn Tự Bảo Vệ (Admin Guard Rails Flowchart)
```mermaid
flowchart TD
    Action(["Admin thực hiện: Khóa / Xóa / Hạ quyền người dùng"]) --> CheckSelf{"Admin có đang tự thao tác<br>trên chính mình không?<br>(adminId == targetUserId)"}

    CheckSelf -- Có --> ThrowSelfError["Ném ngoại lệ: <b>Chặn tự thao tác</b><br>('Admin không thể tự thao tác trên chính mình')"]

    CheckSelf -- Không --> CheckRole{"Mục tiêu có vai trò là ADMIN không?<br>(targetUser.role == 'ADMIN')"}

    CheckRole -- Có --> CountAdmins{"Đếm số Admin đang hoạt động:<br>countByRoleAndIsDeletedFalseAndIsLockedFalse('ADMIN')"}
    CountAdmins -- "<= 1 (Chỉ còn 1 Admin duy nhất)" --> ThrowLastAdminError["Ném ngoại lệ: <b>Bảo vệ Admin cuối cùng</b><br>('Không thể khóa/xóa/hạ cấp Quản trị viên cuối cùng của hệ thống')"]
    
    CountAdmins -- "> 1" --> ExecuteAction["Cho phép thực hiện"]
    CheckRole -- Không (User thường) --> ExecuteAction

    ExecuteAction --> UpdateDB["Cập nhật User trong Database"]
    UpdateDB --> CheckActionType{"Hành động là gì?"}

    CheckActionType -- "Khóa (LOCK)" --> SendLock["Gửi sự kiện realtime qua STOMP:<br>/user/{username}/queue/errors (type: 'LOCK')<br>=> Ép trình duyệt người dùng đăng xuất ngay"]
    CheckActionType -- "Xóa (DELETE)" --> SendDelete["Gửi sự kiện realtime qua STOMP:<br>/user/{username}/queue/errors (type: 'DELETE')<br>=> Ép trình duyệt người dùng đăng xuất ngay"]
    CheckActionType -- "Đổi vai trò" --> SendRoleNoti["Gửi thông báo 'ROLE_CHANGE' cho người dùng"]
```

---

## 12. BẢNG KÊNH STOMP WEBSOCKET ĐẦY ĐỦ

| Kênh STOMP | Loại Kênh | Payload DTO | Mô tả chức năng |
| :--- | :--- | :--- | :--- |
| `/topic/posts` | Broadcast | `PostEventDTO` | Phát sự kiện bài viết mới (`CREATED`), chỉnh sửa (`UPDATED`), xóa bài (`DELETED`) trên toàn mạng xã hội |
| `/topic/comments` | Broadcast | `CommentEventDTO` | Phát sự kiện bình luận mới hoặc xóa bình luận, kèm `commentCount` cập nhật |
| `/topic/reactions` | Broadcast | `ReactionEventDTO` | Phát sự kiện thả cảm xúc (`REACTED`) hoặc bỏ cảm xúc (`UNREACTED`), kèm `reactCount` cập nhật |
| `/topic/groups/membership` | Broadcast | `MembershipEventDTO` | Phát sự kiện thay đổi thành viên nhóm (`JOINED`, `LEFT`, `INVITED`, `APPROVED`, `BANNED`) |
| `/topic/public/status` | Broadcast | `UserStatusDTO` | Phát trạng thái hiện diện thời gian thực (`ONLINE` / `OFFLINE`) của người dùng |
| `/topic/chat/{firebaseRoomKey}/typing` | Broadcast | `TypingEventDTO` | Phát hiệu ứng đang gõ tin nhắn trong phòng chat cụ thể |
| `/topic/chat/{firebaseRoomKey}/seen` | Broadcast | `ReadReceiptDTO` | Phát tín hiệu đã xem (Seen) đến người gửi tin nhắn trong phòng chat |
| `/user/{username}/queue/notifications` | Point-to-Point | `TungNotificationDTO` | Đẩy thông báo cá nhân (kết bạn, tương tác bài viết, hội nhóm, hệ thống) |
| `/user/{username}/queue/chat` | Point-to-Point | `Map<String, Object>` | Đồng bộ danh sách phòng chat, số tin chưa đọc, sự kiện bị xóa khỏi nhóm chat |
| `/user/{username}/queue/errors` | Point-to-Point | `Map<String, String>` | Đẩy thông báo lỗi khẩn cấp (tài khoản bị khóa/xóa) để ép client đăng xuất tức thì |

---
*Tài liệu được thiết kế chi tiết bằng biểu đồ Mermaid trực quan cho toàn bộ luồng nghiệp vụ Backend của dự án Connect (ConnectCG).*
