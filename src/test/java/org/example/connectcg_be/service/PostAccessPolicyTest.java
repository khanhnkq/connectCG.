package org.example.connectcg_be.service;

import org.example.connectcg_be.entity.Group;
import org.example.connectcg_be.entity.GroupMember;
import org.example.connectcg_be.entity.GroupMemberId;
import org.example.connectcg_be.entity.Post;
import org.example.connectcg_be.entity.User;
import org.example.connectcg_be.repository.FriendRepository;
import org.example.connectcg_be.repository.GroupMemberRepository;
import org.example.connectcg_be.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostAccessPolicyTest {
    @Mock
    private UserRepository userRepository;
    @Mock
    private FriendRepository friendRepository;
    @Mock
    private GroupMemberRepository groupMemberRepository;

    private PostAccessPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new PostAccessPolicy(userRepository, friendRepository, groupMemberRepository);
    }

    @Test
    void privatePostIsHiddenFromOtherUsers() {
        User viewer = user(2, "USER");
        when(userRepository.findById(2)).thenReturn(Optional.of(viewer));

        assertFalse(policy.canView(post(user(1, "USER"), "APPROVED", "PRIVATE"), 2));
    }

    @Test
    void friendsPostIsVisibleToFriend() {
        User viewer = user(2, "USER");
        when(userRepository.findById(2)).thenReturn(Optional.of(viewer));
        when(friendRepository.existsByUserIdAndFriendId(2, 1)).thenReturn(true);

        assertTrue(policy.canView(post(user(1, "USER"), "APPROVED", "FRIENDS"), 2));
    }

    @Test
    void privateGroupPostIsHiddenFromOutsider() {
        User viewer = user(2, "USER");
        Group group = group(10, "PRIVATE", user(1, "USER"));
        Post post = post(user(3, "USER"), "APPROVED", "PUBLIC");
        post.setGroup(group);
        when(userRepository.findById(2)).thenReturn(Optional.of(viewer));
        when(groupMemberRepository.findById(new GroupMemberId(10, 2))).thenReturn(Optional.empty());

        assertFalse(policy.canView(post, 2));
    }

    @Test
    void bannedUserCannotViewPublicGroupPost() {
        User viewer = user(2, "USER");
        Group group = group(10, "PUBLIC", user(1, "USER"));
        Post post = post(user(3, "USER"), "APPROVED", "PUBLIC");
        post.setGroup(group);
        GroupMember bannedMember = membership(group, viewer, "MEMBER", "BANNED");
        when(userRepository.findById(2)).thenReturn(Optional.of(viewer));
        when(groupMemberRepository.findById(new GroupMemberId(10, 2))).thenReturn(Optional.of(bannedMember));

        assertFalse(policy.canView(post, 2));
    }

    @Test
    void authorCanViewOwnPendingPost() {
        User author = user(1, "USER");
        when(userRepository.findById(1)).thenReturn(Optional.of(author));

        assertTrue(policy.canView(post(author, "PENDING", "PUBLIC"), 1));
    }

    @Test
    void regularUserCannotViewAnotherUsersPendingPost() {
        User viewer = user(2, "USER");
        when(userRepository.findById(2)).thenReturn(Optional.of(viewer));

        assertFalse(policy.canView(post(user(1, "USER"), "PENDING", "PUBLIC"), 2));
    }

    @Test
    void deletedPostIsHiddenEvenFromSystemAdmin() {
        Post post = post(user(1, "USER"), "APPROVED", "PUBLIC");
        post.setIsDeleted(true);

        assertFalse(policy.canView(post, 9));
    }

    private User user(Integer id, String role) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        user.setIsDeleted(false);
        user.setIsLocked(false);
        return user;
    }

    private Post post(User author, String status, String visibility) {
        Post post = new Post();
        post.setId(100);
        post.setAuthor(author);
        post.setStatus(status);
        post.setVisibility(visibility);
        post.setIsDeleted(false);
        return post;
    }

    private Group group(Integer id, String privacy, User owner) {
        Group group = new Group();
        group.setId(id);
        group.setPrivacy(privacy);
        group.setOwner(owner);
        group.setIsDeleted(false);
        return group;
    }

    private GroupMember membership(Group group, User user, String role, String status) {
        GroupMember member = new GroupMember();
        member.setId(new GroupMemberId(group.getId(), user.getId()));
        member.setGroup(group);
        member.setUser(user);
        member.setRole(role);
        member.setStatus(status);
        return member;
    }
}
