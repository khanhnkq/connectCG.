package org.example.connectcg_be.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "oauth_identities", uniqueConstraints = {
        @UniqueConstraint(name = "uq_oauth_identities_provider_uid", columnNames = {"provider", "provider_user_id"})
})
public class OAuthIdentity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @NotNull
    @Size(max = 32)
    @Column(name = "provider", nullable = false, length = 32)
    private String provider;

    @NotNull
    @Size(max = 255)
    @Column(name = "provider_user_id", nullable = false)
    private String providerUserId;

    @Size(max = 255)
    @Column(name = "email")
    private String email;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public OAuthIdentity(User user, String provider, String providerUserId, String email) {
        this.user = user;
        this.provider = provider;
        this.providerUserId = providerUserId;
        this.email = email;
        this.createdAt = Instant.now();
    }
}
