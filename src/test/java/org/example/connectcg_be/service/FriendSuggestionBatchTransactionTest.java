package org.example.connectcg_be.service;

import org.example.connectcg_be.repository.*;
import org.example.connectcg_be.service.impl.FriendSuggestionServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FriendSuggestionBatchTransactionTest {

    @Mock
    private FriendSuggestionRepository friendSuggestionRepository;

    @Mock
    private DismissedSuggestionRepository dismissedSuggestionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private UserAvatarRepository userAvatarRepository;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private FriendSuggestionServiceImpl friendSuggestionService;

    @Test
    @DisplayName("Failure in one user transaction does not abort processing of subsequent users")
    void failureInOneUserDoesNotAbortSubsequentUsers() {
        when(userRepository.findActiveUserIds()).thenReturn(List.of(1, 2));

        // When transactionTemplate.executeWithoutResult is called:
        // for user 1, throw an exception; for user 2, succeed normally.
        doAnswer(invocation -> {
            Consumer<TransactionStatus> action = invocation.getArgument(0);
            action.accept(mock(TransactionStatus.class));
            throw new RuntimeException("Database error on user 1");
        }).doAnswer(invocation -> {
            Consumer<TransactionStatus> action = invocation.getArgument(0);
            action.accept(mock(TransactionStatus.class));
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        friendSuggestionService.refreshAllSuggestions();

        // Must have attempted to run for BOTH user 1 and user 2 despite error on user 1
        verify(transactionTemplate, times(2)).executeWithoutResult(any());
    }
}
