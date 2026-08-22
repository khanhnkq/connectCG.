package org.example.connectcg_be.service;

import org.example.connectcg_be.dto.FriendSuggestionDTO;
import org.example.connectcg_be.entity.User;
import org.example.connectcg_be.entity.UserProfile;
import org.example.connectcg_be.repository.UserProfileRepository;
import org.example.connectcg_be.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FriendSuggestionServiceIntegrationTest {

    @Autowired
    private FriendSuggestionService friendSuggestionService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Test
    void calculatesSuggestionsWithPostgreSqlAggregationAndIntervals() {
        User viewer = saveUser("suggestion_viewer");
        User candidate = saveUser("suggestion_candidate");
        saveProfile(viewer, "Người xem", "Hồ Chí Minh");
        saveProfile(candidate, "Người được gợi ý", "Hồ Chí Minh");

        friendSuggestionService.calculateSuggestions(viewer.getId());

        Page<FriendSuggestionDTO> result = friendSuggestionService.getSuggestions(
                viewer.getId(), PageRequest.of(0, 10));

        assertEquals(1, result.getTotalElements());
        assertEquals(candidate.getId(), result.getContent().get(0).getUserId());
        assertEquals("Cùng sống tại Hồ Chí Minh", result.getContent().get(0).getDescription());
    }

    private User saveUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPasswordHash("test-password-hash");
        user.setRole("USER");
        user.setIsEnabled(true);
        user.setIsLocked(false);
        user.setIsDeleted(false);
        user.setCreatedAt(Instant.now());
        return userRepository.save(user);
    }

    private void saveProfile(User user, String fullName, String cityName) {
        UserProfile profile = new UserProfile();
        profile.setUser(user);
        profile.setFullName(fullName);
        profile.setCityName(cityName);
        profile.setUpdatedAt(Instant.now());
        userProfileRepository.save(profile);
    }
}
