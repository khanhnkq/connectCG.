package org.example.connectcg_be.controller;

import org.example.connectcg_be.dto.MediaUploadResponse;
import org.example.connectcg_be.ratelimit.RateLimitPolicy;
import org.example.connectcg_be.ratelimit.RateLimitService;
import org.example.connectcg_be.security.UserPrincipal;
import org.example.connectcg_be.service.MediaUploadService;
import org.example.connectcg_be.service.ObjectStorageService;
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
        RateLimitService rateLimitService = mock(RateLimitService.class);
        ObjectStorageService objectStorageService = mock(ObjectStorageService.class);
        MediaUploadController controller = new MediaUploadController(service, rateLimitService, objectStorageService);
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
        verify(rateLimitService).check(RateLimitPolicy.MEDIA_UPLOAD, "42");
        verify(service).upload(file, "avatar", 42);
    }

    @Test
    void viewMedia_PublicCategory_AllowsUnauthenticatedAccessWithPublicCache() {
        MediaUploadService service = mock(MediaUploadService.class);
        RateLimitService rateLimitService = mock(RateLimitService.class);
        ObjectStorageService objectStorageService = mock(ObjectStorageService.class);
        MediaUploadController controller = new MediaUploadController(service, rateLimitService, objectStorageService);

        org.springframework.mock.web.MockHttpServletRequest request = new org.springframework.mock.web.MockHttpServletRequest();
        request.setAttribute(org.springframework.web.servlet.HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE,
                "/api/v1/media/view/avatar/2026-10/test.png");
        request.setAttribute(org.springframework.web.servlet.HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE,
                "/api/v1/media/view/**");

        when(objectStorageService.load("avatar/2026-10/test.png"))
                .thenReturn(new java.io.ByteArrayInputStream(new byte[]{1, 2, 3}));

        ResponseEntity<org.springframework.core.io.Resource> response = controller.viewMedia(request, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("max-age=31536000, public, immutable", response.getHeaders().getCacheControl());
    }

    @Test
    void viewMedia_PrivateCategory_UnauthenticatedReturnsUnauthorized() {
        MediaUploadService service = mock(MediaUploadService.class);
        RateLimitService rateLimitService = mock(RateLimitService.class);
        ObjectStorageService objectStorageService = mock(ObjectStorageService.class);
        MediaUploadController controller = new MediaUploadController(service, rateLimitService, objectStorageService);

        org.springframework.mock.web.MockHttpServletRequest request = new org.springframework.mock.web.MockHttpServletRequest();
        request.setAttribute(org.springframework.web.servlet.HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE,
                "/api/v1/media/view/chat/2026-10/private.png");
        request.setAttribute(org.springframework.web.servlet.HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE,
                "/api/v1/media/view/**");

        ResponseEntity<org.springframework.core.io.Resource> response = controller.viewMedia(request, null);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void viewMedia_PrivateCategory_AuthenticatedPrincipalReturnsOkWithPrivateCache() {
        MediaUploadService service = mock(MediaUploadService.class);
        RateLimitService rateLimitService = mock(RateLimitService.class);
        ObjectStorageService objectStorageService = mock(ObjectStorageService.class);
        MediaUploadController controller = new MediaUploadController(service, rateLimitService, objectStorageService);

        org.springframework.mock.web.MockHttpServletRequest request = new org.springframework.mock.web.MockHttpServletRequest();
        request.setAttribute(org.springframework.web.servlet.HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE,
                "/api/v1/media/view/chat/2026-10/private.png");
        request.setAttribute(org.springframework.web.servlet.HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE,
                "/api/v1/media/view/**");

        UserPrincipal principal = new UserPrincipal(
                42, "tester", "tester@example.com", "password", true, false, false,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));

        when(objectStorageService.load("chat/2026-10/private.png"))
                .thenReturn(new java.io.ByteArrayInputStream(new byte[]{1, 2, 3}));

        ResponseEntity<org.springframework.core.io.Resource> response = controller.viewMedia(request, principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("no-cache, private", response.getHeaders().getCacheControl());
    }
}
