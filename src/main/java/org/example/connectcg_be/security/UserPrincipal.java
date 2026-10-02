package org.example.connectcg_be.security;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.example.connectcg_be.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Getter
@AllArgsConstructor
public class UserPrincipal implements UserDetails, OAuth2User {

    private Integer id;
    private String username;
    private String email;
    private boolean isLocked;
    private boolean isDeleted;
    @JsonIgnore
    private String password;
    private boolean isEnabled;
    private Collection<? extends GrantedAuthority> authorities;
    private int authVersion;
    private Map<String, Object> attributes;

    public UserPrincipal(Integer id, String username, String email, String password, boolean isEnabled,
            boolean isLocked, boolean isDeleted,
            Collection<? extends GrantedAuthority> authorities) {
        this(id, username, email, password, isEnabled, isLocked, isDeleted, authorities, 0);
    }

    public UserPrincipal(Integer id, String username, String email, String password, boolean isEnabled,
            boolean isLocked, boolean isDeleted,
            Collection<? extends GrantedAuthority> authorities, int authVersion) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.password = password;
        this.isEnabled = isEnabled;
        this.isLocked = isLocked;
        this.isDeleted = isDeleted;
        this.authorities = authorities;
        this.authVersion = authVersion;
    }

    // Hàm build từ Entity User sang UserPrincipal
    public static UserPrincipal create(User user) {
        // Mặc định role trong DB của bạn là String (VD: "USER"), cần thêm prefix ROLE_
        List<GrantedAuthority> authorities = Collections.singletonList(
                new SimpleGrantedAuthority("ROLE_" + user.getRole()));

        boolean actuallyLocked = Boolean.TRUE.equals(user.getPermanentLocked());
        if (!actuallyLocked && Boolean.TRUE.equals(user.getIsLocked())) {
            // Check if temporary lock has expired
            if (user.getLockedUntil() == null || java.time.Instant.now().isBefore(user.getLockedUntil())) {
                actuallyLocked = true;
            }
        }

        return new UserPrincipal(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getPasswordHash(),
                Boolean.TRUE.equals(user.getIsEnabled()),
                actuallyLocked,
                Boolean.TRUE.equals(user.getIsDeleted()),
                authorities,
                user.getAuthVersion() == null ? 0 : user.getAuthVersion());
    }

    public static UserPrincipal create(User user, Map<String, Object> attributes) {
        UserPrincipal principal = UserPrincipal.create(user);
        principal.attributes = attributes;
        return principal;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes != null ? attributes : Collections.emptyMap();
    }

    @Override
    public String getName() {
        return username != null ? username : String.valueOf(id);
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !isLocked;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        // Tài khoản được bật nếu: đã kích hoạt Email VÀ chưa bị xóa
        return isEnabled && !isDeleted;
    }
}
