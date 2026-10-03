package org.example.connectcg_be.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.connectcg_be.dto.FriendDTO;
import org.example.connectcg_be.repository.FriendRepository;
import org.example.connectcg_be.repository.FriendRequestRepository;
import org.example.connectcg_be.service.FriendService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FriendServiceImpl implements FriendService {

    private final FriendRepository friendRepository;
    private final FriendRequestRepository friendRequestRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<FriendDTO> getFriends(Integer userId, Integer viewerId, String name, String gender, String cityCode, Pageable pageable) {
        Page<FriendDTO> friends = friendRepository.searchFriends(userId, name, gender, cityCode, pageable);

        if (friends.isEmpty() || viewerId == null) {
            return friends;
        }

        List<Integer> targetIds = friends.getContent().stream()
                .map(FriendDTO::getId)
                .filter(id -> id != null && !viewerId.equals(id))
                .distinct()
                .toList();

        Set<Integer> friendIds = targetIds.isEmpty() ? Collections.emptySet()
                : friendRepository.findFriendIdsByViewerIdAndFriendIdIn(viewerId, targetIds);
        Set<Integer> pendingSentIds = targetIds.isEmpty() ? Collections.emptySet()
                : friendRequestRepository.findPendingReceiverIds(viewerId, targetIds);
        Set<Integer> pendingReceivedIds = targetIds.isEmpty() ? Collections.emptySet()
                : friendRequestRepository.findPendingSenderIds(viewerId, targetIds);

        // Populate relationship status relative to viewerId
        friends.forEach(friend -> {
            Integer targetId = friend.getId();
            if (viewerId.equals(targetId)) {
                friend.setRelationshipStatus("SELF");
            } else if (friendIds.contains(targetId)) {
                friend.setRelationshipStatus("FRIEND");
            } else if (pendingSentIds.contains(targetId)) {
                friend.setRelationshipStatus("PENDING");
            } else if (pendingReceivedIds.contains(targetId)) {
                friend.setRelationshipStatus("WAITING"); // Or whatever status code for "Request Received"
            } else {
                friend.setRelationshipStatus("STRANGER");
            }
        });

        return friends;
    }

    @Override
    @Transactional
    public void unfriend(Integer userId, Integer friendId) {
        if (!friendRepository.existsByUserIdAndFriendId(userId, friendId)) {
            throw new RuntimeException("You are not friends with this user");
        }
        friendRepository.removeFriendship(userId, friendId);
    }
}
