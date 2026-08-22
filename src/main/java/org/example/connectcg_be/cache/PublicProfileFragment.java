package org.example.connectcg_be.cache;

public record PublicProfileFragment(
        Integer userId,
        String fullName,
        String avatarUrl,
        String coverUrl) {
}
