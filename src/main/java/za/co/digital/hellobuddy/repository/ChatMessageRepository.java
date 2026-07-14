package za.co.digital.hellobuddy.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import za.co.digital.hellobuddy.model.ChatMessage;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    // Fetch conversation history in chronological order
    List<ChatMessage> findByThreadIdOrderByTimestampAsc(String threadId);

    List<ChatMessage> findBySenderOrderByTimestampDesc(String sender);
}