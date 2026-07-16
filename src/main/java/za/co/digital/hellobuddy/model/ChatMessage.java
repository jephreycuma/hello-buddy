package za.co.digital.hellobuddy.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "chat_messages")
public class ChatMessage {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String threadId; // Unique session token identifying the customer thread

	@Column(nullable = false)
	private String sender; // "user" or "agent"

	private String senderName;

	@Column(columnDefinition = "TEXT", nullable = false)
	private String message;

	private long timestamp;
	private Long parentId;
	private String parentMessage;
	private String clientRefId;

	// Constructors
	public ChatMessage() {
	}

	// Getters and Setters
	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getThreadId() {
		return threadId;
	}

	public void setThreadId(String threadId) {
		this.threadId = threadId;
	}

	public String getSender() {
		return sender;
	}

	public void setSender(String sender) {
		this.sender = sender;
	}

	public String getSenderName() {
		return senderName;
	}

	public void setSenderName(String senderName) {
		this.senderName = senderName;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public long getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(long timestamp) {
		this.timestamp = timestamp;
	}

	public Long getParentId() {
		return parentId;
	}

	public void setParentId(Long parentId) {
		this.parentId = parentId;
	}

	public String getParentMessage() {
		return parentMessage;
	}

	public void setParentMessage(String parentMessage) {
		this.parentMessage = parentMessage;
	}

	public String getClientRefId() {
		return clientRefId;
	}

	public void setClientRefId(String clientRefId) {
		this.clientRefId = clientRefId;
	}
}
