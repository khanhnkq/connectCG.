package org.example.connectcg_be.repository;

import org.example.connectcg_be.entity.UserAvatar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface UserAvatarRepository extends JpaRepository<UserAvatar, Integer> {
    @EntityGraph(attributePaths = "media")
    UserAvatar findByUserIdAndIsCurrentTrue(Integer userId);

    @Query("SELECT ua FROM UserAvatar ua JOIN FETCH ua.media WHERE ua.user.id IN :userIds AND ua.isCurrent = true")
    List<UserAvatar> findCurrentByUserIds(@Param("userIds") Collection<Integer> userIds);
}
