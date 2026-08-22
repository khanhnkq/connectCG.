package org.example.connectcg_be.repository;

import org.example.connectcg_be.entity.Reaction;
import org.example.connectcg_be.entity.ReactionId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ReactionRepository extends JpaRepository<Reaction, ReactionId> {
    long countByPostId(Integer postId);

    @Query("SELECT r FROM Reaction r WHERE r.user.id = :userId AND r.post.id IN :postIds")
    List<Reaction> findAllByUserIdAndPostIdIn(
            @Param("userId") Integer userId,
            @Param("postIds") Collection<Integer> postIds);
}
