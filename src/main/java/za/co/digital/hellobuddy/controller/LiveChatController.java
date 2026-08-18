package za.co.digital.hellobuddy.controller;

import jakarta.servlet.http.HttpSession;
import za.co.digital.hellobuddy.model.Agent;
import za.co.digital.hellobuddy.model.ChatMessage;
import za.co.digital.hellobuddy.repository.AgentRepository;
import za.co.digital.hellobuddy.repository.ChatMessageRepository;
import za.co.digital.hellobuddy.stripe.AiPrompt;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Controller
public class LiveChatController {

    private final SimpMessagingTemplate messagingTemplate;
    private final ChatMessageRepository messageRepository;
    private final AgentRepository agentRepository;
    private final PasswordEncoder passwordEncoder;
    private final ChatClient chatClient;

    // Persona pool for random assignment
    private static final List<String> AI_PERSONAS = List.of(
        "Kwetsima Cuma",
        "Nhlamulo Cuma",
        "Lesedi Mokoena",
        "Buhle Dlamini"
    );

    // Track assigned persona per threadId so the name stays consistent throughout a single conversation thread
    private final Map<String, String> threadPersonaMap = new ConcurrentHashMap<>();

    public LiveChatController(SimpMessagingTemplate messagingTemplate, 
                               ChatMessageRepository messageRepository, 
                               AgentRepository agentRepository,
                               PasswordEncoder passwordEncoder,
                               ChatClient.Builder chatClientBuilder) {
        this.messagingTemplate = messagingTemplate;
        this.messageRepository = messageRepository;
        this.agentRepository = agentRepository;
        this.passwordEncoder = passwordEncoder;
        this.chatClient = chatClientBuilder.build();
    }

    // ==========================================
    // 1. AGENT ROUTING & AUTHENTICATION
    // ==========================================

    @GetMapping("/agent/login")
    public String showLoginPage() {
        return "agent-login";
    }

    @PostMapping("/agent/login")
    public String processLogin(@RequestParam String username, 
                               @RequestParam String password, 
                               HttpSession session, 
                               Model model) {
        Optional<Agent> agentOpt = agentRepository.findByUsername(username);
        
        if (agentOpt.isPresent() && passwordEncoder.matches(password, agentOpt.get().getPassword())) {
            session.setAttribute("loggedInAgent", agentOpt.get());
            return "redirect:/agent/workspace";
        }
        
        model.addAttribute("error", "Invalid username or password credentials.");
        return "agent-login";
    }

    @GetMapping("/agent/workspace")
    public String showWorkspace(HttpSession session, Model model) {
        Agent agent = (Agent) session.getAttribute("loggedInAgent");
        if (agent == null) {
            return "redirect:/agent/login";
        }
        model.addAttribute("agent", agent);
        return "agent-workspace";
    }

    // ==========================================
    // 2. WEBSOCKET & CHAT PERSISTENCE + AI RESPONSE
    // ==========================================

    @MessageMapping("/chat.send")
    @Transactional
    public void processMessage(@Payload ChatMessage message) {
        System.out.println(">>> RECEIVED CHAT MESSAGE FROM CLIENT: " + message.getMessage());
        if (message.getTimestamp() == 0) {
            message.setTimestamp(System.currentTimeMillis());
        }

        // Save and broadcast incoming customer message
        ChatMessage savedCustomerMessage = messageRepository.saveAndFlush(message);
        messagingTemplate.convertAndSend("/topic/thread/" + message.getThreadId(), savedCustomerMessage);
        
        String assignedAgentId = "1"; 
        messagingTemplate.convertAndSend("/topic/agent-" + assignedAgentId, savedCustomerMessage);

        // If the sender is a customer, trigger AI response asynchronously
        if (!"AI_AGENT".equalsIgnoreCase(message.getSender()) && !"HUMAN_AGENT".equalsIgnoreCase(message.getSender())) {
            CompletableFuture.runAsync(() -> generateAndSendAiResponse(savedCustomerMessage));
        }
    }

    private void generateAndSendAiResponse(ChatMessage customerMessage) {
        try {
            String threadId = customerMessage.getThreadId();

            // Assign or retrieve a persistent persona name for this conversation thread
            String assignedPersona = threadPersonaMap.computeIfAbsent(threadId, id -> 
                AI_PERSONAS.get(ThreadLocalRandom.current().nextInt(AI_PERSONAS.size()))
            );

            // Fetch recent thread history to provide conversational context to Spring AI
            List<ChatMessage> history = messageRepository.findByThreadIdOrderByTimestampAsc(threadId);
            String conversationContext = history.stream()
                .map(msg -> msg.getSenderName() + ": " + msg.getMessage())
                .collect(Collectors.joining("\n"));

            boolean isFirstMessage = history.stream().noneMatch(m -> "AI_AGENT".equalsIgnoreCase(m.getSender()));

            // Build dynamic System Prompt instructing the LLM about identity and greeting rules
            String systemPrompt = String.format(AiPrompt.PROMPT,
                assignedPersona,
                isFirstMessage ? "This is the start of the chat, so greet the customer introduced as: 'Hello, you are talking to " + assignedPersona + ". How can I help you today?'" 
                               : "Do not repeat full formal greetings if you have already greeted the customer in this chat thread."
            );

            // Invoke Spring AI ChatClient
            String aiResponseText = chatClient.prompt()
                .system(systemPrompt)
                .user("Conversation History:\n" + conversationContext + "\n\nRespond to the latest customer message.")
                .call()
                .content();

            // Construct and persist AI ChatMessage entity
            ChatMessage aiMessage = new ChatMessage();
            aiMessage.setThreadId(threadId);
            aiMessage.setSender("AI_AGENT");
            aiMessage.setSenderName(assignedPersona);
            aiMessage.setMessage(aiResponseText);
            aiMessage.setTimestamp(System.currentTimeMillis());
            aiMessage.setParentId(customerMessage.getId());
            aiMessage.setParentMessage(customerMessage.getMessage());

            ChatMessage savedAiMessage = messageRepository.saveAndFlush(aiMessage);

            // Broadcast AI response via WebSockets to customer thread and agent dashboard
            messagingTemplate.convertAndSend("/topic/thread/" + threadId, savedAiMessage);
            messagingTemplate.convertAndSend("/topic/agent-1", savedAiMessage);

        } catch (Exception e) {
        	//("AI Generation Exception Root Cause: ", e); // Log full stack trace
            System.err.println("Detailed Exception Message: " + e.getMessage());
            if (e.getCause() != null) {
                System.err.println("Root Cause: " + e.getCause().getMessage());
            }
        
            System.err.println("Error generating AI chat response: " + e.getMessage());
        }
    }

    // ==========================================
    // 3. HISTORICAL CHAT REST ENDPOINT
    // ==========================================

    @GetMapping("/api/chat/history/{threadId}")
    @ResponseBody
    public List<ChatMessage> getChatHistory(@PathVariable String threadId) {
        return messageRepository.findByThreadIdOrderByTimestampAsc(threadId);
    }
    
    @GetMapping("/api/chat/active-threads")
    @ResponseBody
    public List<ChatMessage> getActiveThreads() {
        return messageRepository.findLatestMessagesPerThread();
    }
    
    @MessageMapping("/chat.edit")
    public void processMessageEdit(@Payload ChatMessage message) {
        Optional<ChatMessage> existingOpt = messageRepository.findById(message.getId());
        if (existingOpt.isPresent()) {
            ChatMessage existing = existingOpt.get();
            existing.setMessage(message.getMessage());
            messageRepository.save(existing);

            messagingTemplate.convertAndSend("/topic/thread/" + message.getThreadId(), message);

            String assignedAgentId = "1"; 
            messagingTemplate.convertAndSend("/topic/agent-" + assignedAgentId, message);
        }
    }
}