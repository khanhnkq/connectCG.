package org.example.connectcg_be.service;

import org.example.connectcg_be.entity.Post;
import org.example.connectcg_be.entity.User;
import org.example.connectcg_be.repository.PostRepository;
import org.example.connectcg_be.repository.PostMediaRepository;
import org.example.connectcg_be.repository.ReactionRepository;
import org.example.connectcg_be.repository.UserAvatarRepository;
import org.example.connectcg_be.repository.UserProfileRepository;
import org.example.connectcg_be.repository.UserRepository;
import org.example.connectcg_be.service.impl.PostServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostP2BusinessRulesTest {
    @Mock private PostRepository postRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserProfileRepository userProfileRepository;
    @Mock private UserAvatarRepository userAvatarRepository;
    @Mock private PostMediaRepository postMediaRepository;
    @Mock private ReactionRepository reactionRepository;
    @Mock private PostRealtimeService postRealtimeService;

    @InjectMocks private PostServiceImpl postService;

    @Test
    void deletingAShareSynchronizesRootCountAndIsIdempotent() {
        User author = new User();
        author.setId(2);
        author.setRole("USER");
        author.setUsername("author");

        Post root = new Post();
        root.setId(10);
        root.setAuthor(author);
        root.setShareCount(1);
        root.setReactCount(0);
        root.setCommentCount(0);
        root.setIsDeleted(false);

        Post share = new Post();
        share.setId(20);
        share.setAuthor(author);
        share.setOriginalPost(root);
        share.setIsDeleted(false);

        when(postRepository.findById(20)).thenReturn(Optional.of(share));
        when(userRepository.findById(2)).thenReturn(Optional.of(author));
        when(postRepository.countByOriginalPostIdAndIsDeletedFalse(10)).thenReturn(0L);

        postService.deletePost(20, 2);
        postService.deletePost(20, 2);

        assertTrue(share.getIsDeleted());
        verify(postRepository, times(1)).updateShareCount(10, 0);
    }
}
