package za.co.digital.hellobuddy.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import za.co.digital.hellobuddy.model.ChatMessage;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    // Fetch conversation history in chronological order
    List<ChatMessage> findByThreadIdOrderByTimestampAsc(String threadId);

    List<ChatMessage> findBySenderOrderByTimestampDesc(String sender);
    
 // Grouping trick: Get the latest message for every distinct thread ID in the database
    @Query("SELECT m FROM ChatMessage m WHERE m.id IN (SELECT MAX(c.id) FROM ChatMessage c GROUP BY c.threadId) ORDER BY m.timestamp DESC")
    List<ChatMessage> findLatestMessagesPerThread();
}