package org.example.connectcg_be.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.example.connectcg_be.entity.Hobby;
import org.example.connectcg_be.entity.Media;
import org.example.connectcg_be.entity.Post;
import org.example.connectcg_be.entity.PostMedia;
import org.example.connectcg_be.entity.PostMediaId;
import org.example.connectcg_be.entity.User;
import org.example.connectcg_be.entity.UserAvatar;
import org.example.connectcg_be.entity.UserHobby;
import org.example.connectcg_be.entity.UserHobbyId;
import org.example.connectcg_be.entity.UserProfile;
import org.example.connectcg_be.repository.HobbyRepository;
import org.example.connectcg_be.repository.MediaRepository;
import org.example.connectcg_be.repository.PostMediaRepository;
import org.example.connectcg_be.repository.PostRepository;
import org.example.connectcg_be.repository.UserAvatarRepository;
import org.example.connectcg_be.repository.UserHobbyRepository;
import org.example.connectcg_be.repository.UserProfileRepository;
import org.example.connectcg_be.repository.UserRepository;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class QueryCountIntegrationTest {

    @Autowired
    private PostService postService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserProfileRepository userProfileRepository;
    @Autowired
    private UserAvatarRepository userAvatarRepository;
    @Autowired
    private UserHobbyRepository userHobbyRepository;
    @Autowired
    private HobbyRepository hobbyRepository;
    @Autowired
    private MediaRepository mediaRepository;
    @Autowired
    private PostRepository postRepository;
    @Autowired
    private PostMediaRepository postMediaRepository;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void feedQueryCountStaysConstantAsPageSizeGrows() {
        User viewer = saveUser("query_viewer");
        for (int index = 1; index <= 6; index++) {
            User author = saveUser("query_author_" + index);
            saveProfile(author, "Author " + index);
            saveAvatar(author, "https://cdn/avatar-" + index + ".jpg");
            savePost(author, index);
        }
        flushAndResetStatistics();

        assertEquals(1, postService.getNewsfeedPosts(viewer.getId(), 0, 1).getNumberOfElements());
        long onePostQueryCount = statistics().getPrepareStatementCount();

        entityManager.clear();
        statistics().clear();
        assertEquals(5, postService.getNewsfeedPosts(viewer.getId(), 0, 5).getNumberOfElements());
        long fivePostQueryCount = statistics().getPrepareStatementCount();

        assertEquals(onePostQueryCount, fivePostQueryCount);
        assertTrue(fivePostQueryCount <= 8, "Feed should use a constant batch-query budget");
    }

    @Test
    void profileAssociationsUseFetchGraphsInsteadOfLazyQueries() {
        User user = saveUser("profile_fetch_user");
        saveAvatar(user, "https://cdn/profile-avatar.jpg");

        List<Hobby> hobbies = new ArrayList<>();
        for (int index = 1; index <= 5; index++) {
            Hobby hobby = new Hobby(null, "CODE_" + index, "Hobby " + index, null, "TEST");
            hobbies.add(hobbyRepository.save(hobby));
        }
        for (Hobby hobby : hobbies) {
            UserHobby userHobby = new UserHobby();
            userHobby.setId(new UserHobbyId(user.getId(), hobby.getId()));
            userHobby.setUser(user);
            userHobby.setHobby(hobby);
            userHobbyRepository.save(userHobby);
        }
        flushAndResetStatistics();

        UserAvatar avatar = userAvatarRepository.findByUserIdAndIsCurrentTrue(user.getId());
        assertEquals("https://cdn/profile-avatar.jpg", avatar.getMedia().getUrl());
        assertEquals(1, statistics().getPrepareStatementCount());

        entityManager.clear();
        statistics().clear();
        List<String> hobbyNames = userHobbyRepository.findByUserId(user.getId()).stream()
                .map(userHobby -> userHobby.getHobby().getName())
                .toList();
        assertEquals(5, hobbyNames.size());
        assertEquals(1, statistics().getPrepareStatementCount());
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

    private void saveProfile(User user, String fullName) {
        UserProfile profile = new UserProfile();
        profile.setUser(user);
        profile.setFullName(fullName);
        profile.setUpdatedAt(Instant.now());
        userProfileRepository.save(profile);
    }

    private void saveAvatar(User user, String url) {
        Media media = new Media();
        media.setUploader(user);
        media.setUrl(url);
        media.setType("IMAGE");
        media.setIsDeleted(false);
        media.setUploadedAt(Instant.now());
        media = mediaRepository.save(media);

        UserAvatar avatar = new UserAvatar();
        avatar.setUser(user);
        avatar.setMedia(media);
        avatar.setIsCurrent(true);
        avatar.setSetAt(Instant.now());
        userAvatarRepository.save(avatar);
    }

    private void savePost(User author, int index) {
        Post post = new Post();
        post.setAuthor(author);
        post.setContent("Post " + index);
        post.setVisibility("PUBLIC");
        post.setStatus("APPROVED");
        post.setIsDeleted(false);
        post.setCommentCount(0);
        post.setReactCount(0);
        post.setShareCount(0);
        post.setCreatedAt(Instant.now().plusSeconds(index));
        post.setUpdatedAt(Instant.now());
        post = postRepository.save(post);

        Media media = new Media();
        media.setUploader(author);
        media.setUrl("https://cdn/post-" + index + ".jpg");
        media.setType("IMAGE");
        media.setIsDeleted(false);
        media.setUploadedAt(Instant.now());
        media = mediaRepository.save(media);

        PostMedia postMedia = new PostMedia(new PostMediaId(post.getId(), media.getId()), post, media, 0);
        postMediaRepository.save(postMedia);
    }

    private void flushAndResetStatistics() {
        entityManager.flush();
        entityManager.clear();
        statistics().clear();
    }

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }
}
