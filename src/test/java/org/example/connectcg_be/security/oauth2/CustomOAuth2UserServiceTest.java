package org.example.connectcg_be.security.oauth2;

import org.example.connectcg_be.entity.OAuthIdentity;
import org.example.connectcg_be.entity.User;
import org.example.connectcg_be.entity.UserProfile;
import org.example.connectcg_be.repository.OAuthIdentityRepository;
import org.example.connectcg_be.repository.UserProfileRepository;
import org.example.connectcg_be.repository.UserRepository;
import org.example.connectcg_be.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomOAuth2UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private OAuthIdentityRepository oauthIdentityRepository;
    @Mock
    private UserProfileRepository userProfileRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private CustomOAuth2UserService customOAuth2UserService;

    @Mock
    private OAuth2User oAuth2User;

    @BeforeEach
    void setUp() {
    }

    @Test
    void processOAuth2User_ExistingIdentity_ReturnsLinkedUserDirectly() {
        when(oAuth2User.getAttribute("sub")).thenReturn("google-sub-12345");
        User user = new User();
        user.setId(10);
        user.setUsername("existing_user");
        user.setIsEnabled(true);
        user.setIsLocked(false);
        user.setIsDeleted(false);

        OAuthIdentity identity = new OAuthIdentity(user, "google", "google-sub-12345", "user@example.com");
        when(oauthIdentityRepository.findByProviderAndProviderUserId("google", "google-sub-12345"))
                .thenReturn(Optional.of(identity));

        OAuth2User result = customOAuth2UserService.processOAuth2User("google", oAuth2User);

        assertInstanceOf(UserPrincipal.class, result);
        UserPrincipal principal = (UserPrincipal) result;
        assertEquals(10, principal.getId());
        assertEquals("existing_user", principal.getUsername());
        verify(userRepository, never()).findByEmail(anyString());
        verify(oauthIdentityRepository, never()).save(any());
    }

    @Test
    void processOAuth2User_ExistingEmail_UnverifiedUser_ThrowsException_PreventingAccountTakeover() {
        // P0 vulnerability test!
        when(oAuth2User.getAttribute("sub")).thenReturn("google-sub-victim");
        when(oAuth2User.getAttribute("email")).thenReturn("victim@example.com");
        when(oAuth2User.getAttribute("email_verified")).thenReturn(true);

        when(oauthIdentityRepository.findByProviderAndProviderUserId("google", "google-sub-victim"))
                .thenReturn(Optional.empty());

        User unverifiedUser = new User();
        unverifiedUser.setId(99);
        unverifiedUser.setEmail("victim@example.com");
        unverifiedUser.setIsEnabled(false); // Email not verified yet

        when(userRepository.findByEmail("victim@example.com"))
                .thenReturn(Optional.of(unverifiedUser));

        OAuth2AuthenticationException ex = assertThrows(
                OAuth2AuthenticationException.class,
                () -> customOAuth2UserService.processOAuth2User("google", oAuth2User)
        );

        assertTrue(ex.getMessage().contains("chưa được kích hoạt"));
        // Ensure user was NEVER activated
        assertFalse(unverifiedUser.getIsEnabled());
        verify(userRepository, never()).save(unverifiedUser);
        verify(oauthIdentityRepository, never()).save(any());
    }

    @Test
    void processOAuth2User_ExistingEmail_ActiveUser_VerifiedEmail_LinksIdentity() {
        when(oAuth2User.getAttribute("sub")).thenReturn("google-sub-verified");
        when(oAuth2User.getAttribute("email")).thenReturn("active@example.com");
        when(oAuth2User.getAttribute("email_verified")).thenReturn(true);

        when(oauthIdentityRepository.findByProviderAndProviderUserId("google", "google-sub-verified"))
                .thenReturn(Optional.empty());

        User activeUser = new User();
        activeUser.setId(5);
        activeUser.setEmail("active@example.com");
        activeUser.setUsername("active_user");
        activeUser.setIsEnabled(true);
        activeUser.setIsLocked(false);
        activeUser.setIsDeleted(false);

        when(userRepository.findByEmail("active@example.com"))
                .thenReturn(Optional.of(activeUser));

        OAuth2User result = customOAuth2UserService.processOAuth2User("google", oAuth2User);

        assertInstanceOf(UserPrincipal.class, result);
        verify(oauthIdentityRepository).save(any(OAuthIdentity.class));
    }

    @Test
    void processOAuth2User_BrandNewUser_RegistersAndCreatesProfileAndIdentity() {
        when(oAuth2User.getAttribute("sub")).thenReturn("google-new-sub");
        when(oAuth2User.getAttribute("email")).thenReturn("newuser@gmail.com");
        when(oAuth2User.getAttribute("email_verified")).thenReturn(true);
        when(oAuth2User.getAttribute("name")).thenReturn("New User FullName");

        when(oauthIdentityRepository.findByProviderAndProviderUserId("google", "google-new-sub"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("newuser@gmail.com"))
                .thenReturn(Optional.empty());
        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$encodedHash");

        User savedUser = new User();
        savedUser.setId(101);
        savedUser.setEmail("newuser@gmail.com");
        savedUser.setUsername("newuser");
        savedUser.setIsEnabled(true);
        savedUser.setIsLocked(false);
        savedUser.setIsDeleted(false);

        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        OAuth2User result = customOAuth2UserService.processOAuth2User("google", oAuth2User);

        assertInstanceOf(UserPrincipal.class, result);
        assertEquals(101, ((UserPrincipal) result).getId());
        verify(userRepository).save(any(User.class));
        verify(userProfileRepository).save(any(UserProfile.class));
        verify(oauthIdentityRepository).save(any(OAuthIdentity.class));
    }

    @Test
    void processOAuth2User_DuplicateUsername_AppendsCounter() {
        when(oAuth2User.getAttribute("sub")).thenReturn("google-dup-sub");
        when(oAuth2User.getAttribute("email")).thenReturn("john@gmail.com");
        when(oAuth2User.getAttribute("email_verified")).thenReturn(true);

        when(oauthIdentityRepository.findByProviderAndProviderUserId("google", "google-dup-sub"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("john@gmail.com"))
                .thenReturn(Optional.empty());
        when(userRepository.existsByUsername("john")).thenReturn(true);
        when(userRepository.existsByUsername("john1")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$encodedHash");

        User savedUser = new User();
        savedUser.setId(102);
        savedUser.setUsername("john1");
        savedUser.setIsEnabled(true);
        savedUser.setIsLocked(false);
        savedUser.setIsDeleted(false);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        OAuth2User result = customOAuth2UserService.processOAuth2User("google", oAuth2User);

        assertNotNull(result);
        verify(userRepository).save(argThat(user -> "john1".equals(user.getUsername())));
    }
}
