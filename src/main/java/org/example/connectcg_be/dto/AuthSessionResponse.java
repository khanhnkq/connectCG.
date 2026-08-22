package org.example.connectcg_be.dto;

public record AuthSessionResponse(
        Integer id,
        String username,
        String role,
        boolean hasProfile,
        String fullName) {
}
