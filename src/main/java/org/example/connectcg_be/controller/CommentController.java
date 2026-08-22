package org.example.connectcg_be.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.connectcg_be.dto.CommentDTO;
import org.example.connectcg_be.dto.CreateCommentRequest;
import org.example.connectcg_be.security.UserPrincipal;
import org.example.connectcg_be.service.CommentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/posts/{postId}/comments")
@RequiredArgsConstructor
public class CommentController {
    @Autowired
    private CommentService commentService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<CommentDTO>> getComments(
            @PathVariable("postId") Integer postId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(commentService.getCommentsByPostId(postId, currentUser.getId()));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CommentDTO> createComment(
            @PathVariable Integer postId,
            @Valid @RequestBody CreateCommentRequest request,
            Authentication authentication) {

        UserPrincipal user = (UserPrincipal) authentication.getPrincipal();
        CommentDTO created = commentService.createComment(postId, user.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // DELETE /api/posts/{postId}/comments/{commentId} - Xóa comment
    @DeleteMapping("/{commentId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteComment(
            @PathVariable("postId") Integer postId,
            @PathVariable("commentId") Integer commentId,
            Authentication authentication) {

        UserPrincipal user = (UserPrincipal) authentication.getPrincipal();
        commentService.deleteComment(postId, commentId, user.getId());
        return ResponseEntity.ok().build();
    }

}
