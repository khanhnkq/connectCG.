package org.example.connectcg_be.controller;

import org.example.connectcg_be.dto.ChatMemberDTO;
import org.example.connectcg_be.dto.ChatRoomDTO;
import org.example.connectcg_be.entity.ChatRoom;
import org.example.connectcg_be.entity.User;
import org.example.connectcg_be.repository.UserRepository;
import org.example.connectcg_be.repository.UserProfileRepository;
import org.example.connectcg_be.repository.UserAvatarRepository;
import org.example.connectcg_be.repository.ChatRoomMemberRepository;
import org.example.connectcg_be.security.UserPrincipal;
import org.example.connectcg_be.service.ChatRoomService;
import org.example.connectcg_be.entity.UserProfile;
import org.example.connectcg_be.entity.UserAvatar;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Chat Controller handles room metadata
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    @Autowired
    private ChatRoomService chatRoomService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ChatRoomMemberRepository chatRoomMemberRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private UserAvatarRepository userAvatarRepository;

    @PostMapping("/direct/{targetUserId}")
    public ResponseEntity<?> getOrCreateDirectChat(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Integer targetUserId) {

        User user1 = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));
        User user2 = userRepository.findById(targetUserId)
                .orElseThrow(() -> new RuntimeException("Target user not found"));

        ChatRoom room = chatRoomService.getOrCreateDirectChat(user1, user2);
        return ResponseEntity.ok(convertToDTO(room, currentUser.getId()));
    }

    @GetMapping("/my")
    public ResponseEntity<List<ChatRoomDTO>> getMyChatRooms(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        List<ChatRoom> rooms = chatRoomService.getUserChatRooms(currentUser.getId());
        List<ChatRoomDTO> dtos = rooms.stream()
                .map(room -> convertToDTO(room, currentUser.getId()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @PostMapping("/group")
    public ResponseEntity<?> createGroupChat(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestBody Map<String, Object> request) {

        String name = (String) request.get("name");
        Object memberIdsObj = request.get("memberIds");
        List<Integer> memberIds = (memberIdsObj instanceof List<?>)
                ? ((List<?>) memberIdsObj).stream().map(o -> (Integer) o).collect(Collectors.toList())
                : List.of();

        User creator = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new RuntimeException("Creator not found"));

        List<User> members = memberIds.stream()
                .map(id -> userRepository.findById(id)
                        .orElseThrow(() -> new RuntimeException("Member not found: " + id)))
                .collect(Collectors.toList());

        ChatRoom room = chatRoomService.createGroupChat(creator, name, members);
        return ResponseEntity.ok(convertToDTO(room, currentUser.getId()));
    }

    @PutMapping("/{roomId}/name")
    public ResponseEntity<?> renameRoom(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long roomId,
            @RequestBody String newName) {

        User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Remove quotes if present from raw string body
        String cleanName = newName.replace("\"", "");
        ChatRoom room = chatRoomService.renameRoom(roomId, cleanName, user);
        return ResponseEntity.ok(convertToDTO(room, currentUser.getId()));
    }

    @PutMapping("/{roomId}/avatar")
    public ResponseEntity<?> updateAvatar(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long roomId,
            @RequestBody Map<String, String> payload) {

        String url = payload.get("url");
        User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        ChatRoom room = chatRoomService.updateAvatar(roomId, url, user);
        return ResponseEntity.ok(convertToDTO(room, currentUser.getId()));
    }

    private ChatRoomDTO convertToDTO(ChatRoom room, Integer currentUserId) {
        String name = room.getName();
        String avatarUrl = room.getAvatarUrl();
        Integer otherParticipantId = null;

        // Fetch all members to populate 'members' list and determine 1-1 info
        List<org.example.connectcg_be.entity.ChatRoomMember> roomMembers = chatRoomMemberRepository
                .findByChatRoom_Id(room.getId());

        List<ChatMemberDTO> memberDTOs = roomMembers.stream().map(rm -> {
            Integer uid = rm.getUser().getId();
            String uName = rm.getUser().getUsername();
            String fName = userProfileRepository.findByUserId(uid)
                    .map(UserProfile::getFullName)
                    .orElse(uName);

            String aUrl = null;
            UserAvatar avatar = userAvatarRepository.findByUserIdAndIsCurrentTrue(uid);
            if (avatar != null && avatar.getMedia() != null) {
                aUrl = avatar.getMedia().getUrl();
            }

            return ChatMemberDTO.builder()
                    .id(uid)
                    .fullName(fName)
                    .avatarUrl(aUrl)
                    .role(rm.getRole())
                    .build();
        }).collect(Collectors.toList());

        if ("DIRECT".equals(room.getType())) {
            // Find the other member specifically for the room header info
            for (ChatMemberDTO m : memberDTOs) {
                if (!m.getId().equals(currentUserId)) {
                    otherParticipantId = m.getId();
                    name = m.getFullName();
                    avatarUrl = m.getAvatarUrl();
                    break;
                }
            }
        }

        return ChatRoomDTO.builder()
                .id(room.getId())
                .type(room.getType())
                .name(name)
                .avatarUrl(avatarUrl)
                .firebaseRoomKey(room.getFirebaseRoomKey())
                .otherParticipantId(otherParticipantId)
                .members(memberDTOs)
                .lastMessageAt(room.getLastMessageAt())
                .createdAt(room.getCreatedAt())
                .build();
    }
}
