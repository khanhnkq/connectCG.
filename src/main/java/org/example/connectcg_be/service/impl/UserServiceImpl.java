package org.example.connectcg_be.service.impl;

import org.example.connectcg_be.dto.UserProfileDTO;
import org.example.connectcg_be.entity.*;
import org.example.connectcg_be.repository.*;
import org.example.connectcg_be.service.UserService;
import org.example.connectcg_be.realtime.RealtimeEventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class UserServiceImpl implements UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private UserAvatarRepository userAvatarRepository;

    @Autowired
    private UserCoverRepository userCoverRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private FriendRepository friendRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private RealtimeEventPublisher realtimeEventPublisher;

    @Override
    public User findByIdUser(int id) {
        return userRepository.findById(id).orElse(null);
    }

    @Override
    public List<UserProfileDTO> getAllUser() {
        return mapToDTOList(userRepository.findAll());
    }

    @Override
    public org.springframework.data.domain.Page<UserProfileDTO> getAllUsersPaged(String keyword, String role,
            org.springframework.data.domain.Pageable pageable) {
        String k = (keyword == null || keyword.trim().isEmpty()) ? null : keyword.trim();
        String r = (role == null || role.trim().isEmpty()) ? null : role.trim();
        org.springframework.data.domain.Page<User> usersPage = userRepository.findByFilters(k, r, pageable);
        List<UserProfileDTO> dtos = mapToDTOList(usersPage.getContent());
        return new org.springframework.data.domain.PageImpl<>(dtos, pageable, usersPage.getTotalElements());
    }

    private List<UserProfileDTO> mapToDTOList(List<User> users) {
        if (users == null || users.isEmpty()) {
            return Collections.emptyList();
        }

        List<Integer> userIds = users.stream().map(User::getId).toList();

        Map<Integer, UserProfile> profileMap = userProfileRepository.findAllByUserIdIn(userIds).stream()
                .filter(p -> p.getUser() != null)
                .collect(Collectors.toMap(p -> p.getUser().getId(), Function.identity(), (a, b) -> a));

        Map<Integer, UserAvatar> avatarMap = userAvatarRepository.findCurrentByUserIds(userIds).stream()
                .filter(ua -> ua.getUser() != null)
                .collect(Collectors.toMap(ua -> ua.getUser().getId(), Function.identity(), (a, b) -> a));

        Map<Integer, UserCover> coverMap = userCoverRepository.findCurrentByUserIds(userIds).stream()
                .filter(uc -> uc.getUser() != null)
                .collect(Collectors.toMap(uc -> uc.getUser().getId(), Function.identity(), (a, b) -> a));

        Map<Integer, Integer> postCountMap = new HashMap<>();
        for (Object[] row : postRepository.countPostsByAuthorIds(userIds)) {
            if (row != null && row.length >= 2 && row[0] != null && row[1] != null) {
                postCountMap.put((Integer) row[0], ((Number) row[1]).intValue());
            }
        }

        Map<Integer, Integer> friendCountMap = new HashMap<>();
        for (Object[] row : friendRepository.countFriendsByUserIds(userIds)) {
            if (row != null && row.length >= 2 && row[0] != null && row[1] != null) {
                friendCountMap.put((Integer) row[0], ((Number) row[1]).intValue());
            }
        }

        return users.stream().map(user -> {
            UserProfileDTO dto = new UserProfileDTO();
            dto.setUserId(user.getId());
            dto.setUsername(user.getUsername());
            dto.setEmail(user.getEmail());
            dto.setRole(user.getRole());
            dto.setIsLocked(user.getIsLocked());
            dto.setLockedUntil(user.getLockedUntil());
            dto.setPermanentLocked(user.getPermanentLocked());

            UserProfile profile = profileMap.get(user.getId());
            if (profile != null) {
                dto.setFullName(profile.getFullName());
                dto.setDateOfBirth(profile.getDateOfBirth());
                dto.setGender(profile.getGender());
                dto.setBio(profile.getBio());
                dto.setOccupation(profile.getOccupation());
                dto.setMaritalStatus(profile.getMaritalStatus());
                dto.setLookingFor(profile.getLookingFor());

                if (profile.getCityCode() != null) {
                    dto.setCityCode(profile.getCityCode());
                    dto.setCityName(profile.getCityName());
                }
            }

            UserAvatar currentAvatar = avatarMap.get(user.getId());
            if (currentAvatar != null && currentAvatar.getMedia() != null) {
                dto.setCurrentAvatarUrl(currentAvatar.getMedia().getUrl());
            }

            UserCover currentCover = coverMap.get(user.getId());
            if (currentCover != null && currentCover.getMedia() != null) {
                dto.setCurrentCoverUrl(currentCover.getMedia().getUrl());
            }

            dto.setPostsCount(postCountMap.getOrDefault(user.getId(), 0));
            dto.setFriendsCount(friendCountMap.getOrDefault(user.getId(), 0));

            return dto;
        }).toList();
    }

    @Override
    @Transactional
    public void updateUserRole(Integer userId, String newRole, Integer actorId) {
        if (actorId.equals(userId)) {
            throw new RuntimeException("Bạn không thể tự thay đổi vai trò của chính mình");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        String oldRole = user.getRole();

        // Protection: Prevent demoting the last Active Admin
        if ("ADMIN".equals(oldRole) && "USER".equals(newRole)) {
            long activeAdmins = userRepository.countByRoleAndIsDeletedFalseAndIsLockedFalse("ADMIN");
            if (activeAdmins <= 1) {
                throw new RuntimeException("Không thể hạ cấp Quản trị viên cuối cùng của hệ thống");
            }
        }

        user.setRole(newRole);
        userRepository.save(user);

        // Create Notification
        Notification notification = new Notification();
        notification.setUser(user);
        if (actorId != null) {
            notification.setActor(userRepository.findById(actorId).orElse(null));
        }
        notification.setType("ROLE_CHANGE");
        notification.setTargetType("USER");
        notification.setTargetId(userId);
        notification.setIsRead(false);
        notification.setCreatedAt(java.time.Instant.now());
        notification.setContent("Vai trò của bạn đã được cập nhật từ " + oldRole + " thành " + newRole);

        notificationRepository.save(notification);
    }

    private UserProfileDTO mapToDTO(User user) {
        return mapToDTO(user, null);
    }

    private UserProfileDTO mapToDTO(User user, Integer viewerId) {
        UserProfileDTO dto = new UserProfileDTO();

        // Basic user info
        dto.setUserId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setRole(user.getRole());
        dto.setIsLocked(user.getIsLocked());
        dto.setLockedUntil(user.getLockedUntil());
        dto.setPermanentLocked(user.getPermanentLocked());

        // Get profile info
        UserProfile profile = userProfileRepository.findByUserId(user.getId()).orElse(null);
        if (profile != null) {
            dto.setFullName(profile.getFullName());
            dto.setDateOfBirth(profile.getDateOfBirth());
            dto.setGender(profile.getGender());
            dto.setBio(profile.getBio());
            dto.setOccupation(profile.getOccupation());
            dto.setMaritalStatus(profile.getMaritalStatus());
            dto.setLookingFor(profile.getLookingFor());

            // City info
            if (profile.getCityCode() != null) {
                dto.setCityCode(profile.getCityCode());
                dto.setCityName(profile.getCityName());
            }
        }

        // Get current avatar
        UserAvatar currentAvatar = userAvatarRepository.findByUserIdAndIsCurrentTrue(user.getId());
        if (currentAvatar != null && currentAvatar.getMedia() != null) {
            dto.setCurrentAvatarUrl(currentAvatar.getMedia().getUrl());
        }

        // Get current cover
        UserCover currentCover = userCoverRepository.findByUserIdAndIsCurrentTrue(user.getId()).orElse(null);
        if (currentCover != null && currentCover.getMedia() != null) {
            dto.setCurrentCoverUrl(currentCover.getMedia().getUrl());
        }

        // Stats
        dto.setPostsCount(postRepository.countByAuthorIdAndIsDeletedFalse(user.getId()));
        dto.setFriendsCount((int) friendRepository.countByUserId(user.getId()));

        // Relationship status (if viewerId is provided)
        if (viewerId != null) {
            if (viewerId.equals(user.getId())) {
                dto.setRelationshipStatus("SELF");
                dto.setIsFriend(false);
            } else {
                boolean isFriend = friendRepository.existsByUserIdAndFriendId(viewerId, user.getId());
                dto.setIsFriend(isFriend);
                dto.setRelationshipStatus(isFriend ? "FRIEND" : "STRANGER");
            }
        }

        return dto;
    }

    public void toggleLockUser(Integer targetUserId, Integer adminId) {
        guardSelfAction(targetUserId, adminId);

        User user = getUser(targetUserId);

        if ("ADMIN".equals(user.getRole()) && !Boolean.TRUE.equals(user.getIsLocked())) {
            long activeAdmins = userRepository.countByRoleAndIsDeletedFalseAndIsLockedFalse("ADMIN");
            if (activeAdmins <= 1) {
                throw new RuntimeException("Không thể khóa Quản trị viên cuối cùng");
            }
        }

        boolean locked = !Boolean.TRUE.equals(user.getIsLocked());
        user.setIsLocked(locked);
        if (locked) {
            user.setAuthVersion(user.getAuthVersion() == null ? 1 : user.getAuthVersion() + 1);
        }
        userRepository.save(user);

        if (locked) {
            sendUserEvent(user.getUsername(), "LOCK", "Tài khoản của bạn đã bị khóa");
        }
    }

    public void softDeleteUser(Integer targetUserId, Integer adminId) {
        guardSelfAction(targetUserId, adminId);

        User user = getUser(targetUserId);

        if ("ADMIN".equals(user.getRole())) {
            long activeAdmins = userRepository.countByRoleAndIsDeletedFalse("ADMIN");
            if (activeAdmins <= 1) {
                throw new RuntimeException("Không thể xóa Quản trị viên cuối cùng");
            }
        }

        user.setIsDeleted(true);
        user.setAuthVersion(user.getAuthVersion() == null ? 1 : user.getAuthVersion() + 1);
        userRepository.save(user);

        sendUserEvent(user.getUsername(), "DELETE", "Tài khoản của bạn đã bị xóa");
    }

    private void guardSelfAction(Integer targetUserId, Integer adminId) {
        if (adminId != null && adminId.equals(targetUserId)) {
            throw new RuntimeException("Admin không thể tự thao tác trên chính mình");
        }
    }

    private User getUser(Integer userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    private void sendUserEvent(String username, String type, String message) {
        if (username == null) {
            return;
        }
        log.info("Sending {} message to user: {}", type, username);
        realtimeEventPublisher.sendToUser(
                username,
                "/queue/errors",
                Map.of("type", type, "message", message));
    }

}
