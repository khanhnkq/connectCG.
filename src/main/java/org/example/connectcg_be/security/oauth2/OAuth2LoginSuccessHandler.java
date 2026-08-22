package org.example.connectcg_be.security.oauth2;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.connectcg_be.entity.User;
import org.example.connectcg_be.repository.UserRepository;
import org.example.connectcg_be.security.JwtTokenProvider;
import org.example.connectcg_be.security.AuthCookieService;
import org.example.connectcg_be.security.UserPrincipal;
import org.example.connectcg_be.service.RefreshTokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {
    @Autowired
    private JwtTokenProvider tokenProvider;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RefreshTokenService refreshTokenService;
    @Autowired
    private AuthCookieService authCookieService;
    @Value("${frontend.url}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = oAuth2User.getAttribute("email");
        // [LOGIC MỚI] Check nếu email bị null
        if (email == null || email.isEmpty()) {
            String idSource = oAuth2User.getAttribute("id"); // Lấy ID Facebook
            if (idSource != null) {
                email = idSource + "@facebook.id"; // Tạo email giả: 12345678@facebook.id
            } else {
                // Trường hợp cực hiếm: không có cả ID -> Báo lỗi
                throw new OAuth2AuthenticationException("Email not found from OAuth2 provider");
            }
        }
        User user = userRepository.findByEmail(email).orElseThrow();
        UserPrincipal userPrincipal = UserPrincipal.create(user);
        RefreshTokenService.IssuedRefreshToken refreshToken = refreshTokenService.issue(user, request);
        String accessToken = tokenProvider.generateToken(userPrincipal, refreshToken.familyId());
        authCookieService.writeSessionCookies(response, accessToken, refreshToken.rawToken());
        String targetUrl = frontendUrl + "/oauth2/redirect";
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}
