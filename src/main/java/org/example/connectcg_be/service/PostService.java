package org.example.connectcg_be.service;

import org.example.connectcg_be.dto.GroupPostDTO;
import java.util.List;

public interface PostService {
    List<GroupPostDTO> getPendingPosts(Integer groupId);

    List<GroupPostDTO> getApprovedPosts(Integer groupId);

    void approvePost(Integer postId, Integer adminId);

    void rejectPost(Integer postId, Integer adminId);
}
