package org.example.connectcg_be.service;

import org.example.connectcg_be.dto.GroupPostDTO;
import org.example.connectcg_be.entity.Media;
import org.example.connectcg_be.entity.Post;
import org.example.connectcg_be.entity.PostMedia;
import org.example.connectcg_be.entity.PostMediaId;
import org.example.connectcg_be.entity.Reaction;
import org.example.connectcg_be.entity.ReactionId;
import org.example.connectcg_be.entity.User;
import org.example.connectcg_be.entity.UserAvatar;
import org.example.connectcg_be.entity.UserProfile;
import org.example.connectcg_be.repository.FriendRepository;
import org.example.connectcg_be.repository.PostMediaRepository;
import org.example.connectcg_be.repository.PostRepository;
import org.example.connectcg_be.repository.ReactionRepository;
import org.example.connectcg_be.repository.UserAvatarRepository;
import org.example.connectcg_be.repository.UserProfileRepository;
import org.example.connectcg_be.service.impl.PostServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostServiceQueryEfficiencyTest {

    @Mock
    private PostRepository postRepository;
    @Mock
    private ReactionRepository reactionRepository;
    @Mock
    private PostMediaRepository postMediaRepository;
    @Mock
    private UserProfileRepository userProfileRepository;
    @Mock
    private UserAvatarRepository userAvatarRepository;
    @Mock
    private FriendRepository friendRepository;
    @Mock
    private GroupMemberService groupMemberService;

    @InjectMocks
    private PostServiceImpl postService;

    @Test
    void newsfeedLoadsEnrichmentInBatchesInsteadOfPerPost() {
        Integer userId = 99;
        List<Post> posts = List.of(post(1, 10, "alice"), post(2, 20, "bob"));

        when(friendRepository.findAllFriendIds(userId)).thenReturn(List.of(10, 20));
        when(groupMemberService.getAcceptedGroupIds(userId, "ACCEPTED")).thenReturn(List.of());
        when(postRepository.findNewsfeedPosts(eq(userId), anyList(), anyList(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(posts));

        Page<GroupPostDTO> result = postService.getNewsfeedPosts(userId, 0, 20);

        assertEquals(List.of("alice", "bob"), result.getContent().stream().map(GroupPostDTO::getAuthorName).toList());
        verify(userProfileRepository, never()).findByUserId(anyInt());
        verify(userAvatarRepository, never()).findByUserIdAndIsCurrentTrue(anyInt());
        verify(postMediaRepository, never()).findAllByPostId(anyInt());
        verify(reactionRepository, never()).findById(any(ReactionId.class));
    }

    @Test
    void newsfeedPreservesProfileAvatarMediaAndReactionWhenUsingBatches() {
        Integer viewerId = 99;
        Post post = post(1, 10, "alice");
        UserProfile profile = new UserProfile();
        profile.setUser(post.getAuthor());
        profile.setFullName("Alice Nguyen");

        Media avatarMedia = media(100, "https://cdn/avatar.jpg", "IMAGE");
        UserAvatar avatar = new UserAvatar();
        avatar.setUser(post.getAuthor());
        avatar.setMedia(avatarMedia);
        avatar.setIsCurrent(true);

        Media postImage = media(200, "https://cdn/post.jpg", "IMAGE");
        PostMedia postMedia = new PostMedia(new PostMediaId(post.getId(), postImage.getId()), post, postImage, 0);
        Reaction reaction = new Reaction(
                new ReactionId(viewerId, post.getId()), new User(), post, "LOVE", Instant.now());

        when(friendRepository.findAllFriendIds(viewerId)).thenReturn(List.of(10));
        when(groupMemberService.getAcceptedGroupIds(viewerId, "ACCEPTED")).thenReturn(List.of());
        when(postRepository.findNewsfeedPosts(eq(viewerId), anyList(), anyList(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(post)));
        when(userProfileRepository.findAllByUserIdIn(any())).thenReturn(List.of(profile));
        when(userAvatarRepository.findCurrentByUserIds(any())).thenReturn(List.of(avatar));
        when(postMediaRepository.findAllByPostIdIn(any())).thenReturn(List.of(postMedia));
        when(reactionRepository.findAllByUserIdAndPostIdIn(eq(viewerId), any())).thenReturn(List.of(reaction));

        GroupPostDTO result = postService.getNewsfeedPosts(viewerId, 0, 20).getContent().get(0);

        assertEquals("Alice Nguyen", result.getAuthorFullName());
        assertEquals("https://cdn/avatar.jpg", result.getAuthorAvatar());
        assertEquals(List.of("https://cdn/post.jpg"), result.getImages());
        assertEquals("LOVE", result.getCurrentUserReaction());
    }

    private Post post(Integer postId, Integer authorId, String username) {
        User author = new User();
        author.setId(authorId);
        author.setUsername(username);

        Post post = new Post();
        post.setId(postId);
        post.setAuthor(author);
        post.setContent("content-" + postId);
        post.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        post.setStatus("APPROVED");
        post.setVisibility("PUBLIC");
        post.setReactCount(0);
        post.setCommentCount(0);
        post.setShareCount(0);
        return post;
    }

    private Media media(Integer id, String url, String type) {
        Media media = new Media();
        media.setId(id);
        media.setUrl(url);
        media.setType(type);
        media.setIsDeleted(false);
        return media;
    }
}
