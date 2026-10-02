package org.example.connectcg_be.security.oauth2;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.connectcg_be.entity.OAuthIdentity;
import org.example.connectcg_be.entity.User;
import org.example.connectcg_be.entity.UserProfile;
import org.example.connectcg_be.repository.OAuthIdentityRepository;
import org.example.connectcg_be.repository.UserProfileRepository;
import org.example.connectcg_be.repository.UserRepository;
import org.example.connectcg_be.security.UserPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;
    private final OAuthIdentityRepository oauthIdentityRepository;
    private final UserProfileRepository userProfileRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        String provider = userRequest.getClientRegistration().getRegistrationId().toLowerCase(Locale.ROOT);
        return processOAuth2User(provider, oAuth2User);
    }

    @Transactional
    public OAuth2User processOAuth2User(String provider, OAuth2User oAuth2User) throws OAuth2AuthenticationException {
        String providerUserId = extractProviderUserId(oAuth2User);
        if (providerUserId == null || providerUserId.isBlank()) {
            throw new OAuth2AuthenticationException(new OAuth2Error("invalid_user_id"),
                    "Không thể xác thực danh tính người dùng từ " + provider);
        }

        String email = oAuth2User.getAttribute("email");
        Boolean emailVerified = oAuth2User.getAttribute("email_verified");

        // 1. Look up existing OAuth identity by (provider, providerUserId)
        Optional<OAuthIdentity> identityOpt = oauthIdentityRepository
                .findByProviderAndProviderUserId(provider, providerUserId);

        User user;
        if (identityOpt.isPresent()) {
            user = identityOpt.get().getUser();
            validateUserStatus(user);
        } else {
            // 2. No existing link for this OAuth provider+id: check if email exists in system
            Optional<User> userWithEmailOpt = (email != null && !email.isBlank())
                    ? userRepository.findByEmail(email)
                    : Optional.empty();

            if (userWithEmailOpt.isPresent()) {
                user = userWithEmailOpt.get();

                // P0 FIX: Never auto-link or auto-activate an unverified / pending account!
                if (Boolean.FALSE.equals(user.getIsEnabled())) {
                    throw new OAuth2AuthenticationException(new OAuth2Error("unverified_account"),
                            "Email này đã được đăng ký nhưng tài khoản chưa được kích hoạt. Vui lòng xác thực email trước khi liên kết.");
                }

                validateUserStatus(user);

                // For linking, ensure the provider has verified the email
                if (emailVerified != null && !emailVerified) {
                    throw new OAuth2AuthenticationException(new OAuth2Error("unverified_provider_email"),
                            "Email từ nhà cung cấp OAuth chưa được xác thực.");
                }

                // Link this OAuth identity to existing active user
                OAuthIdentity identity = new OAuthIdentity(user, provider, providerUserId, email);
                oauthIdentityRepository.save(identity);
                log.info("Linked new OAuth provider {} for userId={}", provider, user.getId());
            } else {
                // 3. Brand new user registration via OAuth
                user = registerNewOAuthUser(provider, providerUserId, email, oAuth2User);
            }
        }

        return UserPrincipal.create(user, oAuth2User.getAttributes());
    }

    private void validateUserStatus(User user) {
        if (Boolean.TRUE.equals(user.getIsDeleted())) {
            throw new OAuth2AuthenticationException(new OAuth2Error("account_deleted"), "Tài khoản không tồn tại hoặc đã bị xóa.");
        }
        if (Boolean.TRUE.equals(user.getPermanentLocked())) {
            throw new OAuth2AuthenticationException(new OAuth2Error("account_locked"), "Tài khoản của bạn đã bị khóa vĩnh viễn.");
        }
        if (Boolean.TRUE.equals(user.getIsLocked())) {
            if (user.getLockedUntil() == null || Instant.now().isBefore(user.getLockedUntil())) {
                throw new OAuth2AuthenticationException(new OAuth2Error("account_locked"), "Tài khoản của bạn đang bị tạm khóa.");
            }
        }
    }

    private User registerNewOAuthUser(String provider, String providerUserId, String email, OAuth2User oAuth2User) {
        String effectiveEmail = (email != null && !email.isBlank())
                ? email
                : (providerUserId + "@" + provider + ".oauth");

        String baseUsername = generateBaseUsername(email, providerUserId, provider);
        String uniqueUsername = generateUniqueUsername(baseUsername);

        User newUser = new User();
        newUser.setEmail(effectiveEmail);
        newUser.setUsername(uniqueUsername);
        // Random non-empty password so direct password login cannot be guessed
        newUser.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        newUser.setRole("USER");
        newUser.setIsEnabled(true);
        newUser.setIsDeleted(false);
        newUser.setIsLocked(false);
        newUser.setCreatedAt(Instant.now());
        User savedUser = userRepository.save(newUser);

        // Create default profile
        UserProfile profile = new UserProfile();
        profile.setUser(savedUser);
        String name = oAuth2User.getAttribute("name");
        profile.setFullName(name != null && !name.isBlank() ? name : uniqueUsername);
        userProfileRepository.save(profile);

        // Save OAuth identity
        OAuthIdentity identity = new OAuthIdentity(savedUser, provider, providerUserId, effectiveEmail);
        oauthIdentityRepository.save(identity);
        log.info("Registered new user via OAuth provider={} userId={}", provider, savedUser.getId());

        return savedUser;
    }

    private String extractProviderUserId(OAuth2User oAuth2User) {
        String sub = oAuth2User.getAttribute("sub");
        if (sub != null && !sub.isBlank()) {
            return sub;
        }
        Object idObj = oAuth2User.getAttribute("id");
        if (idObj != null) {
            return String.valueOf(idObj);
        }
        return null;
    }

    private String generateBaseUsername(String email, String providerUserId, String provider) {
        if (email != null && email.contains("@")) {
            String prefix = email.split("@")[0].replaceAll("[^a-zA-Z0-9_.]", "");
            if (!prefix.isBlank()) {
                return prefix;
            }
        }
        return provider + "_" + (providerUserId.length() > 8 ? providerUserId.substring(0, 8) : providerUserId);
    }

    private String generateUniqueUsername(String baseUsername) {
        String candidate = baseUsername;
        int counter = 1;
        while (Boolean.TRUE.equals(userRepository.existsByUsername(candidate))) {
            candidate = baseUsername + counter;
            counter++;
        }
        return candidate;
    }
}
