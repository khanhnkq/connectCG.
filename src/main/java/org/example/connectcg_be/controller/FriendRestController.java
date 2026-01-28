package org.example.connectcg_be.controller;

import lombok.RequiredArgsConstructor;
import org.example.connectcg_be.dto.FriendDTO;
import org.example.connectcg_be.security.UserPrincipal;
import org.example.connectcg_be.service.FriendService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v1/friends")
@RequiredArgsConstructor
public class FriendRestController {

    private final FriendService friendService;

    @GetMapping(value = "/{userId}")
    public ResponseEntity<Page<FriendDTO>> getFriendsByUserId(
            Authentication authentication,
            @PathVariable(name = "userId") Integer userId,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String gender,
            @RequestParam(required = false) Integer cityId,
            @PageableDefault(size = 2) Pageable pageable) {
        Integer viewerId = null;
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal) {
             viewerId = ((UserPrincipal) authentication.getPrincipal()).getId();
        }
        // If not authenticated, viewerId is null. Logic in Service should handle null if public access is allowed, 
        // but for now let's assume authenticated or handle NPE in service if needed. 
        // Actually best to enforce authentication or handle null check in service. 
        // Service code I wrote assumes viewerId is not null usually, but I should be safe.
        // Let's pass 0 or null. The service uses .equals() on viewerId. If viewerId is null, it might throw NPE.
        // I will assume authentication is required as per security config usually.
        if (viewerId == null) viewerId = 0; // Treat as stranger/anonymous

        Page<FriendDTO> friends = friendService.getFriends(userId, viewerId, name, gender, cityId, pageable);
        return ResponseEntity.ok(friends);
    }

    @GetMapping
    public ResponseEntity<Page<FriendDTO>> getMyFriends(
            Authentication authentication,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String gender,
            @RequestParam(required = false) Integer cityId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(friendService.getFriends(userPrincipal.getId(), userPrincipal.getId(), name, gender, cityId, pageable));
    }

    @DeleteMapping("/{friendId}")
    public ResponseEntity<Void> unfriend(
            Authentication authentication,
            @PathVariable Integer friendId) {
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        friendService.unfriend(userPrincipal.getId(), friendId);
        return ResponseEntity.noContent().build();
    }

}
