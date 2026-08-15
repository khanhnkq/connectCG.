package org.example.connectcg_be.controller;

import org.example.connectcg_be.dto.MediaUploadResponse;
import org.example.connectcg_be.security.UserPrincipal;
import org.example.connectcg_be.service.MediaUploadService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MediaUploadControllerTest {

    @Test
    void returnsCreatedUploadContractForAuthenticatedPrincipal() {
        MediaUploadService service = mock(MediaUploadService.class);
        MediaUploadController controller = new MediaUploadController(service);
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", new byte[] {1});
        UserPrincipal principal = new UserPrincipal(
                42,
                "tester",
                "tester@example.com",
                "password",
                true,
                false,
                false,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        MediaUploadResponse expected = new MediaUploadResponse(
                7, "avatar/2026/08/id.png", "http://localhost:9000/connect-media/avatar/2026/08/id.png", "image/png", 9);
        when(service.upload(file, "avatar", 42)).thenReturn(expected);

        ResponseEntity<MediaUploadResponse> response = controller.upload(file, "avatar", principal);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(expected, response.getBody());
        verify(service).upload(file, "avatar", 42);
    }
}
