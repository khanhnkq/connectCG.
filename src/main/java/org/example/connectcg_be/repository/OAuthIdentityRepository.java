package org.example.connectcg_be.repository;

import org.example.connectcg_be.entity.OAuthIdentity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OAuthIdentityRepository extends JpaRepository<OAuthIdentity, Integer> {
    Optional<OAuthIdentity> findByProviderAndProviderUserId(String provider, String providerUserId);
    List<OAuthIdentity> findByUserId(Integer userId);
    boolean existsByProviderAndProviderUserId(String provider, String providerUserId);
}
