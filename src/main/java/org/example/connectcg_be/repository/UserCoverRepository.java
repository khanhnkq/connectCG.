package org.example.connectcg_be.repository;

import org.example.connectcg_be.entity.UserCover;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserCoverRepository extends JpaRepository<UserCover, Integer> {
    @EntityGraph(attributePaths = "media")
    Optional<UserCover> findByUserIdAndIsCurrentTrue(Integer userId);

    @Query("SELECT uc FROM UserCover uc JOIN FETCH uc.media WHERE uc.user.id IN :userIds AND uc.isCurrent = true")
    List<UserCover> findCurrentByUserIds(@Param("userIds") Collection<Integer> userIds);
}
