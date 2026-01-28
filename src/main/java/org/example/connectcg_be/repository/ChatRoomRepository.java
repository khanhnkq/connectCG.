package org.example.connectcg_be.repository;

import org.example.connectcg_be.entity.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {
    Optional<ChatRoom> findByFirebaseRoomKey(String firebaseRoomKey);
}
