package org.example.connectcg_be.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.connectcg_be.dto.CreatePostRequest;
import org.example.connectcg_be.dto.GroupPostDTO;
import org.example.connectcg_be.dto.ReportAdminUpdateRequest;
import org.example.connectcg_be.dto.ReportResponse;
import org.example.connectcg_be.entity.User;
import org.example.connectcg_be.security.UserPrincipal;
import org.example.connectcg_be.service.PostService;
import org.example.connectcg_be.service.ReportService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReportAndPostSecurityExposureTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void userPasswordHashIsNotExposedDuringSerialization() throws Exception {
        User user = new User();
        user.setId(1);
        user.setUsername("victim");
        user.setEmail("victim@test.com");
        user.setPasswordHash("$2a$12$e8oY2F1f3NqD4xKkK99hce...");
        user.setRole("USER");

        String json = objectMapper.writeValueAsString(user);

        assertFalse(json.contains("passwordHash"), "passwordHash must not appear in JSON");
        assertFalse(json.contains("password_hash"), "password_hash must not appear in JSON");
        assertFalse(json.contains("$2a$12$"), "password hash content must not be exposed");
        assertTrue(json.contains("victim"), "Username should still be serialized");
    }

    @Test
    void postControllerUpdatePostReturnsGroupPostDTO() {
        PostService postService = mock(PostService.class);
        PostController postController = new PostController();

        // Inject mocks via reflection or fields
        org.springframework.test.util.ReflectionTestUtils.setField(postController, "postService", postService);

        CreatePostRequest request = new CreatePostRequest();
        request.setContent("Updated content");
        request.setVisibility("PUBLIC");

        UserPrincipal principal = new UserPrincipal(99, "author", "author@test.com", "pass", true, false, false, List.of(), 1);
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());

        GroupPostDTO expectedDto = new GroupPostDTO();
        expectedDto.setId(10);
        expectedDto.setContent("Updated content");
        expectedDto.setAuthorId(99);

        when(postService.updatePostAndReturnDTO(10, request, 99)).thenReturn(expectedDto);

        ResponseEntity<GroupPostDTO> response = postController.updatePost(10, request, auth);

        assertNotNull(response.getBody());
        assertEquals(10, response.getBody().getId());
        assertEquals("Updated content", response.getBody().getContent());
        verify(postService).updatePostAndReturnDTO(10, request, 99);
    }

    @Test
    void reportControllerGetDetailReturnsReportResponseDTO() {
        ReportService reportService = mock(ReportService.class);
        ReportController reportController = new ReportController(reportService);

        ReportResponse dto = new ReportResponse();
        dto.setId(5);
        dto.setTargetType("POST");
        dto.setTargetId(10);
        dto.setReason("Spam content");
        dto.setReporterUsername("reporterUser");
        dto.setAdminNote("Reviewed");

        when(reportService.getReportResponseById(5)).thenReturn(dto);

        ResponseEntity<ReportResponse> response = reportController.getReportDetail(5);

        assertNotNull(response.getBody());
        assertEquals(5, response.getBody().getId());
        assertEquals("reporterUser", response.getBody().getReporterUsername());
        assertEquals("Reviewed", response.getBody().getAdminNote());
        verify(reportService).getReportResponseById(5);
    }

    @Test
    void reportControllerUpdateUsesAuthenticatedAdminUsername() {
        ReportService reportService = mock(ReportService.class);
        ReportController reportController = new ReportController(reportService);

        ReportAdminUpdateRequest request = new ReportAdminUpdateRequest();
        request.setStatus("RESOLVED");
        request.setAdminNote("Content deleted");

        Principal principal = () -> "super_admin_alice";

        ResponseEntity<?> response = reportController.updateReportStatus(5, request, principal);

        assertEquals(200, response.getStatusCode().value());
        verify(reportService).updateReport(5, request, "super_admin_alice");
    }
}
