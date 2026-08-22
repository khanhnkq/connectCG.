package org.example.connectcg_be.repository;

import org.example.connectcg_be.entity.PostMedia;
import org.example.connectcg_be.entity.PostMediaId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface PostMediaRepository extends JpaRepository<PostMedia, PostMediaId> {
    List<PostMedia> findAllByPostId(Integer postId);

    @Query("SELECT pm FROM PostMedia pm JOIN FETCH pm.media WHERE pm.post.id IN :postIds")
    List<PostMedia> findAllByPostIdIn(@Param("postIds") Collection<Integer> postIds);
}
