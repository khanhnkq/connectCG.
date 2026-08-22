package org.example.connectcg_be.service;

import org.example.connectcg_be.entity.Post;
import org.example.connectcg_be.entity.Reaction;
import org.example.connectcg_be.repository.PostRepository;
import org.example.connectcg_be.repository.ReactionRepository;
import org.example.connectcg_be.service.impl.ReactionServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReactionP1BusinessRulesTest {
    @Mock
    private PostRepository postRepository;
    @Mock
    private ReactionRepository reactionRepository;
    @Mock
    private PostAccessPolicy postAccessPolicy;

    @InjectMocks
    private ReactionServiceImpl reactionService;

    @Test
    void reactionRequiresPostAccess() {
        Post post = new Post();
        post.setId(10);
        when(postRepository.findById(10)).thenReturn(Optional.of(post));
        doThrow(new AccessDeniedException("forbidden"))
                .when(postAccessPolicy).requireCanView(post, 2);

        assertThrows(AccessDeniedException.class, () -> reactionService.reactToPost(10, 2, "LIKE"));
        verify(reactionRepository, never()).save(any(Reaction.class));
    }
}
